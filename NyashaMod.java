package ru.nyasha;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class NyashaMod implements ModInitializer {
    @Override public void onInitialize() {
        ModRegistry.init();
        Llm.load();
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(DialogueManager.I);

        ServerMessageEvents.CHAT_MESSAGE.register((msg, sender, params) -> {
            String text = msg.getSignedContent();
            sender.getServer().execute(() -> Chat.onMessage(sender, text));
        });

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(CommandManager.literal("nyasha")
            .then(CommandManager.literal("act")
                .then(CommandManager.argument("action", StringArgumentType.word())
                    .executes(c -> { Menu.act(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "action"), null); return 1; })
                    .then(CommandManager.argument("kind", StringArgumentType.word())
                        .executes(c -> { Menu.act(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "action"), StringArgumentType.getString(c, "kind")); return 1; }))))
            .then(CommandManager.literal("reply")
                .then(CommandManager.argument("dialogue", StringArgumentType.word())
                    .then(CommandManager.argument("response", StringArgumentType.word())
                        .executes(c -> { Menu.reply(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "dialogue"), StringArgumentType.getString(c, "response")); return 1; }))))
            .then(CommandManager.literal("create")
                .then(CommandManager.argument("kind", StringArgumentType.word())
                    .then(CommandManager.argument("character", StringArgumentType.word())
                        .executes(c -> { Menu.create(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "kind"), StringArgumentType.getString(c, "character"), null); return 1; })
                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                            .executes(c -> { Menu.create(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "kind"), StringArgumentType.getString(c, "character"), StringArgumentType.getString(c, "name")); return 1; })))))
            .then(CommandManager.literal("say")
                .then(CommandManager.argument("text", StringArgumentType.greedyString())
                    .executes(c -> { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); Chat.onMessage(p, StringArgumentType.getString(c, "text")); return 1; })))
            .then(CommandManager.literal("info").executes(c -> {
                c.getSource().sendFeedback(() -> Text.literal("Диалогов: " + DialogueManager.I.loaded + ", ошибок: " + DialogueManager.I.bad + ", LLM: " + (Llm.available() ? "вкл" : "выкл")), false);
                return 1; })))); 
    }
}
