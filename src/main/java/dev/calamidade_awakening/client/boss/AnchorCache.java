package dev.calamidade_awakening.client.boss;

import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.AnchorMath;
import dev.calamidade_awakening.api.v1.RigAnchors;
import dev.calamidade_awakening.api.v1.client.AnchorCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rig anchor poses of the frame (0.13.0, client, render thread): captures reported by addon renderers, providers and
 * the fallback chain. Captures are kept only for anchors an active effect asked for in the last {@link #WANT_FRAMES}
 * frames; everything is dropped when the player leaves the world.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID, value = Dist.CLIENT)
public final class AnchorCache {
    /** Frames an anchor stays "wanted" after its last use. */
    static final int WANT_FRAMES = 120;
    /** Frames an unused capture is kept. */
    static final int KEEP_FRAMES = 600;

    private record Key(int entity, String name) {}
    private record Captured(AnchorMath.Pose pose, long frame, double time, UUID uuid, double ex, double ey, double ez, float yaw) {}

    private static final Map<Key, Captured> CAPTURES = new HashMap<>();
    private static final Map<Key, Long> WANTED = new HashMap<>();
    private static final Map<EntityType<?>, AnchorCapture.Provider> PROVIDERS = new ConcurrentHashMap<>();
    private static long frame;
    /** Diagnostics (F7): poses resolved this frame from a capture, a carried capture, a provider or the static chain. */
    private static int captured, carried, provided, fixed;
    static int lastCaptured, lastCarried, lastProvided, lastFixed;

    private AnchorCache() {}

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        frame++;
        lastCaptured = captured; lastCarried = carried; lastProvided = provided; lastFixed = fixed;
        captured = carried = provided = fixed = 0;
        if ((frame & 127) == 0) {
            CAPTURES.values().removeIf(c -> frame - c.frame > KEEP_FRAMES);
            WANTED.values().removeIf(f -> frame - f > WANT_FRAMES);
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CAPTURES.clear();
        WANTED.clear();
    }

    public static boolean wanted(Entity entity, String name) {
        if (entity == null || name == null) return false;
        Long last = WANTED.get(new Key(entity.getId(), name));
        return last != null && frame - last <= WANT_FRAMES;
    }

    public static void provider(EntityType<?> type, AnchorCapture.Provider provider) { PROVIDERS.put(type, provider); }

    public static void capture(Entity entity, String name, Matrix4f m, Vector3f forward, Vector3f up) {
        if (!wanted(entity, name)) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float[] values = new float[16];
        m.get(values);
        AnchorMath.Pose pose = AnchorMath.fromMatrix(values, cam.x, cam.y, cam.z, forward.x, forward.y, forward.z, up.x, up.y, up.z);
        if (pose == null) return;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 at = entity.getPosition(partial);
        CAPTURES.put(new Key(entity.getId(), name), new Captured(pose, frame, time(entity, partial), entity.getUUID(),
            at.x, at.y, at.z, bodyYaw(entity, partial)));
    }

    /** Pose of an anchor this frame; never null. Marks the anchor as wanted, so the next renders capture it. */
    public static RigAnchors.Pose resolve(Entity entity, String name, float partial) {
        Key key = new Key(entity.getId(), name);
        WANTED.put(key, frame);
        Captured c = CAPTURES.get(key);
        if (c != null && !c.uuid.equals(entity.getUUID())) c = null;
        if (c != null && c.frame == frame) {
            captured++;
            return RigAnchors.Pose.of(c.pose);
        }
        AnchorCapture.Provider provider = PROVIDERS.get(entity.getType());
        if (provider != null) {
            try {
                RigAnchors.Pose pose = provider.resolve(entity, name, partial);
                if (pose != null) {
                    provided++;
                    return pose;
                }
            } catch (RuntimeException failure) {
                PROVIDERS.remove(entity.getType());
                com.mojang.logging.LogUtils.getLogger().error("Rig anchor provider of {} failed and was removed", entity.getType(), failure);
            }
        }
        if (c != null && AnchorMath.carriable(c.time, time(entity, partial))) {
            Vec3 at = entity.getPosition(partial);
            carried++;
            return RigAnchors.Pose.of(AnchorMath.carry(c.pose, c.ex, c.ey, c.ez, c.yaw, at.x, at.y, at.z, bodyYaw(entity, partial)));
        }
        fixed++;
        return RigAnchors.approximate(entity, name, partial);
    }

    private static double time(Entity entity, float partial) { return entity.level().getGameTime() + partial; }

    private static float bodyYaw(Entity entity, float partial) {
        return entity instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : entity.getViewYRot(partial);
    }

    /** Anchors resolved last frame: captured, carried, provider, static (tests and F7). */
    public static int[] stats() { return new int[] {lastCaptured, lastCarried, lastProvided, lastFixed}; }

    /** True when no anchor was resolved last frame. */
    public static boolean idle() { return lastCaptured + lastCarried + lastProvided + lastFixed == 0; }

    /** F7 line: anchors resolved last frame by origin. */
    public static String line() {
        return "Anchors: " + lastCaptured + " captured, " + lastCarried + " carried, " + lastProvided + " provider, " + lastFixed + " static";
    }
}
