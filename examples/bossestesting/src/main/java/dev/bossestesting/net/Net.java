package dev.bossestesting.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/** Channel of the addon: bossestesting:music_v1 (server → client). */
public final class Net {
    /** Client handler, installed by the client setup (no client class is touched on a dedicated server). */
    public static volatile Consumer<MusicPayload> musicHandler = p -> {};

    private Net() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional();
        r.playToClient(MusicPayload.TYPE, MusicPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> musicHandler.accept(payload)));
    }
}
