package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.VisualKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record CastRequest(int visual, float power) {
    public static final RimuPacket<CastRequest> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "request_v2"),
        CastRequest.class, CastRequest::write, CastRequest::read);

    private void write(FriendlyByteBuf buf) { buf.writeUtf(VisualKind.byId(visual).networkKey()); buf.writeFloat(power); }
    private static CastRequest read(FriendlyByteBuf buf) { return new CastRequest(VisualKind.localId(buf.readUtf()), buf.readFloat()); }
}
