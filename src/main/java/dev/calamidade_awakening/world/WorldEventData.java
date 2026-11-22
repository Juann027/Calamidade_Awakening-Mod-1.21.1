package dev.calamidade_awakening.world;

import dev.calamidade_awakening.api.v1.WorldEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent global events (0.12.0), saved with the world in {@code data/calamidade_awakening_world_events.dat} of the
 * overworld. Only events started with {@code persistent(true)}; each keeps its start tick (the server's game time,
 * which is saved with the world too), release age and cue progress, so it resumes in the same phase and no cue runs
 * twice. Written when the world saves (autosave and shutdown).
 */
public final class WorldEventData extends SavedData {
    static final String NAME = "calamidade_awakening_world_events";
    private static final int VERSION = 1;
    private final List<Saved> loaded;

    /**
     * One saved event.
     *
     * @param effect      network key of the effect
     * @param scope       DIMENSION or SERVER
     * @param dimension   origin dimension
     * @param x           origin x, blocks
     * @param y           origin y, blocks
     * @param z           origin z, blocks
     * @param tick        game time at the start
     * @param seed        event seed
     * @param released    real age of the release, -1 before it
     * @param priority    0..100
     * @param cuedThrough highest real age whose cues already ran
     */
    public record Saved(String effect, WorldEvents.Scope scope, String dimension, double x, double y, double z, long tick,
                        long seed, long released, int priority, long cuedThrough) {
        /** NBT of this event. */
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("effect", effect);
            tag.putString("scope", scope.name());
            tag.putString("dimension", dimension);
            tag.putDouble("x", x);
            tag.putDouble("y", y);
            tag.putDouble("z", z);
            tag.putLong("tick", tick);
            tag.putLong("seed", seed);
            tag.putLong("released", released);
            tag.putInt("priority", priority);
            tag.putLong("cuedThrough", cuedThrough);
            return tag;
        }

        /** Event from NBT, or null when the tag is malformed (unknown scope, missing effect). */
        public static Saved fromTag(CompoundTag tag) {
            WorldEvents.Scope scope;
            try {
                scope = WorldEvents.Scope.valueOf(tag.getString("scope"));
            } catch (IllegalArgumentException malformed) {
                return null;
            }
            String effect = tag.getString("effect");
            if (effect.isEmpty() || !tag.contains("tick", Tag.TAG_LONG)) return null;
            return new Saved(effect, scope, tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"),
                tag.getDouble("z"), tag.getLong("tick"), tag.getLong("seed"), tag.getLong("released"),
                Math.clamp(tag.getInt("priority"), 0, WorldEvents.MAX_PRIORITY), tag.getLong("cuedThrough"));
        }
    }

    private WorldEventData(List<Saved> loaded) { this.loaded = loaded; }

    /** Events read from disk when the server started. */
    List<Saved> saved() { return loaded; }

    /** Writes the persistent events that are active now. */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", VERSION);
        tag.put("events", write(WorldEventServer.persistent()));
        return tag;
    }

    /** NBT list of saved events (also used by the tests). */
    public static ListTag write(List<Saved> events) {
        ListTag list = new ListTag();
        for (Saved s : events) list.add(s.toTag());
        return list;
    }

    /** Saved events of an NBT list, skipping malformed entries. */
    public static List<Saved> read(ListTag list) {
        List<Saved> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Saved s = Saved.fromTag(list.getCompound(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    private static WorldEventData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new WorldEventData(read(tag.getList("events", Tag.TAG_COMPOUND)));
    }

    private static final SavedData.Factory<WorldEventData> FACTORY =
        new SavedData.Factory<>(() -> new WorldEventData(List.of()), WorldEventData::load);

    /** Data of this server (overworld storage), created empty when the world has none. */
    static WorldEventData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The set of persistent events changed: save at the next world save. */
    static void changed(MinecraftServer server) { get(server).setDirty(); }

    /** Every tick with persistent events: the cue progress changes, so keep the data dirty. */
    static void touch(MinecraftServer server) {
        if (!WorldEventServer.persistent().isEmpty()) get(server).setDirty();
    }
}
