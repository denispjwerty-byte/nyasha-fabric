package ru.nyasha;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModRegistry {
    private ModRegistry() {}
    public static final EntityType<NyashaEntity> NYASHA = EntityType.Builder.create(NyashaEntity::new, SpawnGroup.CREATURE).dimensions(0.6f, 1.9f).maxTrackingRange(10).build("creeper");
    public static final EntityType<LissiEntity> LISSI = EntityType.Builder.create(LissiEntity::new, SpawnGroup.CREATURE).dimensions(0.6f, 1.9f).maxTrackingRange(10).build("lissi");
    public static final HeartItem HEART = new HeartItem("nyasha");
    public static final HeartItem LISSI_HEART = new HeartItem("lissi");

    public static void init() {
        Registry.register(Registries.ENTITY_TYPE, Identifier.of(Config.MOD_ID, "creeper"), NYASHA);
        Registry.register(Registries.ENTITY_TYPE, Identifier.of(Config.MOD_ID, "lissi"), LISSI);
        FabricDefaultAttributeRegistry.register(NYASHA, CompanionEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(LISSI, CompanionEntity.createAttributes());
        Registry.register(Registries.ITEM, Identifier.of(Config.MOD_ID, "heart"), HEART);
        Registry.register(Registries.ITEM, Identifier.of(Config.MOD_ID, "lissi_heart"), LISSI_HEART);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(g -> { g.add(HEART); g.add(LISSI_HEART); });
    }
}
