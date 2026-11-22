package dev.calamidade_awakening.world;

import com.mojang.logging.LogUtils;
import cyou.rimuru.rimulib.mc1211.api.NetApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.SkyProfileMath;
import dev.calamidade_awakening.api.v1.SkyProfile;
import dev.calamidade_awakening.api.v1.SkyProfiles;
import dev.calamidade_awakening.net.SkyProfileSync;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server side of the dimension skies (0.12.0): the current transition of every dimension with a {@link SkyProfile},
 * saved in that dimension's data ({@code data/calamidade_awakening_sky_profile.dat}) and sent ({@code sky_profile_v1})
 * once per change to the players in the dimension and to every player who enters it (checked every tick, and right
 * away on login, dimension change and respawn).
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class SkyProfileServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "calamidade_awakening_sky_profile";
    private static final Map<ResourceLocation, Entry> ENTRIES = new HashMap<>();

    private SkyProfileServer() {}

    /** One dimension: its transition and who already received it. */
    private static final class Entry {
        SkyProfileMath.Transition transition;
        final Set<ServerPlayer> recipients = new HashSet<>();
        Entry(SkyProfileMath.Transition transition) { this.transition = transition; }
    }

    /** Saved transition of one dimension. */
    static final class Data extends SavedData {
        SkyProfileMath.Transition transition;

        Data(SkyProfileMath.Transition transition) { this.transition = transition; }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            return write(transition, tag);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) { return new Data(read(tag)); }
    }

    /** NBT of a transition (also used by the tests). */
    public static CompoundTag write(SkyProfileMath.Transition t, CompoundTag tag) {
        if (t == null) return tag;
        tag.putString("from", t.from());
        tag.putString("to", t.to());
        tag.putLong("start", t.start());
        tag.putInt("ticks", t.ticks());
        return tag;
    }

    /** Transition of NBT, or null when there is none. */
    public static SkyProfileMath.Transition read(CompoundTag tag) {
        if (!tag.contains("to")) return null;
        return new SkyProfileMath.Transition(tag.getString("from"), tag.getString("to"), tag.getLong("start"), tag.getInt("ticks"));
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> new Data(null), Data::load), NAME);
    }

    /** Implementation of {@link SkyProfiles#setState}. */
    public static boolean setState(MinecraftServer server, ResourceLocation dimension, String state, int ticks) {
        if (!server.isSameThread()) throw new IllegalStateException("Call on the server thread");
        SkyProfile profile = SkyProfiles.profile(dimension).orElse(null);
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (profile == null || level == null) return false;
        profile.state(state);   // throws for unknown states
        Entry entry = ENTRIES.computeIfAbsent(dimension, d -> new Entry(SkyProfileMath.Transition.settled(profile.initial())));
        long now = server.overworld().getGameTime();
        var next = entry.transition.toward(state, now, ticks);
        if (next == entry.transition) return true;
        entry.transition = next;
        entry.recipients.clear();
        Data data = data(level);
        data.transition = next;
        data.setDirty();
        dispatch(server, dimension, entry);
        return true;
    }

    /** Implementation of {@link SkyProfiles#state}. */
    public static Optional<String> state(MinecraftServer server, ResourceLocation dimension) {
        Entry entry = ENTRIES.get(dimension);
        if (entry != null) return Optional.of(entry.transition.to());
        return SkyProfiles.profile(dimension).map(SkyProfile::initial);
    }

    private static void dispatch(MinecraftServer server, ResourceLocation dimension, Entry entry) {
        var players = server.getPlayerList().getPlayers();
        entry.recipients.retainAll(players);
        for (ServerPlayer player : players) dispatch(player, dimension, entry);
    }

    private static void dispatch(ServerPlayer player, ResourceLocation dimension, Entry entry) {
        boolean here = player.level().dimension().location().equals(dimension);
        if (!here) { entry.recipients.remove(player); return; }
        if (!NetApi.canSend(player, SkyProfileSync.PACKET) || !entry.recipients.add(player)) return;
        var t = entry.transition;
        NetApi.send(player, new SkyProfileSync(dimension, t.from(), t.to(), t.start(), t.ticks()));
    }

    private static void resend(ServerPlayer player) {
        ENTRIES.forEach((dimension, entry) -> {
            entry.recipients.remove(player);
            dispatch(player, dimension, entry);
        });
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (ENTRIES.isEmpty()) return;
        ENTRIES.forEach((dimension, entry) -> dispatch(event.getServer(), dimension, entry));
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) resend(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) resend(player);
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) resend(player);
    }

    /** Every dimension with a profile starts in its saved state, or the profile's initial one. */
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ENTRIES.clear();
        MinecraftServer server = event.getServer();
        SkyProfiles.profiles().forEach((dimension, profile) -> {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) {
                LOGGER.warn("Sky profile for {}: dimension not loaded", dimension);
                return;
            }
            var saved = data(level).transition;
            boolean valid = saved != null && profile.has(saved.from()) && profile.has(saved.to());
            ENTRIES.put(dimension, new Entry(valid ? saved : SkyProfileMath.Transition.settled(profile.initial())));
        });
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { ENTRIES.clear(); }
}
