package ru.nyasha.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import ru.nyasha.ModRegistry;

public class NyashaClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(ModRegistry.NYASHA, ctx -> new CompanionRenderer<>(ctx, "nyasha"));
        EntityRendererRegistry.register(ModRegistry.LISSI, ctx -> new CompanionRenderer<>(ctx, "lissi"));
    }
}
