package dev.bossestesting.net;

import dev.bossestesting.BossesTesting;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Server → client: start (fade 0) or stop (fade in ticks) the phase 2 track of an encounter. One packet per change
 * (start, leave, defeat), never per tick; the client keeps one instance per encounter and ignores repeated starts.
 */
public record MusicPayload(UUID encounter, boolean play, int fade) implements CustomPacketPayload {
    public static final Type<MusicPayload> TYPE = new Type<>(BossesTesting.id("music_v1"));
    public static final StreamCodec<ByteBuf, MusicPayload> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, MusicPayload::encounter,
        ByteBufCodecs.BOOL, MusicPayload::play,
        ByteBufCodecs.VAR_INT, MusicPayload::fade,
        MusicPayload::new);

    public static MusicPayload play(UUID encounter) { return new MusicPayload(encounter, true, 0); }
    public static MusicPayload stop(UUID encounter, int fade) { return new MusicPayload(encounter, false, fade); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
