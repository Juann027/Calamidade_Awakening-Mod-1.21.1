package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.resources.ResourceLocation;

/**
 * Release of a sustained instance (0.7.0). {@code age} is the real age, in ticks, at which the timeline jumps to its
 * release start. Sent to every recipient when released and right after the start event to late arrivals.
 */
public record ReleaseEffect(long order, long age) {
    public static final RimuPacket<ReleaseEffect> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "release"), ReleaseEffect.class,
        (packet, buf) -> { buf.writeLong(packet.order); buf.writeVarLong(packet.age); },
        buf -> new ReleaseEffect(buf.readLong(), buf.readVarLong()));
}
