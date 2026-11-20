package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.resources.ResourceLocation;

public record EndEffect(long order) {
    public static final RimuPacket<EndEffect> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "end_effect"), EndEffect.class,
        (packet, buf) -> buf.writeLong(packet.order), buf -> new EndEffect(buf.readLong()));
}
