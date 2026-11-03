package ru.nyasha;

import com.google.gson.JsonElement;
import java.util.*;

/** Формат dialogues/*.json (как в аддоне). */
public class Dialogue {
    public String id, character, track, category, text;
    public List<String> tags = new ArrayList<>();
    public int priority;
    public double cooldown = 1800;
    public boolean repeatable = true;
    public Cond conditions = new Cond();
    public List<Resp> responses = new ArrayList<>();

    public static class Cond {
        public JsonElement event;
        public Integer min_friend_stage, max_friend_stage, min_love_stage, max_love_stage;
        public Double min_affection, max_affection, min_trust, max_trust, min_romance, max_romance, min_irritation, max_irritation;
        public Double min_days_together, min_absence, distance_max;
        public String stage_key, requires_flag, forbids_flag, requires_memory, time_of_day, dimension, weather, near;
        public Boolean enemies_nearby, conflict;
    }
    public static class Resp { public String id, text; public Effects effects = new Effects(); }
    public static class Effects {
        public Double affection, trust, romance, irritation;
        public List<String> flags_set;
        public String promise;
        public Double get(String k) {
            return switch (k) { case "affection" -> affection; case "trust" -> trust; case "romance" -> romance; case "irritation" -> irritation; default -> null; };
        }
    }
    public static class Reaction { public String text, emotion, tier; }
    public static class ReactionSet { public String character; public List<String> refusals = new ArrayList<>(); public Map<String, List<Reaction>> tones = new HashMap<>(); }
}
