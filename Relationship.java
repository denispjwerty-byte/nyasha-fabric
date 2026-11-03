package ru.nyasha;

import java.util.*;

/** Порт relationship_manager.js: 4 параметра, этапы, защита от накрутки. */
public final class Relationship {
    private Relationship() {}
    public static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    public static Map<String, Double> apply(Profile p, Dialogue.Effects e, String source, boolean noDiminish, long now) {
        Map<String, Double> applied = new HashMap<>();
        if (e == null) return applied;
        long[] u = p.use.get(source);
        double factor = 1;
        if (u != null && now - u[1] < Config.DIMINISH_WINDOW) factor = Math.max(Config.DIMINISH_MIN, 1 - u[0] * Config.DIMINISH_STEP);
        else p.use.put(source, new long[]{0, now});
        if (noDiminish) factor = 1;
        double[] mult = Config.GAIN_MULT.get(p.character);
        for (int i = 0; i < 4; i++) {
            String k = Config.STAT_KEYS[i];
            Double d0 = e.get(k);
            if (d0 == null || d0 == 0) continue;
            double d = d0;
            if (d > 0) {
                d *= factor * mult[i];
                if (k.equals("romance")) d *= Math.max(0.35, Math.min(1, 0.35 + p.stat("affection") / 100));
                Double cap = Config.HOURLY_CAP.get(k);
                if (!k.equals("irritation") && cap != null) {
                    double[] c = p.cap.computeIfAbsent(k, x -> new double[]{now, 0});
                    if (now - c[0] > 1000) { c[0] = now; c[1] = 0; }
                    double room = Math.max(0, cap - c[1]);
                    d = Math.min(d, room); c[1] += d;
                }
            }
            d = Math.round(d * 10) / 10.0;
            double before = p.stat(k);
            p.stats.put(k, clamp(before + d));
            applied.put(k, Math.round((p.stat(k) - before) * 10) / 10.0);
        }
        long[] uu = p.use.get(source);
        if (uu != null) { uu[0]++; uu[1] = now; }
        if (e.flags_set != null) for (String f : e.flags_set) p.flags.putIfAbsent(f, now);
        double score = applied.getOrDefault("affection", 0.0) + applied.getOrDefault("trust", 0.0) * 0.7
            + applied.getOrDefault("romance", 0.0) * 0.5 - applied.getOrDefault("irritation", 0.0) * 1.2;
        p.mood = clamp(p.mood + score * 1.5);
        return applied;
    }

    public static String moodLabel(Profile p) {
        double irr = p.stat("irritation");
        if (irr >= 70) return "злится";
        if (p.mood >= 75) return "в восторге";
        if (p.mood >= 58) return "радуется";
        if (p.mood <= 25) return "грустит";
        if (p.mood <= 40) return "не в духе";
        return "спокойно";
    }

    private static boolean reqMet(Config.Stage r, Profile p) {
        if (r.aff() > p.stat("affection") || r.trust() > p.stat("trust") || r.rom() > p.stat("romance")) return false;
        if (r.days() > p.daysTogether || r.friend() > p.friendStage) return false;
        if (r.flagsAny().length > 0 && Arrays.stream(r.flagsAny()).noneMatch(p::hasFlag)) return false;
        return true;
    }
    /** Возвращает ключ нового этапа ("friendship:2"/"love:1") или null. Только вверх. */
    public static String evaluateStages(Profile p) {
        if (p.stagePending != null || p.conflict.active) return null;
        int nf = p.friendStage + 1;
        if (nf < Config.FRIEND_STAGES.length && reqMet(Config.FRIEND_STAGES[nf], p)) { p.friendStage = nf; p.stagePending = "friendship:" + nf; p.remember("stage"); return p.stagePending; }
        int nl = p.loveStage + 1;
        if (nl < Config.LOVE_STAGES.length && reqMet(Config.LOVE_STAGES[nl], p)) { p.loveStage = nl; p.stagePending = "love:" + nl; p.remember("stage"); return p.stagePending; }
        return null;
    }
    public static String stageName(String track, int i) { return (track.startsWith("love") ? Config.LOVE_STAGES : Config.FRIEND_STAGES)[i].name(); }

    /** null = действие доступно, иначе причина отказа. */
    public static String actionBlocked(Profile p, String action, long now) {
        Config.Req r = Config.actionReq(action, p.character);
        if (r.aff() > p.stat("affection")) return "Пока рановато… (нужно больше симпатии)";
        if (r.trust() > p.stat("trust")) return "Пока рановато… (нужно больше доверия)";
        if (r.friend() > p.friendStage) return "Откроется на этапе дружбы «" + stageName("friendship", r.friend()) + "»";
        if (r.love() > p.loveStage) return "Откроется на этапе любви «" + stageName("love", r.love()) + "»";
        Long last = p.cooldowns.get(action);
        if (r.cooldown() > 0 && last != null && now - last < r.cooldown()) return "Подожди немного…";
        return null;
    }
}
