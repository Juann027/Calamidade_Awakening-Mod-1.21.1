package dev.calamidade_awakening.api.v1;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable terrain edit of one effect (0.11.0): an excavation ({@link TerrainShape}) with an optional burnt shell
 * ({@link TerrainPalette}), real blocks placed in the middle (a {@link Fill} or a structure template), the moment of the
 * commit (a marker of the effect's {@link Timeline}, normally the impact frame) and the client visuals that go with it:
 * the cooling heat over the edited area and an optional falling stand-in ({@link StandIn}). Pure Java, no Minecraft
 * types. Register with {@link Effects#terrain} on both sides during mod construction.
 *
 * <p><b>How it runs.</b> When an instance starts, the server checks the safety rules (server config, allowed
 * dimensions, {@code mobGriefing} for mob sources, the cancellable {@code TerrainEditEvent}) and computes the plan on
 * a worker thread from the shape, the instance center, orientation and seed (no world reads). At the marker it writes
 * the blocks straight into the chunk sections on the server thread, {@link Builder#blocksPerTick} blocks per tick
 * (server default when 0): no drops, no neighbour updates, no physics; light and the changed sections are resent at the
 * end of each tick's batch. Bedrock, unbreakable blocks (hardness &lt; 0), blocks with a block entity (unless
 * {@link Builder#replacingBlockEntities()} and the server allows it) and the block tag
 * {@code calamidade_awakening:terrain_protected} are never touched. Entities in the area are not removed: damage and
 * knockback stay in your cues. Every committed edit can be undone with {@code /calamidade terrain undo}.
 *
 * <p><b>Under the impact frame.</b> Put the commit marker on the tick of the definition's {@link Effects.Impact} and give
 * the impact 4 to 8 ticks: the flash hides the moment the blocks disappear and the sections are resent. Then the heat
 * (ember glow on the crater faces, glowing cracks, light smoke, an incandescent strip along trenches and an orange
 * light from the Light API) cools over {@link Builder#cooling cooling} ticks or a {@link Builder#heatTrack track}. The
 * client computes the heated area from the same shape and seed, without receiving the blocks.
 *
 * <pre>{@code
 * static final TerrainEdit METEORO = TerrainEdit.carve(TerrainShape.crater(8, 5).rough(1, 3))
 *     .commitAt("impacto")
 *     .burn(TerrainPalette.burnt(), 2)
 *     .fill(TerrainEdit.Fill.solid("minecraft:raw_gold_block", 2.5f).at(0, -2, 0))
 *     .standIn(TerrainEdit.StandIn.sphere(2.5f).from(-20, 90, -30).fall(10).reveal(40))
 *     .build();
 * Effects.terrain(ID, METEORO);   // after register and timeline
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class TerrainEdit {
    /** Most terrain edits per effect ({@link Effects#terrain}). */
    public static final int MAX_PER_EFFECT = 4;
    /** Thickest burnt shell, blocks. */
    public static final int MAX_THICKNESS = 4;
    /** Largest blocks-per-tick value an edit may ask for (the server config caps it too). */
    public static final int MAX_BLOCKS_PER_TICK = 1_000_000;
    /** Longest cooling, ticks. */
    public static final int MAX_COOLING = 6000;
    /** Largest fill radius, blocks. */
    public static final float MAX_FILL_RADIUS = 16;
    /** Largest offset of a fill, a structure or a stand-in start, blocks per axis. */
    public static final float MAX_OFFSET = 256;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** What a {@link Fill} places. */
    public enum FillKind {
        /** A solid ball of the block. */
        SOLID,
        /** A hollow ball one block thick. */
        SHELL
    }

    /**
     * Real blocks placed at the commit, after the excavation: the stand-in becoming real (a meteor turning into a ball
     * of ore). Positions are in the instance frame from the edit origin.
     *
     * @param kind   solid or hollow ball
     * @param radius radius, blocks, (0, {@value #MAX_FILL_RADIUS}]
     * @param block  registry ID of the block ({@code namespace:path}); a block with a block entity is placed without
     *               its data
     * @param x      center offset to the left, blocks
     * @param y      center offset up, blocks
     * @param z      center offset forward, blocks
     */
    public record Fill(FillKind kind, float radius, String block, float x, float y, float z) {
        public Fill {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(block, "block");
            if (!ID.matcher(block).matches()) throw new IllegalArgumentException("Expected a block ID namespace:path, got " + block);
            if (!Float.isFinite(radius) || radius <= 0 || radius > MAX_FILL_RADIUS)
                throw new IllegalArgumentException("Expected a fill radius in (0, " + MAX_FILL_RADIUS + "], got " + radius);
            offset(x, y, z);
        }

        /** Solid ball of {@code block} with {@code radius} blocks at the origin. */
        public static Fill solid(String block, float radius) { return new Fill(FillKind.SOLID, radius, block, 0, 0, 0); }

        /** Hollow ball (one block thick) of {@code block} with {@code radius} blocks at the origin. */
        public static Fill shell(String block, float radius) { return new Fill(FillKind.SHELL, radius, block, 0, 0, 0); }

        /** Copy centered {@code right}, {@code up} and {@code forward} blocks from the origin (instance frame). */
        public Fill at(float right, float up, float forward) { return new Fill(kind, radius, block, -right, up, forward); }

        /** True when the local point (blocks, instance frame) is inside the fill. */
        public boolean contains(double px, double py, double pz) {
            double dx = px - x, dy = py - y, dz = pz - z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return d <= radius && (kind == FillKind.SOLID || d > radius - 1);
        }
    }

    private static void offset(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
            || Math.abs(x) > MAX_OFFSET || Math.abs(y) > MAX_OFFSET || Math.abs(z) > MAX_OFFSET)
            throw new IllegalArgumentException("Expected finite offsets within ±" + MAX_OFFSET + " blocks");
    }

    private static String name(String what, String value) {
        Objects.requireNonNull(value, what);
        if (!NAME.matcher(value).matches()) throw new IllegalArgumentException("Invalid " + what + " name: " + value);
        return value;
    }

    /**
     * Visual stand-in of a falling object (0.11.0): drawn by the client while it falls from {@link Builder#from} to the
     * impact point, between {@link Builder#fall} and the commit marker (or along a 0..1 {@link Builder#track track}),
     * with a fire trail. A {@link Kind#SPHERE} is drawn by the engine's terrain pass; a {@link Kind#STRUCTURE} is the
     * edit's structure template drawn as a block mesh (built once per template on the client and cached). At the commit
     * the real blocks appear under it; the stand-in stays over them and fades during {@link Builder#reveal} ticks.
     */
    public static final class StandIn {
        /** Look of the stand-in. */
        public enum Kind {
            /** A burning ball drawn by the engine's shader. */
            SPHERE,
            /** The edit's structure template as a moving block mesh. */
            STRUCTURE
        }

        private final Kind kind;
        private final float radius, x, y, z, trail;
        private final int fall, reveal, color;
        private final String track;

        private StandIn(Builder b) {
            kind = b.kind; radius = b.radius; x = b.x; y = b.y; z = b.z; trail = b.trail;
            fall = b.fall; reveal = b.reveal; color = b.color; track = b.track;
        }

        /** Burning ball of {@code radius} blocks, (0, {@value #MAX_FILL_RADIUS}]; it lands on the fill center, or on the origin. */
        public static Builder sphere(float radius) {
            if (!Float.isFinite(radius) || radius <= 0 || radius > MAX_FILL_RADIUS)
                throw new IllegalArgumentException("Expected a stand-in radius in (0, " + MAX_FILL_RADIUS + "], got " + radius);
            return new Builder(Kind.SPHERE, radius);
        }

        /** The edit's structure template as a falling block mesh; it lands where the structure is placed. */
        public static Builder structure() { return new Builder(Kind.STRUCTURE, 0); }

        /** Look of the stand-in. */
        public Kind kind() { return kind; }
        /** Ball radius, blocks (0 for a structure). */
        public float radius() { return radius; }
        /** Start of the fall to the left of the impact point, blocks (instance frame). */
        public float x() { return x; }
        /** Start of the fall above the impact point, blocks. */
        public float y() { return y; }
        /** Start of the fall forward of the impact point, blocks (instance frame). */
        public float z() { return z; }
        /** Local tick when the fall starts (before the commit marker). */
        public int fall() { return fall; }
        /** Ticks the stand-in takes to fade over the real blocks after the commit. */
        public int reveal() { return reveal; }
        /** Fire color, ARGB (alpha ignored). */
        public int color() { return color; }
        /** Length of the fire trail, blocks (0 for none). */
        public float trail() { return trail; }
        /** Track giving the fall progress 0..1 (eased by the track itself), or null for the default ease-in. */
        public String track() { return track; }

        /**
         * Fall progress 0..1 at a local age: the {@link #track()} value when set, otherwise {@code t²} (accelerating)
         * with {@code t} going from 0 at {@link #fall()} to 1 at {@code commitTick}.
         */
        public float progress(Timeline timeline, double localAge, int commitTick) {
            if (track != null && timeline != null) return Math.clamp(timeline.value(track, localAge), 0f, 1f);
            if (commitTick <= fall) return 1;
            double t = Math.clamp((localAge - fall) / (commitTick - fall), 0, 1);
            return (float) (t * t);
        }

        /** Opacity 0..1 at a local age: 1 until the commit, then fading to 0 over {@link #reveal()} ticks. */
        public float opacity(double localAge, int commitTick) {
            if (localAge < fall) return 0;
            if (localAge <= commitTick) return 1;
            if (reveal == 0) return 0;
            double t = Math.clamp((localAge - commitTick) / reveal, 0, 1);
            return (float) (1 - t * t * (3 - 2 * t));
        }

        /** Readable form, such as {@code StandIn[SPHERE]}. */
        @Override public String toString() { return "StandIn[" + kind + "]"; }

        /** Builder of a {@link StandIn}. */
        public static final class Builder {
            private final Kind kind;
            private final float radius;
            private float x, y = 80, z, trail = 16;
            private int fall, reveal = 30, color = 0xffff8a2a;
            private String track;

            private Builder(Kind kind, float radius) { this.kind = kind; this.radius = radius; }

            /**
             * Start of the fall relative to the impact point: {@code right}, {@code up}, {@code forward} blocks in the
             * instance frame (default straight above, 80 blocks), each within ±{@value #MAX_OFFSET}.
             */
            public Builder from(float right, float up, float forward) {
                offset(right, up, forward);
                x = -right; y = up; z = forward;
                return this;
            }

            /** Local tick when the fall starts, 0..commit tick − 1 (default 0). */
            public Builder fall(int startTick) {
                if (startTick < 0 || startTick >= Effects.MAX_TICKS) throw new IllegalArgumentException("Invalid fall start tick " + startTick);
                fall = startTick;
                return this;
            }

            /** Fall progress from a timeline track (values 0..1) instead of the default ease-in. */
            public Builder track(String name) { track = name("track", name); return this; }

            /** Ticks of the fade over the real blocks after the commit, 0..400 (default 30). */
            public Builder reveal(int ticks) {
                if (ticks < 0 || ticks > 400) throw new IllegalArgumentException("Expected reveal ticks in [0, 400], got " + ticks);
                reveal = ticks;
                return this;
            }

            /** Fire color, ARGB (alpha ignored; default orange 0xffff8a2a). */
            public Builder color(int argb) { color = argb; return this; }

            /** Length of the fire trail, 0..64 blocks (default 16). */
            public Builder trail(float blocks) {
                if (!Float.isFinite(blocks) || blocks < 0 || blocks > 64)
                    throw new IllegalArgumentException("Expected a trail length in [0, 64], got " + blocks);
                trail = blocks;
                return this;
            }

            /** The stand-in. */
            public StandIn build() { return new StandIn(this); }
        }
    }

    private final TerrainShape shape;
    private final String marker;
    private final TerrainPalette palette;
    private final int thickness;
    private final Fill fill;
    private final String structure;
    private final float structureX, structureY, structureZ;
    private final int blocksPerTick;
    private final boolean blockEntities;
    private final boolean heat;
    private final int cooling, glow;
    private final String heatTrack;
    private final float smoke, cracks;
    private final StandIn standIn;

    private TerrainEdit(Builder b) {
        shape = b.shape; marker = b.marker; palette = b.palette; thickness = b.thickness; fill = b.fill;
        structure = b.structure; structureX = b.structureX; structureY = b.structureY; structureZ = b.structureZ;
        blocksPerTick = b.blocksPerTick; blockEntities = b.blockEntities; heat = b.heat; cooling = b.cooling;
        glow = b.glow; heatTrack = b.heatTrack; smoke = b.smoke; cracks = b.cracks; standIn = b.standIn;
    }

    /** New edit that carves {@code shape}; call {@link Builder#commitAt} before {@link Builder#build}. */
    public static Builder carve(TerrainShape shape) { return new Builder(Objects.requireNonNull(shape, "shape")); }

    /** Excavation shape. */
    public TerrainShape shape() { return shape; }
    /** Timeline marker of the commit. */
    public String marker() { return marker; }
    /** Burnt shell palette, or null for no shell. */
    public TerrainPalette palette() { return palette; }
    /** Shell thickness, blocks (0 without a shell). */
    public int thickness() { return thickness; }
    /** Real blocks placed in the middle, or null. */
    public Fill fill() { return fill; }
    /** Structure template ID ({@code data/<namespace>/structure/<path>.nbt}), or null. */
    public String structure() { return structure; }
    /** Structure offset to the left of the origin, blocks (instance frame snapped to 90°). */
    public float structureX() { return structureX; }
    /** Structure offset up from the origin, blocks. */
    public float structureY() { return structureY; }
    /** Structure offset forward from the origin, blocks (instance frame snapped to 90°). */
    public float structureZ() { return structureZ; }
    /** Blocks written per tick at the commit, or 0 for the server default (capped by the server config). */
    public int blocksPerTick() { return blocksPerTick; }
    /** True when blocks with a block entity may be replaced (the server config must allow it too). */
    public boolean replacesBlockEntities() { return blockEntities; }
    /** True when the client draws the heat after the commit. */
    public boolean heat() { return heat; }
    /** Ticks the heat takes to cool down without a heat track. */
    public int cooling() { return cooling; }
    /** Ember color of the heat, ARGB (alpha ignored). */
    public int glow() { return glow; }
    /** Track giving the heat 0..1 after the commit, or null for the default cooling. */
    public String heatTrack() { return heatTrack; }
    /** Smoke amount over the hot area, 0..1. */
    public float smoke() { return smoke; }
    /** Strength of the glowing cracks, 0..1. */
    public float cracks() { return cracks; }
    /** Falling stand-in, or null. */
    public StandIn standIn() { return standIn; }

    /**
     * Heat 0..1 at a local age, {@code sinceCommit} ticks after the commit (negative before it): 0 before the commit;
     * then the {@link #heatTrack()} value, or {@code (1 - sinceCommit / cooling)^1.6} down to 0. Always 0 without heat.
     */
    public float heatAt(Timeline timeline, double localAge, double sinceCommit) {
        if (!heat || sinceCommit < 0) return 0;
        if (heatTrack != null && timeline != null) return Math.clamp(timeline.value(heatTrack, localAge), 0f, 1f);
        double t = Math.clamp(1 - sinceCommit / cooling, 0, 1);
        return (float) Math.pow(t, 1.6);
    }

    /**
     * Checks the edit against the effect's timeline and duration: the commit marker and the tracks exist, the stand-in
     * starts falling before the marker, and the structure stand-in has a structure. Called by {@link Effects#terrain}.
     */
    public void validate(Timeline timeline, int duration) {
        if (timeline == null)
            throw new IllegalArgumentException("A terrain edit needs the effect's timeline (register it before Effects.terrain)");
        if (!timeline.hasMarker(marker)) throw new IllegalArgumentException("Unknown commit marker: " + marker);
        int commit = timeline.tick(marker);
        if (commit >= duration) throw new IllegalArgumentException("Commit marker beyond the effect duration: " + marker);
        if (heatTrack != null && !timeline.hasTrack(heatTrack)) throw new IllegalArgumentException("Unknown heat track: " + heatTrack);
        if (standIn != null) {
            if (standIn.track() != null && !timeline.hasTrack(standIn.track()))
                throw new IllegalArgumentException("Unknown stand-in track: " + standIn.track());
            if (standIn.track() == null && standIn.fall() >= commit)
                throw new IllegalArgumentException("The stand-in must start falling before the commit marker (tick " + commit + ")");
            if (standIn.kind() == StandIn.Kind.STRUCTURE && structure == null)
                throw new IllegalArgumentException("A structure stand-in needs Builder.structure(...)");
        }
    }

    /** Readable form with the shape, the marker and the structure. */
    @Override public String toString() {
        return "TerrainEdit[" + shape + " at " + marker + (structure != null ? ", structure " + structure : "") + "]";
    }

    /** Builder of a {@link TerrainEdit}. */
    public static final class Builder {
        private final TerrainShape shape;
        private String marker;
        private TerrainPalette palette;
        private int thickness;
        private Fill fill;
        private String structure;
        private float structureX, structureY, structureZ;
        private int blocksPerTick;
        private boolean blockEntities;
        private boolean heat = true;
        private int cooling = 200, glow = 0xffff5a14;
        private String heatTrack;
        private float smoke = .5f, cracks = .8f;
        private StandIn standIn;

        private Builder(TerrainShape shape) { this.shape = shape; }

        /** Timeline marker of the commit (required), normally the impact frame tick. */
        public Builder commitAt(String marker) { this.marker = name("marker", marker); return this; }

        /** Burnt shell of {@code thickness} blocks (1..{@value #MAX_THICKNESS}) around the excavation, from {@code palette}. */
        public Builder burn(TerrainPalette palette, int thickness) {
            if (thickness < 1 || thickness > MAX_THICKNESS)
                throw new IllegalArgumentException("Expected a shell thickness in [1, " + MAX_THICKNESS + "], got " + thickness);
            this.palette = Objects.requireNonNull(palette, "palette");
            this.thickness = thickness;
            return this;
        }

        /** Real blocks placed in the middle at the commit (see {@link Fill}). */
        public Builder fill(Fill fill) { this.fill = Objects.requireNonNull(fill, "fill"); return this; }

        /**
         * Structure template placed at the commit, after the excavation: {@code id} is
         * {@code data/<namespace>/structure/<path>.nbt} as {@code namespace:path}. Its horizontal center goes on the
         * origin and its lowest layer on the origin level, turned by the instance yaw in 90° steps. Size limited by the
         * server config (default 32 × 32 × 32). Blocks with a block entity in the template get their data.
         */
        public Builder structure(String id) {
            Objects.requireNonNull(id, "id");
            if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Expected a structure ID namespace:path, got " + id);
            structure = id;
            return this;
        }

        /** Moves the structure {@code right}, {@code up} and {@code forward} blocks (instance yaw snapped to 90°). */
        public Builder structureOffset(float right, float up, float forward) {
            offset(right, up, forward);
            structureX = -right; structureY = up; structureZ = forward;
            return this;
        }

        /** Blocks written per tick at the commit, 1..{@value #MAX_BLOCKS_PER_TICK} (0, the default, uses the server's; the server caps it). */
        public Builder blocksPerTick(int blocks) {
            if (blocks < 0 || blocks > MAX_BLOCKS_PER_TICK)
                throw new IllegalArgumentException("Expected blocks per tick in [0, " + MAX_BLOCKS_PER_TICK + "], got " + blocks);
            blocksPerTick = blocks;
            return this;
        }

        /**
         * Explicitly allows replacing blocks with a block entity (chests, furnaces, blocks of other mods): their
         * contents are lost (saved in the undo snapshot). The server config {@code terrainBlockEntities} must allow it
         * too; otherwise they are left alone.
         */
        public Builder replacingBlockEntities() { blockEntities = true; return this; }

        /** Cooling time of the heat without a track, 1..{@value #MAX_COOLING} ticks (default 200). */
        public Builder cooling(int ticks) {
            if (ticks < 1 || ticks > MAX_COOLING) throw new IllegalArgumentException("Expected cooling in [1, " + MAX_COOLING + "], got " + ticks);
            cooling = ticks;
            return this;
        }

        /** Heat after the commit from a timeline track (values 0..1) instead of the default cooling. */
        public Builder heatTrack(String track) { heatTrack = name("track", track); return this; }

        /** Ember color of the heat, ARGB (alpha ignored; default 0xffff5a14). */
        public Builder glow(int argb) { glow = argb; return this; }

        /** Smoke amount over the hot area, 0..1 (default 0.5). */
        public Builder smoke(float amount) {
            if (!Float.isFinite(amount) || amount < 0 || amount > 1) throw new IllegalArgumentException("Expected smoke in [0, 1]");
            smoke = amount;
            return this;
        }

        /** Strength of the glowing cracks, 0..1 (default 0.8). */
        public Builder cracks(float amount) {
            if (!Float.isFinite(amount) || amount < 0 || amount > 1) throw new IllegalArgumentException("Expected cracks in [0, 1]");
            cracks = amount;
            return this;
        }

        /** No heat visuals after the commit (the edit is invisible apart from the blocks). */
        public Builder noHeat() { heat = false; return this; }

        /** Falling stand-in drawn by the client before the commit (see {@link StandIn}). */
        public Builder standIn(StandIn standIn) { this.standIn = Objects.requireNonNull(standIn, "standIn"); return this; }

        /** The edit; throws when the commit marker is missing. */
        public TerrainEdit build() {
            if (marker == null) throw new IllegalArgumentException("A terrain edit needs commitAt(marker)");
            if (standIn != null && standIn.kind() == StandIn.Kind.STRUCTURE && structure == null)
                throw new IllegalArgumentException("A structure stand-in needs structure(...)");
            return new TerrainEdit(this);
        }
    }
}
