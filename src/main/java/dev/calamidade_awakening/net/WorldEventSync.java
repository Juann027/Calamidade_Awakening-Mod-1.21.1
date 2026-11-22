package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.v1.WorldEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * State of a global event (0.12.0, channel {@code world_event_v1}): sent on start, on release and to every player
 * who starts receiving it (login, dimension change, respawn). The client derives everything else from the clock
 * ({@code tick} is the server's game time at the start, shared by every dimension), so nothing is sent per tick. An
 * engine without this channel does not see global events: run the same version on the server and every client.
 *
 * @param key       event key on this server (not kept across restarts)
 * @param effect    network key of the effect
 * @param scope     DIMENSION or SERVER
 * @param dimension origin dimension
 * @param origin    origin position, blocks
 * @param tick      server game time at the start (its age is now - tick)
 * @param seed      seed of the event (the instance seed of its sky layers)
 * @param released  real age of the release, -1 before it
 * @param priority  0..100
 */
public record WorldEventSync(long key, String effect, WorldEvents.Scope scope, ResourceLocation dimension, Vec3 origin,
                             long tick, long seed, long released, int priority) {
    public static final RimuPacket<WorldEventSync> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "world_event_v1"), WorldEventSync.class,
        WorldEventSync::write, WorldEventSync::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(key);
        buf.writeUtf(effect);
        buf.writeByte(scope.ordinal());
        buf.writeResourceLocation(dimension);
        buf.writeVec3(origin);
        buf.writeLong(tick);
        buf.writeLong(seed);
        buf.writeVarLong(released + 1);
        buf.writeByte(priority);
    }

    public static WorldEventSync read(FriendlyByteBuf buf) {
        long key = buf.readLong();
        String effect = buf.readUtf();
        int scope = buf.readUnsignedByte();
        var scopes = WorldEvents.Scope.values();
        ResourceLocation dimension = buf.readResourceLocation();
        Vec3 origin = buf.readVec3();
        long tick = buf.readLong(), seed = buf.readLong(), released = buf.readVarLong() - 1;
        int priority = buf.readUnsignedByte();
        return new WorldEventSync(key, effect, scope < scopes.length ? scopes[scope] : WorldEvents.Scope.SERVER, dimension,
            origin, tick, seed, released, Math.clamp(priority, 0, WorldEvents.MAX_PRIORITY));
    }
}
