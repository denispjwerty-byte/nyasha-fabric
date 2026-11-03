package ru.nyasha;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import java.util.*;

/** Сохранение профилей в мире (аналог Dynamic Properties). */
public class Profiles extends PersistentState {
    private static final Gson GSON = new Gson();
    public Map<String, Profile> map = new HashMap<>();
    public static final PersistentState.Type<Profiles> TYPE = new PersistentState.Type<>(Profiles::new, Profiles::load, null);

    public static Profiles get(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "nyasha_profiles");
    }
    public static Profiles load(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        Profiles p = new Profiles();
        try {
            Map<String, Profile> m = GSON.fromJson(nbt.getString("data"), new TypeToken<Map<String, Profile>>() {}.getType());
            if (m != null) p.map = m;
        } catch (Exception e) { Nyasha.LOG.warn("Не удалось прочитать профили", e); }
        return p;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putString("data", GSON.toJson(map));
        return nbt;
    }
    public Profile of(UUID owner, String kind, String character) {
        Config.Companion c = Config.COMPANIONS.get(kind);
        String id = c.prefix() + owner;
        Profile p = map.get(id);
        if (p == null) {
            p = new Profile();
            p.id = id; p.kind = kind; p.owner = owner.toString(); p.name = c.defaultName(); p.character = character;
            double[] st = Config.START.get(character);
            for (int i = 0; i < 4; i++) p.stats.put(Config.STAT_KEYS[i], st[i]);
            map.put(id, p);
            markDirty();
        }
        return p;
    }
    public Profile find(UUID owner, String kind) { return map.get(Config.COMPANIONS.get(kind).prefix() + owner); }
}
