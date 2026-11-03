package ru.nyasha;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;

/** Общая база Мелони и Лисси: ИИ-состояния (follow/defend/flee/idle), эмоции/действия для анимаций, события диалогов. */
public abstract class CompanionEntity extends PathAwareEntity implements GeoEntity {
    static final TrackedData<String> EMOTION = net.minecraft.entity.data.DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.STRING);
    static final TrackedData<String> ACTION = net.minecraft.entity.data.DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.STRING);
    static final TrackedData<String> CHARACTER = net.minecraft.entity.data.DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.STRING);

    protected UUID ownerId;
    protected boolean following = true;
    protected int actionTicks, emotionTicks;
    private long lastSupport = -99999, lastLowHp = -99999, lastNight = -99999;
    private boolean wasNight;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected CompanionEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }
    public abstract String kind();
    public UUID getOwnerId() { return ownerId; }
    public void setOwner(UUID id) { this.ownerId = id; }
    public boolean isFollowing() { return following; }
    public void setFollowing(boolean f) { following = f; }
    public String getEmotion() { return dataTracker.get(EMOTION); }
    public String getAction() { return dataTracker.get(ACTION); }
    public String getCharacter() { return dataTracker.get(CHARACTER); }
    public void setCharacter(String c) { dataTracker.set(CHARACTER, c); }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 30)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    @Override protected void initDataTracker(DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(EMOTION, "neutral"); b.add(ACTION, "none"); b.add(CHARACTER, "");
    }

    // ---------- ИИ ----------
    @Override protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new FleeGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.add(3, new FollowOwnerGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(7, new LookAroundGoal(this));
        targetSelector.add(1, new DefendOwnerGoal(this));
    }

    /** Убегает при здоровье < 30%. */
    static class FleeGoal extends EscapeDangerGoal {
        private final CompanionEntity c;
        FleeGoal(CompanionEntity c) { super(c, 1.5); this.c = c; }
        @Override public boolean canStart() { return c.getHealth() < c.getMaxHealth() * 0.3f && super.canStart(); }
    }

    static class FollowOwnerGoal extends Goal {
        private final CompanionEntity c; private PlayerEntity owner; private int cd;
        FollowOwnerGoal(CompanionEntity c) { this.c = c; setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart() {
            if (c.ownerId == null || !c.following) return false;
            owner = c.getWorld().getPlayerByUuid(c.ownerId);
            return owner != null && c.squaredDistanceTo(owner) > 36;
        }
        @Override public boolean shouldContinue() { return owner != null && owner.isAlive() && c.following && c.squaredDistanceTo(owner) > 9 && c.getTarget() == null; }
        @Override public void start() { cd = 0; }
        @Override public void stop() { c.getNavigation().stop(); }
        @Override public void tick() {
            c.getLookControl().lookAt(owner, 10f, c.getMaxLookPitchChange());
            if (--cd > 0) return;
            cd = 8;
            double d2 = c.squaredDistanceTo(owner);
            if (d2 > Config.FAR * Config.FAR && c.getWorld() instanceof ServerWorld) {
                c.refreshPositionAndAngles(owner.getX() + (c.random.nextDouble() - 0.5) * 2, owner.getY(), owner.getZ() + (c.random.nextDouble() - 0.5) * 2, c.getYaw(), c.getPitch());
                c.getNavigation().stop();
            } else c.getNavigation().startMovingTo(owner, d2 > 144 ? 1.4 : 1.05);
        }
    }

    /** Защита хозяина: бьёт того, кто ударил хозяина / кого ударил хозяин / ближайшего монстра рядом с хозяином. */
    static class DefendOwnerGoal extends Goal {
        private final CompanionEntity c; private LivingEntity t;
        DefendOwnerGoal(CompanionEntity c) { this.c = c; setControls(EnumSet.of(Control.TARGET)); }
        @Override public boolean canStart() {
            if (c.ownerId == null || c.age % 5 != 0) return false;
            PlayerEntity o = c.getWorld().getPlayerByUuid(c.ownerId);
            if (o == null || c.squaredDistanceTo(o) > 24 * 24) return false;
            LivingEntity a = o.getAttacker();
            if (a == null) a = o.getAttacking();
            if (a != null && a.isAlive() && !(a instanceof CompanionEntity) && a != o) { t = a; return true; }
            List<HostileEntity> list = c.getWorld().getEntitiesByClass(HostileEntity.class, o.getBoundingBox().expand(8), e -> e.isAlive() && !(e instanceof CompanionEntity));
            if (list.isEmpty()) return false;
            list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(o)));
            t = list.get(0); return true;
        }
        @Override public boolean shouldContinue() { return t != null && t.isAlive() && c.squaredDistanceTo(t) < 30 * 30; }
        @Override public void start() { c.setTarget(t); }
        @Override public void stop() { c.setTarget(null); t = null; }
    }

    // ---------- Тик ----------
    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (getCharacter().isEmpty()) {
            String[] ch = Config.COMPANIONS.get(kind()).chars();
            setCharacter(ch[random.nextInt(ch.length)]);
        }
        if (actionTicks > 0 && --actionTicks == 0) dataTracker.set(ACTION, "none");
        if (emotionTicks > 0 && --emotionTicks == 0) dataTracker.set(EMOTION, "neutral");
        if (age % Config.TICK_FAST == 0) fastTick();
        if (age % Config.TICK_SLOW == 0) slowTick();
    }

    public void setAction(String a, int ticks) { dataTracker.set(ACTION, a); actionTicks = ticks > 0 ? ticks : Config.ACTION_LEN.getOrDefault(a, 50); }
    public void setEmotion(String e, int ticks) { dataTracker.set(EMOTION, e); emotionTicks = ticks; }
    public void particles(net.minecraft.particle.ParticleEffect p, int n) {
        if (getWorld() instanceof ServerWorld sw) sw.spawnParticles(p, getX(), getY() + 1.6, getZ(), n, 0.3, 0.3, 0.3, 0.02);
    }

    public Profile profile() {
        if (ownerId == null || !(getWorld() instanceof ServerWorld sw)) return null;
        return Profiles.get(sw.getServer()).find(ownerId, kind());
    }
    private ServerPlayerEntity ownerPlayer() { return ownerId == null ? null : (ServerPlayerEntity) getWorld().getPlayerByUuid(ownerId); }
    private void dirty() { if (getWorld() instanceof ServerWorld sw) Profiles.get(sw.getServer()).markDirty(); }

    public DialogueManager.Ctx buildCtx(ServerPlayerEntity pl, Profile p) {
        DialogueManager.Ctx c = new DialogueManager.Ctx();
        long tod = getWorld().getTimeOfDay() % 24000;
        c.timeOfDay = tod >= 12000 && tod < 13000 ? "dusk" : tod >= 13000 && tod < 23000 ? "night" : "day";
        if (c.timeOfDay.equals("night")) c.states.add("night");
        c.dimension = getWorld().getRegistryKey().getValue().getPath().replace("the_", "");
        if (getWorld().isThundering()) { c.weather = "thunder"; c.states.add("rain"); }
        else if (getWorld().isRaining()) { c.weather = "rain"; c.states.add("rain"); }
        c.distance = pl == null ? 9999 : distanceTo(pl);
        c.absence = pl == null ? 0 : Math.max(0, getWorld().getTime() - p.lastSeen);
        c.enemies = !getWorld().getEntitiesByClass(HostileEntity.class, getBoundingBox().expand(12), e -> e.isAlive()).isEmpty();
        net.minecraft.util.math.BlockPos bp = getBlockPos();
        for (net.minecraft.util.math.BlockPos q : net.minecraft.util.math.BlockPos.iterateOutwards(bp, 5, 2, 5)) {
            var st = getWorld().getBlockState(q);
            if (st.isOf(net.minecraft.block.Blocks.CAMPFIRE) || st.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE)) { c.near.add("campfire"); c.states.add("campfire"); }
            else if (st.isOf(net.minecraft.block.Blocks.WATER)) c.near.add("water");
            else if (st.getBlock() instanceof net.minecraft.block.BedBlock) c.near.add("bed");
        }
        return c;
    }

    /** Показывает диалог по событиям. force=true игнорирует глобальную паузу. Возвращает, найдена ли реплика. */
    public boolean fire(ServerPlayerEntity pl, List<String> events, boolean noResp, boolean force, String stageKey) {
        Profile p = profile();
        if (p == null || pl == null) return false;
        long now = getWorld().getTime();
        if (!force && now - p.lastSay < Config.GLOBAL_DIALOGUE_GAP) return false;
        DialogueManager.Ctx ctx = buildCtx(pl, p);
        ctx.stageKey = stageKey;
        Dialogue d = DialogueManager.I.select(p, ctx, events, noResp, now);
        if (d == null) return false;
        say(pl, p, d, events);
        return true;
    }

    public void say(ServerPlayerEntity pl, Profile p, Dialogue d, List<String> events) {
        Menu.sendLine(pl, this, p, DialogueManager.fill(d.text, p, pl.getName().getString()));
        for (Dialogue.Resp r : d.responses) Menu.sendChoice(pl, d, r, p, pl.getName().getString());
        DialogueManager.I.markShown(p, d, getWorld().getTime());
        for (String ev : events) {
            String[] re = Config.REACT.get(ev);
            if (re != null) { setEmotion(re[0], 160); if (!re[1].equals("none")) setAction(re[1], 0); break; }
        }
        dirty();
    }

    /** Применить эффекты, проверить этапы и ссору. Возвращает применённые изменения. */
    public Map<String, Double> change(ServerPlayerEntity pl, Profile p, Dialogue.Effects e, String source) {
        long now = getWorld().getTime();
        Map<String, Double> ap = Relationship.apply(p, e, source, false, now);
        String st = Relationship.evaluateStages(p);
        if (st != null) { fire(pl, List.of("stage_up"), false, true, st); p.stagePending = null; particles(ParticleTypes.HEART, 8); }
        if (!p.conflict.active && p.stat("irritation") >= Config.CONFLICT_START_IRR) {
            p.conflict.active = true; p.conflict.since = now;
            fire(pl, List.of("conflict_start"), false, true, null);
        }
        dirty();
        return ap;
    }

    private void fastTick() {
        ServerPlayerEntity pl = ownerPlayer();
        Profile p = profile();
        if (pl == null || p == null) return;
        long now = getWorld().getTime();
        boolean near = pl.getWorld() == getWorld() && distanceTo(pl) <= Config.NEAR;
        if (near) {
            if (now - p.lastSeen > Config.ABSENCE_RETURN) fire(pl, List.of("player_returned"), false, true, null);
            p.lastSeen = now;
            if (pl.getHealth() <= 6 && now - lastLowHp > 1200) { lastLowHp = now; fire(pl, List.of("player_low_health"), true, true, null); }
            if (pl.getHealth() <= 8 && p.stat("affection") >= 55 && p.stat("trust") >= 45 && now - lastSupport > 3600) {
                lastSupport = now; pl.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 0)); particles(ParticleTypes.HEART, 6);
            }
            long tod = getWorld().getTimeOfDay() % 24000;
            boolean night = tod >= 13000 && tod < 23000;
            if (night && !wasNight && now - lastNight > 6000) { lastNight = now; fire(pl, List.of("night"), true, false, null); }
            wasNight = night;
        }
    }

    private void slowTick() {
        ServerPlayerEntity pl = ownerPlayer();
        Profile p = profile();
        if (p == null) return;
        long now = getWorld().getTime();
        if (p.lastDayTick == 0) p.lastDayTick = now;
        if (now - p.lastDayTick >= 24000) { p.lastDayTick = now; p.daysTogether++; }
        if (p.stat("irritation") > 0) p.stats.put("irritation", Math.max(0, p.stat("irritation") - Config.IRR_DECAY_SLOW));
        if (p.conflict.active && p.stat("irritation") < Config.CONFLICT_HEAL_IRR && pl != null
            && now - p.conflict.since >= Config.CONFLICT_MIN_WAIT.get(p.character)) {
            p.conflict.active = false; p.remember("reconciled");
            fire(pl, List.of("reconcile"), false, true, null);
        }
        if (pl != null && pl.getWorld() == getWorld() && distanceTo(pl) <= Config.NEAR && getTarget() == null && now - p.lastSay > Config.AMBIENT_COOLDOWN)
            fire(pl, List.of("ambient"), false, false, null);
        dirty();
    }

    // ---------- Взаимодействие ----------
    @Override public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (hand != Hand.MAIN_HAND || ownerId == null || !player.getUuid().equals(ownerId) || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        ItemStack st = player.getStackInHand(hand);
        if (st.getItem() instanceof HeartItem) { sp.sendMessage(net.minecraft.text.Text.literal("§7Сердце дарить нельзя.")); return ActionResult.SUCCESS; }
        if (!st.isEmpty()) { Menu.gift(sp, this, st); return ActionResult.SUCCESS; }
        if (player.isSneaking()) {
            following = !following;
            sp.sendMessage(net.minecraft.text.Text.literal(following ? "§7Идёт за тобой." : "§7Остаётся на месте."), true);
            return ActionResult.SUCCESS;
        }
        Menu.open(sp, this);
        return ActionResult.SUCCESS;
    }

    @Override public void writeCustomDataToNbt(NbtCompound n) {
        super.writeCustomDataToNbt(n);
        if (ownerId != null) n.putUuid("Owner", ownerId);
        n.putBoolean("Following", following); n.putString("Character", getCharacter());
    }
    @Override public void readCustomDataFromNbt(NbtCompound n) {
        super.readCustomDataFromNbt(n);
        if (n.containsUuid("Owner")) ownerId = n.getUuid("Owner");
        following = !n.contains("Following") || n.getBoolean("Following");
        setCharacter(n.getString("Character"));
    }
    @Override public boolean canImmediatelyDespawn(double d) { return false; }

    // ---------- GeckoLib ----------
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    private static final Set<String> ACT_ANIMS = Set.of("hug", "kiss", "holdhand", "wave", "cheer", "joy", "gift", "pout", "scared", "romance", "sleep", "pet", "slap", "kiss_tsun");
    private static final Set<String> EMO_ANIMS = Set.of("happy", "sad", "angry", "shy", "surprised", "jealous", "tender", "sleepy");

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        final String k = kind();
        c.add(new AnimationController<>(this, "move", 4, s -> {
            CompanionEntity e = s.getAnimatable();
            double sp = e.getVelocity().horizontalLength();
            String a = !e.isOnGround() && !e.isTouchingWater() ? "jump" : sp > 0.2 ? "run" : sp > 0.02 ? "walk" : "idle";
            return s.setAndContinue(RawAnimation.begin().thenLoop("animation." + k + "." + a));
        }));
        c.add(new AnimationController<>(this, "look", 0, s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation." + k + ".look"))));
        c.add(new AnimationController<>(this, "pose", 6, s -> {
            CompanionEntity e = s.getAnimatable();
            String act = e.getAction(), emo = e.getEmotion();
            if (ACT_ANIMS.contains(act)) return s.setAndContinue(RawAnimation.begin().thenLoop("animation." + k + "." + act));
            if (EMO_ANIMS.contains(emo)) return s.setAndContinue(RawAnimation.begin().thenLoop("animation." + k + "." + emo));
            return PlayState.STOP;
        }));
    }
}
