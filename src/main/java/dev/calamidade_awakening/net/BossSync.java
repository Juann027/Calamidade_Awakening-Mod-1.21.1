package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.BossBarMath;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * State of a boss bar beyond the vanilla bar (0.13.0, channel {@code boss_v1}): sent once per state change (attach,
 * phase change, vulnerability) and to every player who starts seeing the bar (late arrivals, dimension change,
 * respawn), never per tick. The vanilla bar (name, progress, color) travels on the game's own packets, keyed by the same
 * {@code bar} UUID.
 *
 * @param bar        UUID of the vanilla boss event
 * @param removed    the bar is gone: forget it
 * @param style      client style id, or empty for vanilla
 * @param color      bar color ordinal ({@code Boss.BarColor}) of the current phase
 * @param overlay    vanilla overlay ordinal ({@code Boss.BarOverlay})
 * @param label      translation key of the phase label, or empty
 * @param marks      segment marks, high to low, inside (0, 1)
 * @param phase      phase index
 * @param phaseTick  game time when the phase began (flash and label transitions are computed from it)
 * @param vulnerable false while the phase cancels damage
 */
public record BossSync(UUID bar, boolean removed, String style, int color, int overlay, String label, float[] marks,
                       int phase, long phaseTick, boolean vulnerable) {
    public static final RimuPacket<BossSync> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "boss_v1"), BossSync.class, BossSync::write, BossSync::read);

    /** The removal packet of a bar. */
    public static BossSync removal(UUID bar) { return new BossSync(bar, true, "", 0, 0, "", new float[0], 0, 0, true); }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(bar);
        buf.writeBoolean(removed);
        if (removed) return;
        buf.writeUtf(style, 256);
        buf.writeByte(color);
        buf.writeByte(overlay);
        buf.writeUtf(label, 256);
        int n = Math.min(marks.length, BossBarMath.MAX_MARKS);
        buf.writeByte(n);
        for (int i = 0; i < n; i++) buf.writeFloat(marks[i]);
        buf.writeVarInt(phase);
        buf.writeLong(phaseTick);
        buf.writeBoolean(vulnerable);
    }

    public static BossSync read(FriendlyByteBuf buf) {
        UUID bar = buf.readUUID();
        if (buf.readBoolean()) return removal(bar);
        String style = buf.readUtf(256);
        int color = buf.readUnsignedByte(), overlay = buf.readUnsignedByte();
        String label = buf.readUtf(256);
        int n = Math.min(buf.readUnsignedByte(), BossBarMath.MAX_MARKS);
        float[] marks = new float[n];
        for (int i = 0; i < n; i++) marks[i] = buf.readFloat();
        int phase = buf.readVarInt();
        long phaseTick = buf.readLong();
        boolean vulnerable = buf.readBoolean();
        return new BossSync(bar, false, style, color, overlay, label, marks, phase, phaseTick, vulnerable);
    }
}
