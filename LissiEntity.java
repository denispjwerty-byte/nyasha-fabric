package ru.nyasha;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.core.molang.MolangParser;

/** Лисси (лисичка), характеры: meek / sly. id: nyasha:lissi. */
public class LissiEntity extends CompanionEntity {
    public LissiEntity(EntityType<? extends PathAwareEntity> t, World w) { super(t, w); }
    @Override public String kind() { return "lissi"; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        super.registerControllers(c);
        // Хвост/уши: анимация fox зависит от эмоции через Molang-переменные v.emotion / v.sleeping (в Bedrock — q.property).
        c.add(new AnimationController<>(this, "fox", 0, s -> {
            LissiEntity e = (LissiEntity) s.getAnimatable();
            int emo = java.util.Arrays.asList(Config.EMOTIONS).indexOf(e.getEmotion());
            double sleeping = e.getAction().equals("sleep") ? 1 : 0;
            MolangParser.INSTANCE.setValue("variable.emotion", () -> Math.max(0, emo));
            MolangParser.INSTANCE.setValue("variable.sleeping", () -> sleeping);
            return s.setAndContinue(RawAnimation.begin().thenLoop("animation.lissi.fox"));
        }));
    }
}
