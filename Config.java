package ru.nyasha;

import java.util.*;

/** Порт config.js: все настраиваемые значения. */
public final class Config {
    private Config() {}
    public static final String MOD_ID = "nyasha";
    public static final String[] STAT_KEYS = {"affection", "trust", "romance", "irritation"};

    public record Companion(String kind, String prefix, String defaultName, String color, String[] chars, String[] aliases) {}
    public static final Map<String, Companion> COMPANIONS = Map.of(
        "nyasha", new Companion("nyasha", "", "Мелони", "§a", new String[]{"cute", "tsundere"},
            new String[]{"няша", "няшка", "няшенька", "няшечка", "няшуля", "няш", "nyasha", "nyasa", "njasha", "мелони", "мелонька", "мелоша", "мелка", "meloni", "melony"}),
        "lissi", new Companion("lissi", "L", "Лисси", "§6", new String[]{"meek", "sly"},
            new String[]{"лисси", "лисса", "лиска", "лисичка", "лисенок", "лисёнок", "лисонька", "лис", "lissi", "lisi", "lisa", "foxy"}));

    // Интервалы (тики)
    public static final int TICK_FAST = 20, TICK_SLOW = 200;
    public static final double NEAR = 12, TALK_RANGE = 6, FAR = 28;
    public static final int ABSENCE_RETURN = 6000, ABSENCE_LONG = 24000, AMBIENT_COOLDOWN = 2400, GLOBAL_DIALOGUE_GAP = 400;
    public static final int RECENT_HISTORY = 12, HISTORY_LIMIT = 220;

    public static final Map<String, double[]> START = Map.of( // affection, trust, romance, irritation
        "cute", new double[]{25, 20, 0, 0}, "tsundere", new double[]{10, 8, 0, 20},
        "meek", new double[]{15, 8, 0, 0}, "sly", new double[]{22, 12, 0, 4});
    public static final Map<String, double[]> GAIN_MULT = Map.of(
        "cute", new double[]{1.15, 1.0, 1.0, 0.8}, "tsundere", new double[]{0.85, 0.8, 0.8, 1.3},
        "meek", new double[]{1.1, 0.85, 0.85, 0.7}, "sly", new double[]{1.05, 0.95, 0.95, 0.9});
    public static final Map<String, Double> HOURLY_CAP = Map.of("affection", 16.0, "trust", 14.0, "romance", 10.0);
    public static final double DIMINISH_STEP = 0.25, DIMINISH_MIN = 0.2;
    public static final long DIMINISH_WINDOW = 6000;

    public record Stage(String name, double aff, double trust, double rom, int days, int friend, String... flagsAny) {}
    public static final Stage[] FRIEND_STAGES = {
        new Stage("Знакомство", 0, 0, 0, 0, 0),
        new Stage("Приятельство", 20, 15, 0, 1, 0),
        new Stage("Хорошая дружба", 40, 35, 0, 3, 0, "first_gift", "walked_together", "first_hug"),
        new Stage("Лучшие друзья", 65, 60, 0, 7, 0, "rescued_or_protected", "shared_adventure", "campfire_evening")};
    public static final Stage[] LOVE_STAGES = {
        new Stage("Нет", 0, 0, 0, 0, 0),
        new Stage("Симпатия", 30, 25, 15, 2, 1, "compliment_given", "first_hug"),
        new Stage("Влюблённость", 45, 40, 35, 5, 2, "first_hug", "walked_together"),
        new Stage("Сильная влюблённость", 60, 55, 55, 9, 2, "walked_together", "campfire_evening"),
        new Stage("Взаимная любовь", 75, 70, 75, 14, 3, "campfire_evening", "mutual_hint", "confession"),
        new Stage("Глубокая любовь", 90, 85, 90, 21, 3, "campfire_evening", "house_built", "mutual_hint")};

    /** Минимальные требования действий меню: ключ -> {affection, trust, friend, love, cooldown} с переопределением по характеру. */
    public record Req(double aff, double trust, int friend, int love, int cooldown) {}
    public static Req actionReq(String action, String ch) {
        return switch (action) {
            case "hug" -> switch (ch) { case "tsundere" -> new Req(30, 0, 1, 0, 600); case "meek" -> new Req(18, 10, 0, 0, 600); default -> new Req(10, 0, 0, 0, 600); };
            case "pet" -> switch (ch) { case "tsundere" -> new Req(45, 30, 2, 0, 600); case "meek" -> new Req(28, 18, 0, 0, 600); case "sly" -> new Req(22, 10, 0, 0, 600); default -> new Req(15, 0, 0, 0, 600); };
            case "walk" -> new Req(0, 0, 1, 0, 2400);
            case "rest" -> new Req(0, 0, 2, 0, 2400);
            case "holdhand" -> new Req(0, 0, 0, 1, 900);
            case "kiss" -> new Req(0, 0, 0, 0, 600);
            case "romantic_compliment" -> new Req(0, 0, 0, 1, 900);
            default -> new Req(0, 0, 0, 0, 0);
        };
    }
    public static final double SLAP_TRUST = 50, SLAP_DAMAGE = 1, SLAP_KNOCKBACK = 0.5;
    public static final double CONFLICT_START_IRR = 80, CONFLICT_HEAL_IRR = 45;
    public static final Map<String, Integer> CONFLICT_MIN_WAIT = Map.of("cute", 2400, "tsundere", 6000, "meek", 3000, "sly", 2400);
    public static final double IRR_DECAY_SLOW = 1.5;

    public record Gift(String pref, double a, double t) {}
    public static final Gift GIFT_DEFAULT = new Gift("neutral", 1, 0);
    public static final Map<String, Gift> GIFTS = new HashMap<>();
    public static final Map<String, Gift> GIFTS_LISSI = new HashMap<>();
    private static void g(Map<String, Gift> m, String id, String pref, double a, double t) { m.put("minecraft:" + id, new Gift(pref, a, t)); }
    static {
        g(GIFTS, "gunpowder", "love", 7, 1); g(GIFTS, "diamond", "love", 8, 2); g(GIFTS, "emerald", "love", 7, 2); g(GIFTS, "cake", "love", 8, 1);
        g(GIFTS, "cookie", "like", 4, 1); g(GIFTS, "sweet_berries", "like", 3, 0); g(GIFTS, "apple", "like", 3, 1); g(GIFTS, "golden_apple", "love", 9, 3);
        g(GIFTS, "poppy", "like", 4, 0); g(GIFTS, "dandelion", "like", 4, 0); g(GIFTS, "red_tulip", "like", 4, 0); g(GIFTS, "pink_tulip", "love", 6, 0);
        g(GIFTS, "cornflower", "like", 4, 0); g(GIFTS, "oxeye_daisy", "like", 4, 0); g(GIFTS, "azure_bluet", "like", 4, 0); g(GIFTS, "lily_of_the_valley", "like", 5, 0);
        g(GIFTS, "pink_petals", "love", 6, 0); g(GIFTS, "rose_bush", "love", 6, 0); g(GIFTS, "bread", "like", 3, 1); g(GIFTS, "cooked_beef", "like", 3, 1);
        g(GIFTS, "lapis_lazuli", "like", 3, 0); g(GIFTS, "gold_ingot", "like", 4, 0);
        g(GIFTS, "rotten_flesh", "dislike", -3, -1); g(GIFTS, "spider_eye", "dislike", -2, 0); g(GIFTS, "dirt", "dislike", -2, -1);
        g(GIFTS_LISSI, "sweet_berries", "love", 8, 1); g(GIFTS_LISSI, "glow_berries", "love", 7, 1); g(GIFTS_LISSI, "cookie", "love", 8, 1);
        g(GIFTS_LISSI, "honey_bottle", "love", 9, 2); g(GIFTS_LISSI, "pumpkin_pie", "love", 8, 1); g(GIFTS_LISSI, "sugar", "like", 5, 0);
        g(GIFTS_LISSI, "cake", "love", 10, 2); g(GIFTS_LISSI, "chicken", "like", 4, 1); g(GIFTS_LISSI, "cooked_chicken", "love", 6, 1);
        g(GIFTS_LISSI, "rabbit", "like", 4, 0); g(GIFTS_LISSI, "cooked_rabbit", "love", 6, 1); g(GIFTS_LISSI, "milk_bucket", "like", 3, 0);
        g(GIFTS_LISSI, "gunpowder", "neutral", 1, 0); g(GIFTS_LISSI, "dandelion", "love", 6, 0); g(GIFTS_LISSI, "poppy", "love", 6, 0);
    }

    /** Эффекты действий меню (ACTION_EFFECTS). */
    public static Dialogue.Effects actionEffects(String a) {
        Dialogue.Effects e = new Dialogue.Effects();
        switch (a) {
            case "hug" -> { e.affection = 3.0; e.trust = 1.0; e.romance = 1.0; e.flags_set = List.of("first_hug"); }
            case "pet" -> { e.affection = 2.0; e.trust = 1.0; }
            case "compliment" -> { e.affection = 3.0; e.flags_set = List.of("compliment_given"); }
            case "romantic_compliment" -> { e.romance = 4.0; e.affection = 2.0; e.flags_set = List.of("compliment_given"); }
            case "walk" -> { e.affection = 3.0; e.trust = 2.0; e.flags_set = List.of("walked_together"); }
            case "rest" -> { e.affection = 2.0; e.trust = 2.0; }
            case "holdhand" -> { e.romance = 3.0; e.affection = 2.0; }
            case "kiss" -> { e.romance = 5.0; e.affection = 2.0; e.trust = 1.0; }
            default -> {}
        }
        return e;
    }
    public static final Map<String, List<String>> ACTION_EVENT = Map.of(
        "hug", List.of("hug"), "pet", List.of("pet"), "compliment", List.of("compliment"),
        "romantic_compliment", List.of("compliment", "confession"), "walk", List.of("walk_invite"), "rest", List.of("rest"),
        "holdhand", List.of("hold_hand"), "kiss", List.of("kiss"), "mood", List.of("ask_mood"), "talk", List.of("talk", "ambient"));
    public static final Map<String, Integer> ACTION_LEN = Map.of("sleep", 160, "hug", 100, "kiss", 84, "slap", 46, "kiss_tsun", 74,
        "holdhand", 100, "wave", 50, "cheer", 60, "pout", 100, "joy", 40);
    public static final String[] EMOTIONS = {"neutral", "happy", "sad", "angry", "shy", "surprised", "scared", "sleepy", "jealous", "tender"};
    /** Реакция (эмоция, действие) на событие. Общая таблица (как у «милой»). */
    public static final Map<String, String[]> REACT = new HashMap<>();
    static {
        REACT.put("first_meet", new String[]{"happy", "wave"}); REACT.put("player_returned", new String[]{"happy", "hug"});
        REACT.put("gift", new String[]{"happy", "joy"}); REACT.put("hug", new String[]{"tender", "hug"}); REACT.put("pet", new String[]{"tender", "pet"});
        REACT.put("compliment", new String[]{"shy", "none"}); REACT.put("confession", new String[]{"shy", "romance"}); REACT.put("kiss", new String[]{"shy", "kiss"});
        REACT.put("hold_hand", new String[]{"tender", "holdhand"}); REACT.put("rescue", new String[]{"scared", "hug"});
        REACT.put("explosion_nearby", new String[]{"scared", "scared"}); REACT.put("hit_nyasha", new String[]{"sad", "none"}); REACT.put("insult", new String[]{"sad", "none"});
        REACT.put("jealousy_npc", new String[]{"jealous", "none"}); REACT.put("jealousy_pet", new String[]{"jealous", "none"});
        REACT.put("conflict_start", new String[]{"sad", "none"}); REACT.put("reconcile", new String[]{"happy", "hug"}); REACT.put("stage_up", new String[]{"happy", "cheer"});
        REACT.put("player_died", new String[]{"sad", "none"}); REACT.put("player_low_health", new String[]{"scared", "none"}); REACT.put("player_hurt", new String[]{"scared", "none"});
        REACT.put("night", new String[]{"sleepy", "none"}); REACT.put("player_sleep", new String[]{"sleepy", "sleep"}); REACT.put("rest", new String[]{"sleepy", "sleep"});
        REACT.put("walk_invite", new String[]{"happy", "cheer"}); REACT.put("rage_burst", new String[]{"angry", "pout"}); REACT.put("apology", new String[]{"tender", "none"});
    }
}
