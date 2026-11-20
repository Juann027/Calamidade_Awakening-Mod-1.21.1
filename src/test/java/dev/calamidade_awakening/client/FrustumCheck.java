package dev.calamidade_awakening.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class FrustumCheck {
    public static void main(String[] args) {
        var projection = new Matrix4f().setPerspective((float) Math.toRadians(90), 1, .1f, 128);
        Frustum view = new Frustum(new Matrix4f(), projection);
        Vec3 camera = new Vec3(400, 65, -300);
        view.prepare(camera.x, camera.y, camera.z);
        for (int sign : new int[]{-1, 1}) {
            check(view, camera.add(sign * 18, 0, -10), false);
            check(view, camera.add(0, sign * 18, -10), false);
            check(view, camera.add(sign * 12, 0, -10), true);
            check(view, camera.add(0, sign * 12, -10), true);
        }
        check(view, camera, true);
        check(view, camera.add(0, 0, 20), false);
    }

    private static void check(Frustum view, Vec3 center, boolean expected) {
        if (StageScene.isVisible(view, center, 1) != expected)
            throw new AssertionError("Unexpected visibility at " + center);
    }
}
