package ru.nyasha;

import com.google.gson.*;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.io.BufferedReader;
import java.util.*;

/** Порт dialogue_manager.js: загрузка data/nyasha/dialogues/**.json, выбор, анти-повторение. */
public final class DialogueManager implements SimpleSynchronousResourceReloadListener {
    public static final DialogueManager I = new DialogueManager();
    private static final Gson GSON = new Gson();
    private final Map<String, Dialogue> byId = new HashMap<>();
    private final Map<String, List<Dialogue>> byEvent = new HashMap<>();
    private final Map<String, Dialogue.ReactionSet> reactions = new HashMap<>();
    public int loaded, bad;

    /** Состояние мира для проверки условий. */
    public static class Ctx {
        public String timeOfDay = "day", dimension = "overworld", weather = "clear", stageKey;
        public Set<String> near = new HashSet<>(), states = new HashSet<>();
        public boolean enemies;
        public double distance, absence;
    }

    @Override public Identifier getFabricId() { return Identifier.of(Config.MOD_ID, "dialogues"); }

    @Override public void reload(ResourceManager rm) {
        byId.clear(); byEvent.clear(); reactions.clear(); loaded = 0; bad = 0;
        Map<Identifier, Resource> found = rm.findResources("dialogues", id -> id.getNamespace().equals(Config.MOD_ID) && id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> en : found.entrySet()) {
            try (BufferedReader r = en.getValue().getReader()) {
                JsonObject doc = JsonParser.parseReader(r).getAsJsonObject();
                if (doc.has("kind") && "reactions".equals(doc.get("kind").getAsString())) {
                    Dialogue.ReactionSet rs = GSON.fromJson(doc, Dialogue.ReactionSet.class);
                    reactions.put(rs.character, rs);
                    continue;
                }
                String character = doc.has("character") ? doc.get("character").getAsString() : null;
                String track = doc.has("track") ? doc.get("track").getAsString() : null;
                for (JsonElement el : doc.getAsJsonArray("dialogues")) {
                    Dialogue d = GSON.fromJson(el, Dialogue.class);
                    if (d.id == null || d.text == null || d.conditions == null || byId.containsKey(d.id)) { bad++; continue; }
                    if (d.character == null) d.character = character;
                    if (d.track == null) d.track = track;
                    byId.put(d.id, d);
                    for (String ev : eventsOf(d.conditions)) byEvent.computeIfAbsent(ev, k -> new ArrayList<>()).add(d);
                    loaded++;
                }
            } catch (Exception e) { bad++; Nyasha.LOG.warn("Диалоги: ошибка файла {}: {}", en.getKey(), e.toString()); }
        }
        Nyasha.LOG.info("Диалогов загружено: {} (ошибок: {}), событий: {}", loaded, bad, byEvent.size());
    }

    private static List<String> eventsOf(Dialogue.Cond c) {
        List<String> l = new ArrayList<>();
        if (c.event == null || c.event.isJsonNull()) l.add("ambient");
        else if (c.event.isJsonArray()) c.event.getAsJsonArray().forEach(e -> l.add(e.getAsString()));
        else l.add(c.event.getAsString());
        return l;
    }
    public Dialogue getById(String id) { return byId.get(id); }

    private static boolean in(double v, Double min, Double max) { return (min == null || v >= min) && (max == null || v <= max); }
    private static boolean inI(int v, Integer min, Integer max) { return (min == null || v >= min) && (max == null || v <= max); }

    /** -1 = не подходит, иначе бонус к весу. */
    private double matches(Dialogue.Cond c, Profile p, Ctx ctx, Set<String> evSet) {
        if (eventsOf(c).stream().noneMatch(evSet::contains)) return -1;
        double score = 0;
        if (!inI(p.friendStage, c.min_friend_stage, c.max_friend_stage)) return -1;
        if (!inI(p.loveStage, c.min_love_stage, c.max_love_stage)) return -1;
        if (!in(p.stat("affection"), c.min_affection, c.max_affection)) return -1;
        if (!in(p.stat("trust"), c.min_trust, c.max_trust)) return -1;
        if (!in(p.stat("romance"), c.min_romance, c.max_romance)) return -1;
        if (!in(p.stat("irritation"), c.min_irritation, c.max_irritation)) return -1;
        if (c.min_days_together != null && p.daysTogether < c.min_days_together) return -1;
        if (c.min_absence != null && ctx.absence < c.min_absence) return -1;
        if (c.stage_key != null && !c.stage_key.equals(ctx.stageKey)) return -1;
        if (c.requires_flag != null && !p.hasFlag(c.requires_flag)) return -1;
        if (c.forbids_flag != null && p.hasFlag(c.forbids_flag)) return -1;
        if (c.requires_memory != null) { if (!p.hasMemory(c.requires_memory)) return -1; score += 1.5; }
        if (c.time_of_day != null) { if (!c.time_of_day.equals(ctx.timeOfDay)) return -1; score += 0.5; }
        if (c.dimension != null) { if (!c.dimension.equals(ctx.dimension)) return -1; score += 0.5; }
        if (c.weather != null) { if (!c.weather.equals(ctx.weather)) return -1; score += 0.5; }
        if (c.near != null) { if (!ctx.near.contains(c.near)) return -1; score += 0.5; }
        if (c.enemies_nearby != null && ctx.enemies != c.enemies_nearby) return -1;
        if (c.distance_max != null && ctx.distance > c.distance_max) return -1;
        return score;
    }

    /** Выбор реплики: характер → условия → ссора → кулдаун → недавние → приоритет → взвешенный случай. */
    public Dialogue select(Profile p, Ctx ctx, Collection<String> events, boolean noResponses, long now) {
        Set<String> evSet = new HashSet<>(events);
        evSet.addAll(ctx.states);
        Set<String> recent = new HashSet<>(p.history.recent.subList(Math.max(0, p.history.recent.size() - Config.RECENT_HISTORY), p.history.recent.size()));
        boolean conflict = p.conflict.active;
        record Cand(Dialogue d, double sc) {}
        List<Cand> cand = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String ev : evSet) {
            for (Dialogue d : byEvent.getOrDefault(ev, List.of())) {
                if (!seen.add(d.id) || !p.character.equals(d.character)) continue;
                if (noResponses && !d.responses.isEmpty()) continue;
                double sc = matches(d.conditions, p, ctx, evSet);
                if (sc < 0) continue;
                boolean cTag = d.tags.contains("conflict") || Boolean.TRUE.equals(d.conditions.conflict);
                if (conflict && !cTag) continue;
                if (!conflict && Boolean.TRUE.equals(d.conditions.conflict)) continue;
                if (!d.repeatable && p.history.done.contains(d.id)) continue;
                Long last = p.history.last.get(d.id);
                if (last != null && now - last < d.cooldown) continue;
                if (recent.contains(d.id) && d.priority < 100) continue;
                cand.add(new Cand(d, sc));
            }
        }
        if (cand.isEmpty()) return null;
        int top = cand.stream().mapToInt(c -> c.d.priority).max().getAsInt();
        List<Cand> pool = cand.stream().filter(c -> c.d.priority == top).toList();
        double[] w = new double[pool.size()]; double sum = 0;
        for (int i = 0; i < w.length; i++) {
            Long ls = p.history.last.get(pool.get(i).d.id);
            double age = ls == null ? 3 : Math.min(3, (now - ls) / 12000.0);
            w[i] = 1 + pool.get(i).sc + age; sum += w[i];
        }
        double r = Math.random() * sum;
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (r <= 0) return pool.get(i).d; }
        return pool.get(pool.size() - 1).d;
    }

    public void markShown(Profile p, Dialogue d, long now) {
        p.history.last.put(d.id, now); p.history.recent.add(d.id);
        if (p.history.recent.size() > 40) p.history.recent.subList(0, p.history.recent.size() - 40).clear();
        if (!d.repeatable && !p.history.done.contains(d.id)) p.history.done.add(d.id);
        if (p.history.last.size() > Config.HISTORY_LIMIT) {
            List<String> ids = new ArrayList<>(p.history.last.keySet());
            ids.sort(Comparator.comparingLong(p.history.last::get));
            for (String id : ids.subList(0, ids.size() - Config.HISTORY_LIMIT)) p.history.last.remove(id);
        }
        p.lastSay = now;
    }

    public static String fill(String text, Profile p, String player) { return text.replace("{name}", p.name).replace("{player}", player); }

    /** Реакция на выбранный ответ — зависит от тона последствий, близости и ссоры. */
    public Dialogue.Reaction reactionFor(Profile p, Map<String, Double> applied) {
        Dialogue.ReactionSet set = reactions.get(p.character);
        if (set == null) return null;
        double score = applied.getOrDefault("affection", 0.0) + applied.getOrDefault("trust", 0.0) + applied.getOrDefault("romance", 0.0) - applied.getOrDefault("irritation", 0.0) * 1.2;
        String tone = score >= 4 ? "positive" : score <= -2 ? "negative" : "neutral";
        boolean close = p.loveStage >= 2 || (p.friendStage >= 2 && p.stat("affection") >= 60);
        String tier = p.conflict.active ? "conflict" : close ? "close" : "far";
        List<Dialogue.Reaction> list = set.tones.getOrDefault(tone, List.of());
        List<Dialogue.Reaction> pool = list.stream().filter(r -> r.tier == null || r.tier.equals("any") || r.tier.equals(tier)).toList();
        if (pool.isEmpty()) pool = list;
        return pool.isEmpty() ? null : pool.get(new Random().nextInt(pool.size()));
    }
    public String refusal(Profile p) {
        Dialogue.ReactionSet s = reactions.get(p.character);
        if (s == null || s.refusals.isEmpty()) return "…";
        return s.refusals.get(new Random().nextInt(s.refusals.size()));
    }
}
