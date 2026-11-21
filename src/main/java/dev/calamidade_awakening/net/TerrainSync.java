package dev.calamidade_awakening.net;

import cyou.rimuru.rimulib.mc1211.api.NetApi;
import cyou.rimuru.rimulib.mc1211.net.RimuPacket;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * State of one terrain edit of an instance (0.11.0), channel {@code terrain_v1}. Sent once when the state changes and
 * again to late arrivals, never per tick:
 * <ul>
 * <li>{@link #COMMITTED}: the commit started at game tick {@code commitTick} with this origin and orientation (the
 * client draws the heat from the registered shape and the instance seed);</li>
 * <li>{@link #REFUSED}: the edit will not run (config, dimension, mobGriefing, a protection mod, volume);</li>
 * <li>{@link #STRUCTURE}: the blocks of the structure stand-in (rotated block state IDs, positions relative to the
 * structure corner), sent before the commit so the client can build the falling mesh.</li>
 * </ul>
 * A 0.10 client does not register this channel and simply draws no terrain visuals; run the same engine version on the
 * server and every client.
 */
public record TerrainSync(long order, int index, int state, long commitTick, Vec3 origin, float yaw, float pitch,
                          int sizeX, int sizeY, int sizeZ, int cornerX, int cornerY, int cornerZ,
                          int[] palette, int[] positions, int[] values) {
    public static final int COMMITTED = 1, REFUSED = 2, STRUCTURE = 3;
    /** Largest structure palette and block count accepted when reading (32 × 32 × 32 by default, 64³ at most). */
    private static final int MAX_BLOCKS = 64 * 64 * 64;

    public static final RimuPacket<TerrainSync> PACKET = NetApi.packet(
        ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "terrain_v1"),
        TerrainSync.class, TerrainSync::write, TerrainSync::read);

    private static final int[] NONE = new int[0];

    /** Commit started. */
    public static TerrainSync committed(long order, int index, long commitTick, Vec3 origin, float yaw, float pitch) {
        return new TerrainSync(order, index, COMMITTED, commitTick, origin, yaw, pitch, 0, 0, 0, 0, 0, 0, NONE, NONE, NONE);
    }

    /** Edit refused. */
    public static TerrainSync refused(long order, int index) {
        return new TerrainSync(order, index, REFUSED, 0, Vec3.ZERO, 0, 0, 0, 0, 0, 0, 0, 0, NONE, NONE, NONE);
    }

    /** Structure stand-in blocks: positions packed {@code x | y << 6 | z << 12} relative to the corner. */
    public static TerrainSync structure(long order, int index, int sizeX, int sizeY, int sizeZ, int cornerX, int cornerY,
                                        int cornerZ, int[] palette, int[] positions, int[] values) {
        return new TerrainSync(order, index, STRUCTURE, 0, Vec3.ZERO, 0, 0, sizeX, sizeY, sizeZ, cornerX, cornerY, cornerZ,
            palette, positions, values);
    }

    void write(FriendlyByteBuf buf) {
        buf.writeLong(order);
        buf.writeVarInt(index);
        buf.writeByte(state);
        if (state == COMMITTED) {
            buf.writeLong(commitTick);
            buf.writeVec3(origin);
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
        } else if (state == STRUCTURE) {
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeInt(cornerX);
            buf.writeInt(cornerY);
            buf.writeInt(cornerZ);
            buf.writeVarInt(palette.length);
            for (int id : palette) buf.writeVarInt(id);
            buf.writeVarInt(positions.length);
            for (int i = 0; i < positions.length; i++) {
                buf.writeVarInt(positions[i]);
                buf.writeVarInt(values[i]);
            }
        }
    }

    static TerrainSync read(FriendlyByteBuf buf) {
        long order = buf.readLong();
        int index = buf.readVarInt();
        int state = buf.readByte();
        if (state == COMMITTED) {
            long tick = buf.readLong();
            Vec3 origin = buf.readVec3();
            float yaw = buf.readFloat(), pitch = buf.readFloat();
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) { yaw = 0; pitch = 0; }
            return committed(order, index, tick, origin, yaw, Math.clamp(pitch, -90f, 90f));
        }
        if (state == STRUCTURE) {
            int sx = buf.readVarInt(), sy = buf.readVarInt(), sz = buf.readVarInt();
            int cx = buf.readInt(), cy = buf.readInt(), cz = buf.readInt();
            int paletteSize = buf.readVarInt();
            if (paletteSize < 0 || paletteSize > MAX_BLOCKS) throw new IllegalArgumentException("Structure palette too large: " + paletteSize);
            int[] palette = new int[paletteSize];
            for (int i = 0; i < paletteSize; i++) palette[i] = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_BLOCKS) throw new IllegalArgumentException("Structure too large: " + count);
            int[] positions = new int[count], values = new int[count];
            for (int i = 0; i < count; i++) {
                positions[i] = buf.readVarInt();
                values[i] = buf.readVarInt();
            }
            return structure(order, index, sx, sy, sz, cx, cy, cz, palette, positions, values);
        }
        return refused(order, index);
    }
}
