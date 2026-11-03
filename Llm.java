package ru.nyasha;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Необязательный LLM-мост (в Bedrock был отдельный пак для BDS). Java умеет HTTP сам, отдельный пак не нужен.
 *  Конфиг: config/nyasha.json  { "llm": { "enabled": false, "endpoint": "...", "model": "...", "apiKey": "", "timeoutSec": 20 } } */
public final class Llm {
    private Llm() {}
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static JsonObject cfg;
    private static long disabledUntil;
    private static int fails;

    public static void load() {
        Path f = FabricLoader.getInstance().getConfigDir().resolve("nyasha.json");
        try {
            if (!Files.exists(f)) {
                Files.writeString(f, "{\n  \"llm\": {\n    \"enabled\": false,\n    \"endpoint\": \"https://api.openai.com/v1/chat/completions\",\n    \"model\": \"gpt-4o-mini\",\n    \"apiKey\": \"\",\n    \"timeoutSec\": 20\n  }\n}\n");
            }
            cfg = JsonParser.parseString(Files.readString(f)).getAsJsonObject().getAsJsonObject("llm");
        } catch (Exception e) { Nyasha.LOG.warn("Конфиг LLM не прочитан: {}", e.toString()); cfg = null; }
    }
    public static boolean available() {
        return cfg != null && cfg.has("enabled") && cfg.get("enabled").getAsBoolean() && System.currentTimeMillis() > disabledUntil
            && !cfg.get("apiKey").getAsString().isBlank();
    }

    public static String persona(Profile p) {
        String base = switch (p.character) {
            case "cute" -> "Ты Мелони — милая девочка-крипер, добрая, ласковая, смущается. Иногда говоришь «Няя~».";
            case "tsundere" -> "Ты Мелони — девочка-крипер с характером «стервы»: колкая, не любит признавать симпатию, но в душе заботливая.";
            case "meek" -> "Ты Лисси — тихая стеснительная девочка-лисичка, говоришь коротко и робко.";
            default -> "Ты Лисси — хитрая любопытная девочка-лисичка, игривая и подкалывающая.";
        };
        return base + " Тебя зовут " + p.name + ". Отвечай по-русски, 1–2 короткие фразы, строго в образе, без эмодзи-спама. Ты спутница игрока в Minecraft."
            + " Отношения: симпатия " + Math.round(p.stat("affection")) + "/100, доверие " + Math.round(p.stat("trust")) + "/100, романтика " + Math.round(p.stat("romance"))
            + "/100, раздражение " + Math.round(p.stat("irritation")) + "/100; дней вместе " + p.daysTogether + (p.conflict.active ? "; вы в ссоре." : ".");
    }

    public static CompletableFuture<String> ask(Profile p, String player, String msg) {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.get("model").getAsString());
        body.addProperty("max_tokens", 120);
        JsonArray m = new JsonArray();
        JsonObject s = new JsonObject(); s.addProperty("role", "system"); s.addProperty("content", persona(p)); m.add(s);
        JsonObject u = new JsonObject(); u.addProperty("role", "user"); u.addProperty("content", player + ": " + msg); m.add(u);
        body.add("messages", m);
        int to = cfg.has("timeoutSec") ? cfg.get("timeoutSec").getAsInt() : 20;
        HttpRequest rq = HttpRequest.newBuilder(URI.create(cfg.get("endpoint").getAsString())).timeout(Duration.ofSeconds(to))
            .header("Content-Type", "application/json").header("Authorization", "Bearer " + cfg.get("apiKey").getAsString())
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
        return HTTP.sendAsync(rq, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
            if (r.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + r.statusCode());
            String t = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString().replace('§', ' ').replaceAll("[\\r\\n\\t]+", " ").trim();
            fails = 0;
            return t.length() > 240 ? t.substring(0, 240) : t;
        }).whenComplete((r, ex) -> { if (ex != null && ++fails >= 3) { disabledUntil = System.currentTimeMillis() + 60_000; fails = 0; } });
    }
}
