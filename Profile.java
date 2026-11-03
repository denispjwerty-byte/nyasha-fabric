package ru.nyasha;

import java.util.*;

/** Профиль отношений одной пары игрок—спутница (save_manager.js). */
public class Profile {
    public String id, kind, owner, name, character;
    public Map<String, Double> stats = new HashMap<>();
    public double mood = 50;
    public int friendStage, loveStage;
    public long daysTogether, lastDayTick, lastSeen, lastSay;
    public Map<String, Long> flags = new HashMap<>();
    public Map<String, Integer> memory = new HashMap<>();
    public List<String> facts = new ArrayList<>();
    public Conflict conflict = new Conflict();
    public String stagePending;
    public Map<String, long[]> use = new HashMap<>();
    public Map<String, double[]> cap = new HashMap<>();
    public Map<String, Long> cooldowns = new HashMap<>();
    public History history = new History();

    public static class Conflict { public boolean active; public long since; }
    public static class History {
        public Map<String, Long> last = new HashMap<>();
        public List<String> recent = new ArrayList<>();
        public List<String> done = new ArrayList<>();
    }
    public double stat(String k) { return stats.getOrDefault(k, 0.0); }
    public boolean hasFlag(String f) { return flags.containsKey(f); }
    public void remember(String key) { memory.merge(key, 1, Integer::sum); }
    public boolean hasMemory(String key) { return memory.containsKey(key); }
}
