package ru.nyasha;

import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import java.util.*;

/** Меню действий в чате (вместо форм Script API), подарки, призыв, создание спутницы. */
public final class Menu {
    private Menu() {}
    private static final String[][] ACTIONS = {
        {"talk", "Поговорить"}, {"mood", "Как настроение?"}, {"compliment", "Комплимент"}, {"hug", "Обнять"}, {"pet", "Погладить"},
        {"walk", "Пойдём гулять"}, {"rest", "Отдохнём"}, {"holdhand", "Взять за руку"}, {"kiss", "Поцеловать в щёчку"}, {"romantic_compliment", "Романтический комплимент"}};

    private static MutableText button(String label, String cmd, String color) {
        return Text.literal("[" + label + "] ").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)).withColor(net.minecraft.util.Formatting.byName(color)));
    }
    public static void sendLine(ServerPlayerEntity pl, CompanionEntity e, Profile p, String text) {
        pl.sendMessage(Text.literal(Config.COMPANIONS.get(e.kind()).color() + "[" + p.name + "] §r" + text));
    }
    public static void sendChoice(ServerPlayerEntity pl, Dialogue d, Dialogue.Resp r, Profile p, String player) {
        pl.sendMessage(Text.literal("  ").append(button(DialogueManager.fill(r.text, p, player), "/nyasha reply " + d.id + " " + r.id, "gray")));
    }

    public static void open(ServerPlayerEntity pl, CompanionEntity e) {
        Profile p = e.profile();
        if (p == null) return;
        MutableText t = Text.literal(Config.COMPANIONS.get(e.kind()).color() + p.name + " §7— " + Relationship.moodLabel(p) + "\n");
        for (String[] a : ACTIONS) t.append(button(a[1], "/nyasha act " + a[0] + " " + e.kind(), "aqua"));
        pl.sendMessage(t);
    }

    public static CompanionEntity find(MinecraftServer s, UUID owner, String kind) {
        for (ServerWorld w : s.getWorlds()) {
            List<? extends CompanionEntity> l = w.getEntitiesByType(TypeFilter.instanceOf(CompanionEntity.class), c -> c.kind().equals(kind) && owner.equals(c.getOwnerId()));
            if (!l.isEmpty()) return l.get(0);
        }
        return null;
    }
    public static CompanionEntity nearest(ServerPlayerEntity pl, String kind) {
        List<CompanionEntity> l = pl.getServerWorld().getEntitiesByClass(CompanionEntity.class, pl.getBoundingBox().expand(Config.FAR * 2),
            c -> pl.getUuid().equals(c.getOwnerId()) && (kind == null || c.kind().equals(kind)));
        l.sort(Comparator.comparingDouble(c -> c.squaredDistanceTo(pl)));
        return l.isEmpty() ? null : l.get(0);
    }

    // ---------- Сердце ----------
    public static void heart(ServerPlayerEntity pl, String kind) {
        MinecraftServer s = pl.getServer();
        Profiles ps = Profiles.get(s);
        Config.Companion comp = Config.COMPANIONS.get(kind);
        Profile p = ps.find(pl.getUuid(), kind);
        if (p == null) {
            MutableText t = Text.literal(comp.color() + "Выбери характер (" + comp.defaultName() + "):\n");
            for (String ch : comp.chars()) t.append(button(charLabel(ch), "/nyasha create " + kind + " " + ch, "yellow"));
            t.append(Text.literal("\n§7Своё имя: /nyasha create " + kind + " <характер> <имя>"));
            pl.sendMessage(t);
            return;
        }
        CompanionEntity e = find(s, pl.getUuid(), kind);
        ServerWorld w = pl.getServerWorld();
        if (e != null) {
            e.teleport(w, pl.getX(), pl.getY(), pl.getZ(), Set.of(), pl.getYaw(), 0f);
            pl.sendMessage(Text.literal(comp.color() + "[" + p.name + "] §7Я уже здесь! Нажми на меня, чтобы поговорить."));
        } else {
            e = spawn(pl, p);
            pl.sendMessage(Text.literal(comp.color() + "[" + p.name + "] §aЯ вернулась!"));
            e.fire(pl, List.of("player_returned"), false, true, null);
        }
    }
    public static String charLabel(String ch) {
        return switch (ch) { case "cute" -> "Милая"; case "tsundere" -> "Стерва"; case "meek" -> "Милая тихоня"; case "sly" -> "Хитрая"; default -> ch; };
    }
    private static CompanionEntity spawn(ServerPlayerEntity pl, Profile p) {
        EntityType<? extends CompanionEntity> t = p.kind.equals("lissi") ? ModRegistry.LISSI : ModRegistry.NYASHA;
        CompanionEntity e = t.create(pl.getServerWorld());
        e.refreshPositionAndAngles(pl.getX() + 1, pl.getY(), pl.getZ(), pl.getYaw(), 0);
        e.setOwner(pl.getUuid()); e.setCharacter(p.character);
        e.setCustomName(Text.literal(p.name)); e.setCustomNameVisible(true);
        pl.getServerWorld().spawnEntity(e);
        return e;
    }
    public static void create(ServerPlayerEntity pl, String kind, String character, String name) {
        Config.Companion comp = Config.COMPANIONS.get(kind);
        if (comp == null || !Arrays.asList(comp.chars()).contains(character)) { pl.sendMessage(Text.literal("§cНеизвестный характер.")); return; }
        Profiles ps = Profiles.get(pl.getServer());
        if (ps.find(pl.getUuid(), kind) != null) { pl.sendMessage(Text.literal("§7Она уже с тобой. Используй Сердце, чтобы позвать.")); return; }
        Profile p = ps.of(pl.getUuid(), kind, character);
        if (name != null && !name.isBlank()) p.name = name.trim().substring(0, Math.min(name.trim().length(), 24));
        p.mood = 55; p.lastSeen = pl.getWorld().getTime(); p.lastDayTick = p.lastSeen; p.remember("first_meet");
        CompanionEntity e = spawn(pl, p);
        ps.markDirty();
        e.fire(pl, List.of("first_meet"), false, true, null);
    }

    // ---------- Действия ----------
    public static void act(ServerPlayerEntity pl, String action, String kind) {
        CompanionEntity e = nearest(pl, kind);
        if (e == null) { pl.sendMessage(Text.literal("§7Рядом никого нет.")); return; }
        Profile p = e.profile();
        long now = pl.getWorld().getTime();
        if (e.distanceTo(pl) > Config.TALK_RANGE) { pl.sendMessage(Text.literal("§7Подойди ближе.")); return; }
        boolean soft = action.equals("talk") || action.equals("mood");
        if (p.conflict.active && !soft) { sendLine(pl, e, p, DialogueManager.I.refusal(p)); return; }
        String why = Relationship.actionBlocked(p, action, now);
        if (why != null) { pl.sendMessage(Text.literal("§7" + why)); return; }
        p.cooldowns.put(action, now);
        Dialogue.Effects eff = Config.actionEffects(action);
        switch (action) {
            case "mood" -> pl.sendMessage(Text.literal(String.format("§7%s: %s (симпатия %d, доверие %d, романтика %d, раздражение %d)", p.name, Relationship.moodLabel(p),
                Math.round(p.stat("affection")), Math.round(p.stat("trust")), Math.round(p.stat("romance")), Math.round(p.stat("irritation")))));
            case "kiss" -> {
                if (p.character.equals("tsundere") && p.stat("trust") < Config.SLAP_TRUST) {
                    e.setAction("slap", 0); e.setEmotion("angry", 160);
                    pl.damage(e.getWorld().getDamageSources().mobAttack(e), (float) Config.SLAP_DAMAGE);
                    pl.takeKnockback(Config.SLAP_KNOCKBACK, e.getX() - pl.getX(), e.getZ() - pl.getZ());
                    eff = new Dialogue.Effects(); eff.irritation = 6.0; eff.affection = -2.0;
                } else { e.setAction(p.character.equals("tsundere") ? "kiss_tsun" : "kiss", 0); e.particles(ParticleTypes.HEART, 10); p.remember("kiss"); }
            }
            case "hug", "pet", "holdhand" -> { e.setAction(action, 0); e.particles(ParticleTypes.HEART, 6); }
            case "walk" -> { e.setFollowing(true); e.setAction("cheer", 0); }
            case "rest" -> { e.setFollowing(false); e.setAction("sleep", 0); }
            case "compliment", "romantic_compliment" -> e.setEmotion("shy", 160);
            default -> {}
        }
        if (!action.equals("mood")) e.change(pl, p, eff, "act:" + action);
        e.fire(pl, Config.ACTION_EVENT.getOrDefault(action, List.of("talk")), false, true, null);
    }

    public static void gift(ServerPlayerEntity pl, CompanionEntity e, ItemStack st) {
        Profile p = e.profile();
        if (p == null) return;
        String id = Registries.ITEM.getId(st.getItem()).toString();
        Config.Gift g = (e.kind().equals("lissi") ? Config.GIFTS_LISSI : Config.GIFTS).getOrDefault(id, Config.GIFT_DEFAULT);
        if (!pl.isCreative()) st.decrement(1);
        Dialogue.Effects eff = new Dialogue.Effects();
        eff.affection = g.a(); eff.trust = g.t(); eff.flags_set = List.of("first_gift");
        boolean good = g.pref().equals("love") || g.pref().equals("like");
        if (g.pref().equals("love")) p.remember("gift_love");
        e.setAction(good ? "joy" : "pout", 0); e.setEmotion(good ? "happy" : "sad", 160);
        if (good) e.particles(ParticleTypes.HEART, 6);
        e.change(pl, p, eff, "gift");
        if (!e.fire(pl, List.of("gift"), false, true, null))
            sendLine(pl, e, p, good ? "Ой, мне? Спасибо!" : "Э-э… спасибо, наверное.");
    }

    public static void reply(ServerPlayerEntity pl, String did, String rid) {
        Dialogue d = DialogueManager.I.getById(did);
        if (d == null) return;
        Dialogue.Resp r = d.responses.stream().filter(x -> x.id.equals(rid)).findFirst().orElse(null);
        CompanionEntity e = nearest(pl, null);
        if (r == null || e == null) return;
        Profile p = e.profile();
        if (p == null || !p.character.equals(d.character) || !p.history.recent.contains(did)) return;   // ответ только на недавнюю реплику своей спутницы
        pl.sendMessage(Text.literal("§7» " + DialogueManager.fill(r.text, p, pl.getName().getString())));
        Map<String, Double> ap = e.change(pl, p, r.effects, "dlg:" + did);
        Dialogue.Reaction re = DialogueManager.I.reactionFor(p, ap);
        if (re != null) {
            sendLine(pl, e, p, DialogueManager.fill(re.text, p, pl.getName().getString()));
            if (re.emotion != null) e.setEmotion(re.emotion, 160);
        }
        p.history.recent.remove(did);   // один ответ на реплику
    }
}
