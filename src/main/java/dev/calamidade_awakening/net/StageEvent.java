package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.VisualKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * Start of an instance, sent once to each player in range (and to late arrivals). Since 0.10.0 it travels on the
 * {@code event_v2} channel and carries the instance orientation: yaw and pitch in degrees and whether the yaw follows
 * the anchor's body. An engine older than 0.10.0 does not register {@code event_v2}, so mixed versions fail cleanly
 * (no instances) instead of misreading the packet: run the same engine version on the server and every client.
 */
public record StageEvent(int visual, long order, long tick, UUID caster,
                         ResourceLocation dimension, Vec3 center, long seed, int entityId, UUID anchorUuid, Vec3 offset,
                         float yaw, float pitch, boolean followBody) {
    public StageEvent(int visual, long order, long tick, UUID caster, ResourceLocation dimension, Vec3 center, long seed) {
        this(visual, order, tick, caster, dimension, center, seed, -1, null, Vec3.ZERO);
    }
    public StageEvent(int visual, long order, long tick, UUID caster, ResourceLocation dimension, Vec3 center, long seed,
                      int entityId, UUID anchorUuid, Vec3 offset) {
        this(visual, order, tick, caster, dimension, center, seed, entityId, anchorUuid, offset, 0, 0, false);
    }
    public static final RimuPacket<StageEvent> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "event_v2"),
        StageEvent.class, StageEvent::write, StageEvent::read);

    /** Fixed orientation of the instance (for {@link #followBody()} instances, the yaw is an offset to the body yaw). */
    public Basis basis() { return Basis.of(yaw, pitch); }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(VisualKind.byId(visual).networkKey());
        writeBody(buf);
    }

    /** Everything after the effect key; split out so the tests can round-trip it without the registry. */
    void writeBody(FriendlyByteBuf buf) {
        buf.writeLong(order);
        buf.writeLong(tick);
        buf.writeUUID(caster);
        buf.writeResourceLocation(dimension);
        buf.writeVec3(center);
        buf.writeLong(seed);
        buf.writeVarInt(entityId);
        if (entityId >= 0) { buf.writeUUID(anchorUuid); buf.writeVec3(offset); }
        // 0.10.0: orientation. Flags: bit 0 = the yaw follows the anchor's body.
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeByte(followBody ? 1 : 0);
    }

    private static StageEvent read(FriendlyByteBuf buf) {
        return readBody(VisualKind.localId(buf.readUtf()), buf);
    }

    static StageEvent readBody(int visual, FriendlyByteBuf buf) {
        long order = buf.readLong(), tick = buf.readLong();
        UUID caster = buf.readUUID();
        ResourceLocation dimension = buf.readResourceLocation();
        Vec3 center = buf.readVec3();
        long seed = buf.readLong();
        int entityId = buf.readVarInt();
        UUID anchorUuid = entityId >= 0 ? buf.readUUID() : null;
        Vec3 offset = entityId >= 0 ? buf.readVec3() : Vec3.ZERO;
        float yaw = buf.readFloat(), pitch = buf.readFloat();
        int flags = buf.readUnsignedByte();
        // A malformed orientation becomes "no orientation" instead of breaking the client.
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) { yaw = 0; pitch = 0; }
        return new StageEvent(visual, order, tick, caster, dimension, center, seed, entityId, anchorUuid, offset,
            Basis.wrapYaw(yaw), Math.clamp(pitch, -90f, 90f), (flags & 1) != 0 && entityId >= 0);
    }
}
