package ru.nyasha;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

/** Мелони (крипер), характеры: cute / tsundere. id: nyasha:creeper. */
public class NyashaEntity extends CompanionEntity {
    public NyashaEntity(EntityType<? extends PathAwareEntity> t, World w) { super(t, w); }
    @Override public String kind() { return "nyasha"; }
}
