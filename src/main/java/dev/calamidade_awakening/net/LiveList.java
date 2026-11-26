package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.resources.ResourceLocation;

/**
 * 0.15.0 (canal {@code live_v1}): the orders of every instance a player should show, sent every
 * {@value StageNetwork#RECONCILE_PERIOD} ticks to players that have (or just had) one. The client drops any resident
 * instance missing from the list: an end that never arrived can no longer leave a sustained effect on screen forever.
 */
public record LiveList(long[] orders) {
    /** Most orders in one list (the server never has more live instances). */
    static final int MAX = 256;

    public static final RimuPacket<LiveList> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "live_v1"), LiveList.class,
        (packet, buf) -> {
            int n = Math.min(packet.orders.length, MAX);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeLong(packet.orders[i]);
        },
        buf -> {
            int n = Math.clamp(buf.readVarInt(), 0, MAX);
            long[] orders = new long[n];
            for (int i = 0; i < n; i++) orders[i] = buf.readLong();
            return new LiveList(orders);
        });
}
