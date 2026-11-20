package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.VisualKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public record StageEvent(int visual, long order, long tick, UUID caster,
                         ResourceLocation dimension, Vec3 center, long seed, int entityId, UUID anchorUuid, Vec3 offset) {
    public StageEvent(int visual, long order, long tick, UUID caster, ResourceLocation dimension, Vec3 center, long seed) {
        this(visual, order, tick, caster, dimension, center, seed, -1, null, Vec3.ZERO);
    }
    public static final RimuPacket<StageEvent> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "event"),
        StageEvent.class, StageEvent::write, StageEvent::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(VisualKind.byId(visual).networkKey());
        buf.writeLong(order);
        buf.writeLong(tick);
        buf.writeUUID(caster);
        buf.writeResourceLocation(dimension);
        buf.writeVec3(center);
        buf.writeLong(seed);
        buf.writeVarInt(entityId);
        if (entityId >= 0) { buf.writeUUID(anchorUuid); buf.writeVec3(offset); }
    }

    private static StageEvent read(FriendlyByteBuf buf) {
        int visual = VisualKind.localId(buf.readUtf());
        long order = buf.readLong(), tick = buf.readLong();
        UUID caster = buf.readUUID();
        ResourceLocation dimension = buf.readResourceLocation();
        Vec3 center = buf.readVec3();
        long seed = buf.readLong();
        int entityId = buf.readVarInt();
        return new StageEvent(visual, order, tick, caster, dimension, center, seed, entityId,
            entityId >= 0 ? buf.readUUID() : null, entityId >= 0 ? buf.readVec3() : Vec3.ZERO);
    }
}
