package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.resources.ResourceLocation;

/** End of a global event (0.12.0, channel {@code world_end_v1}): stop, natural end or a player that stops seeing it. */
public record WorldEventEnd(long key) {
    public static final RimuPacket<WorldEventEnd> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "world_end_v1"), WorldEventEnd.class,
        (packet, buf) -> buf.writeLong(packet.key), buf -> new WorldEventEnd(buf.readLong()));
}
