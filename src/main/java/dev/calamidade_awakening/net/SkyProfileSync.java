package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sky state of a dimension (0.12.0, channel {@code sky_profile_v1}): the transition from one state to another, sent on
 * each change and to every player who enters the dimension. The client blends by the game time.
 *
 * @param dimension dimension of the profile
 * @param from      state fading out
 * @param to        state fading in
 * @param start     game time when the transition started
 * @param ticks     duration, ticks
 */
public record SkyProfileSync(ResourceLocation dimension, String from, String to, long start, int ticks) {
    public static final RimuPacket<SkyProfileSync> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "sky_profile_v1"), SkyProfileSync.class,
        SkyProfileSync::write, SkyProfileSync::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeUtf(from, 64);
        buf.writeUtf(to, 64);
        buf.writeLong(start);
        buf.writeVarInt(ticks);
    }

    public static SkyProfileSync read(FriendlyByteBuf buf) {
        return new SkyProfileSync(buf.readResourceLocation(), buf.readUtf(64), buf.readUtf(64), buf.readLong(),
            Math.max(0, buf.readVarInt()));
    }
}
