package ru.nyasha.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import ru.nyasha.CompanionEntity;
import ru.nyasha.Config;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CompanionRenderer<T extends CompanionEntity> extends GeoEntityRenderer<T> {
    public CompanionRenderer(EntityRendererFactory.Context ctx, String kind) {
        super(ctx, new GeoModel<T>() {
            @Override public Identifier getModelResource(T e) { return Identifier.of(Config.MOD_ID, "geo/entity/" + kind + ".geo.json"); }
            @Override public Identifier getAnimationResource(T e) { return Identifier.of(Config.MOD_ID, "animations/entity/" + kind + ".animation.json"); }
            @Override public Identifier getTextureResource(T e) {
                String tex = kind.equals("nyasha") ? "nyasha_" + (e.getCharacter().equals("tsundere") ? "tsundere" : "cute") : "lissi";
                return Identifier.of(Config.MOD_ID, "textures/entity/" + tex + ".png");
            }
        });
    }
}
