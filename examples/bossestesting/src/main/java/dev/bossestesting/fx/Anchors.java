package dev.bossestesting.fx;

import dev.bossestesting.registry.ModEntities;
import dev.calamidade_awakening.api.v1.RigAnchors;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Rig anchors of both forms. The static spec (feet frame, body yaw) is what the server uses for damage and missile
 * spawns; clients replace it every frame with the real GeckoLib bone pose ({@code AnchorLayer} → AnchorCapture).
 * Positions come from the model pivots (px / 16), with GeckoLib's mirrored x (the "_r" bones sit on the right).
 */
public final class Anchors {
    private Anchors() {}

    /** Bone + offset (GeckoLib model space, pixels) of an anchor, used by the client capture. */
    public record Bone(String anchor, String bone, float x, float y, float z) {}

    public static final List<Bone> PHASE1 = List.of(
        new Bone("reactor", "torso", 0, 9, -6.5f), new Bone("cabin", "jar", 0, 6, 0), new Bone("head", "head", 0, 4, -2),
        new Bone("hand_r", "hand_r", 0, -4, 0), new Bone("hand_l", "hand_l", 0, -4, 0),
        new Bone("foot_r", "foot_r", 0, -5, 0), new Bone("foot_l", "foot_l", 0, -5, 0));

    public static final List<Bone> PHASE2 = List.of(
        new Bone("reactor", "reactor_heart", 0, 0, -3), new Bone("nucleus", "heart_nucleus", 0, 0, 0), new Bone("head", "head", 0, 4, -2),
        new Bone("hand_r", "hand_r", 0, -4, 0), new Bone("hand_l", "hand_l", 0, -4, 0),
        new Bone("foot_r", "foot_r", 0, -5, 0), new Bone("foot_l", "foot_l", 0, -5, 0),
        new Bone("rack_r", "missile_rack_r", 0, 3, 0), new Bone("rack_l", "missile_rack_l", 0, 3, 0),
        new Bone("pod_r", "missile_pod_r", 0, 2, 0), new Bone("pod_l", "missile_pod_l", 0, 2, 0), new Bone("wires_l", "wires_l", 0, 0, 0));

    public static final List<Bone> MISSILE = List.of(new Bone("exhaust", "exhaust", 0, 0, 1));

    /** Static anchors, registered at common setup (the entity types exist from then on). */
    public static void register() {}

    public static void define() {
        common(ModEntities.JAR_GOLEM.get());
        common(ModEntities.FINALE.get());
        EntityType<?> p2 = ModEntities.FINALE.get();
        RigAnchors.define(p2, "rack_r", RigAnchors.Spec.body(1.44, 4.12, 0));
        RigAnchors.define(p2, "rack_l", RigAnchors.Spec.body(-1.44, 4.12, 0));
        RigAnchors.define(p2, "pod_r", RigAnchors.Spec.body(1.44, 3.75, 0));
        RigAnchors.define(p2, "pod_l", RigAnchors.Spec.body(-1.44, 3.75, 0));
        RigAnchors.define(p2, "wires_l", RigAnchors.Spec.body(-1.69, 1.94, .25));
        RigAnchors.define(p2, "nucleus", RigAnchors.Spec.body(0, 2.79, .48));
        RigAnchors.define(ModEntities.MISSILE.get(), "exhaust", RigAnchors.Spec.body(0, .3, -.55));
    }

    private static void common(EntityType<?> type) {
        RigAnchors.define(type, "reactor", RigAnchors.Spec.body(0, 2.81, .45));
        RigAnchors.define(type, "cabin", RigAnchors.Spec.body(0, 3.6, .1));
        RigAnchors.define(type, "head", RigAnchors.Spec.body(0, 3.9, .2));
        RigAnchors.define(type, "hand_r", RigAnchors.Spec.body(1.47, 1.15, .1));
        RigAnchors.define(type, "hand_l", RigAnchors.Spec.body(-1.47, 1.15, .1));
        RigAnchors.define(type, "foot_r", RigAnchors.Spec.body(.66, .1, 0));
        RigAnchors.define(type, "foot_l", RigAnchors.Spec.body(-.66, .1, 0));
    }
}
