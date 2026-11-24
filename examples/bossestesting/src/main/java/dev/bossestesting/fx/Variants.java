package dev.bossestesting.fx;

import dev.bossestesting.BossesTesting;
import dev.bossestesting.BtConfig;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A destructive effect registered four times with the same look: without terrain (destruction OFF, mobGriefing off)
 * and with the LOW, NORMAL and EPIC edits. The client registers its layers for every id ({@link #all()}).
 * Damage never lives here: the actions hit on the same tick, so blocks and damage are independent.
 */
public final class Variants {
    public final ResourceLocation base, low, normal, epic;

    public Variants(String name) {
        base = BossesTesting.id(name);
        low = BossesTesting.id(name + "_low");
        normal = BossesTesting.id(name + "_normal");
        epic = BossesTesting.id(name + "_epic");
    }

    public List<ResourceLocation> all() { return List.of(base, low, normal, epic); }

    public boolean is(ResourceLocation id) { return all().contains(id); }

    /**
     * Registers the four ids: `common` adds definition, timeline, beams, lights, sky and atmosphere to each; the
     * edits of each level are added after it (LOW, NORMAL, EPIC; null or empty = no terrain at that level).
     */
    public void register(Consumer<ResourceLocation> common, List<TerrainEdit> lowEdits, List<TerrainEdit> normalEdits, List<TerrainEdit> epicEdits) {
        common.accept(base);
        common.accept(low);
        common.accept(normal);
        common.accept(epic);
        edits(low, lowEdits);
        edits(normal, normalEdits);
        edits(epic, epicEdits);
    }

    private static void edits(ResourceLocation id, List<TerrainEdit> edits) {
        if (edits != null && !edits.isEmpty()) Effects.terrain(id, edits.toArray(TerrainEdit[]::new));
    }

    /** The id for the current destruction setting and game rules of `level`. */
    public ResourceLocation pick(ServerLevel level) {
        if (!Destruction.allowed(level)) return base;
        return switch (BtConfig.destruction()) {
            case OFF -> base;
            case LOW -> low;
            case NORMAL -> normal;
            case EPIC -> epic;
        };
    }

    /**
     * Starts the variant at a fixed placement (fixed positions plan on a worker thread before the marker). The boss
     * is the source, so claims and the engine's mobGriefing rule see the right entity; when its quota refuses the
     * start, the visual-only id is tried with a script source so the attack never loses its visuals.
     */
    public Optional<Effects.Handle> start(ServerLevel level, Entity boss, Placement placement) {
        ResourceLocation id = pick(level);
        Optional<Effects.Handle> h = Effects.start(id, level, placement.withSource(EffectSource.boss(boss)));
        if (h.isEmpty()) h = Effects.start(base, level, placement.withSource(EffectSource.script()));
        return h;
    }

    /** Destruction gate of the addon (on top of the engine's own rules). */
    public static final class Destruction {
        private Destruction() {}

        public static boolean allowed(ServerLevel level) {
            return BtConfig.destruction() != BtConfig.Destruction.OFF && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        }
    }

    /** Collects edits fluently. */
    public static List<TerrainEdit> edits(TerrainEdit... edits) { return new ArrayList<>(List.of(edits)); }
}
