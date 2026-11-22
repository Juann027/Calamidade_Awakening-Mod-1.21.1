package dev.calamidade_awakening;

import dev.calamidade_awakening.api.holo.HoloGeometry;
import dev.calamidade_awakening.api.holo.HoloInputArbiter;
import dev.calamidade_awakening.api.holo.HoloPlacement;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Star Atlas in the world (0.11.2): board-to-world matrix, the same local point by drawing and by the aim ray, wall
 * placement and the input owner. Adapted from the RespectCraft self tests {@code holoWorld} and {@code holoInput} (same
 * author). Pure; run by FrameBudgetCheck inside checkBudget.
 */
final class HoloCheck {
    private HoloCheck() {}

    static void run() {
        world();
        ray();
        placement();
        input();
    }

    private static final float CX = 320F, CY = 180F, EYE = 257F, S = 2F / 257F;

    private static void world() {
        Vector3f out = new Vector3f();
        for (float yaw : new float[] {0F, 90F, -135F, 200F}) {
            Matrix4f m = HoloGeometry.layoutToWorld(new Matrix4f(), 10F, 64F, -3F, yaw, S, CX, CY, EYE);
            m.transformPosition(out.set(CX, CY, EYE));
            require(out.distance(10F, 64F, -3F) < 1e-3F, "board eye lands on the anchor (yaw " + yaw + ")");
            m.transformPosition(out.set(CX, CY, 0F));
            float fx = (float) HoloPlacement.forwardX(yaw), fz = (float) HoloPlacement.forwardZ(yaw);
            require(out.distance(10F + 2F * fx, 64F, -3F + 2F * fz) < 1e-3F, "board front follows the yaw " + yaw + ": " + out);
            m.transformPosition(out.set(CX + 100F, CY + 50F, 0F));
            float rx = (float) HoloPlacement.rightX(yaw), rz = (float) HoloPlacement.rightZ(yaw);
            require(out.distance(10F + 2F * fx + 100F * S * rx, 64F - 50F * S, -3F + 2F * fz + 100F * S * rz) < 1e-3F,
                "layout right and down (yaw " + yaw + ")");
        }
        require(Math.abs(HoloGeometry.distanceToRect(10F, 20F, -3F, 100F, 50F) - 3F) < 1e-4F
            && Math.abs(HoloGeometry.distanceToRect(-3F, 54F, 0F, 100F, 50F) - 5F) < 1e-4F, "distance to the panel rectangle");
    }

    /** The same local point by drawing and by the ray, with the camera at several places around a fixed anchor. */
    private static void ray() {
        Vector3f out = new Vector3f();
        Matrix4f panel = HoloGeometry.panel(new Matrix4f(), 245F, 112F, 150F, 104F, .5F, .5F, 14F, -8F, 0F, 1F, 1F);
        float ax = 100.5F, ay = 70.62F, az = -40.25F;
        for (float[] cam : new float[][] {{ax, ay, az}, {ax + 1.3F, ay - .4F, az + .6F}, {ax - 2.5F, ay + .3F, az - 3.5F}}) {
            Matrix4f model = HoloGeometry.layoutToWorld(new Matrix4f(), ax - cam[0], ay - cam[1], az - cam[2], 37F, S, CX, CY, EYE)
                .mul(panel);
            Vector3f target = model.transformPosition(new Vector3f(60F, 80F, 0F));
            Vector3f dir = new Vector3f(target).normalize();
            boolean ok = HoloGeometry.rayHit(new Matrix4f(model).invert(), 0F, 0F, 0F, dir.x, dir.y, dir.z, 150F, 104F, out);
            require(ok && Math.abs(out.x - 60F) < .05F && Math.abs(out.y - 80F) < .05F && Math.abs(out.z - target.length()) < 1e-3F,
                "aim ray -> local point of the anchored panel: " + out);
            require(!HoloGeometry.rayHit(new Matrix4f(model).invert(), 0F, 0F, 0F, -dir.x, -dir.y, -dir.z, 150F, 104F, out),
                "panel behind the camera refused");
            require(HoloGeometry.rayLocal(new Matrix4f(model).invert(), 0F, 0F, 0F, dir.x, dir.y, dir.z, out),
                "drag on the panel plane without clipping");
        }
    }

    private static void placement() {
        List<HoloPlacement.Sample> samples = new ArrayList<>();
        for (int i = -2; i <= 2; i++) {
            double a = Math.toRadians(i * 31.0);
            int kind = i == 0 ? HoloPlacement.ESSENTIAL : Math.abs(i) == 1 ? HoloPlacement.IMPORTANT : HoloPlacement.OPTIONAL;
            for (float up : new float[] {-.25F, 0F, .25F})
                samples.add(new HoloPlacement.Sample((float) Math.sin(a) * 1.2F, up, (float) Math.cos(a) * 1.2F, kind));
        }
        double ex = .5, ey = 65.62, ez = .5;
        var open = HoloPlacement.choose(ex, ey, ez, 0F, 2F, .7F, samples, (x0, y0, z0, x1, y1, z1) -> true);
        require(open.fits() && open.yaw() == 0F && open.distance() == 2F, "open field uses the preferred distance: " + open);
        var near = HoloPlacement.choose(ex, ey, ez, 0F, 2F, .7F, samples, (x0, y0, z0, x1, y1, z1) -> z1 < 2.0);
        require(near.fits() && near.yaw() == 0F && near.distance() < 2F && ez + near.distance() * 1.2F < 2.0,
            "a wall ahead brings the board closer: " + near);
        var turned = HoloPlacement.choose(ex, ey, ez, 0F, 2F, .7F, samples,
            (x0, y0, z0, x1, y1, z1) -> !(z1 >= 1.2 && Math.abs(x1 - .5) < .6));
        require(turned.fits() && Math.abs(turned.yaw()) == 50F && turned.distance() == 2F, "a pillar ahead turns the board: " + turned);
        var boxed = HoloPlacement.choose(ex, ey, ez, 0F, 2F, .7F, samples,
            (x0, y0, z0, x1, y1, z1) -> Math.abs(x1 - x0) < .3 && Math.abs(z1 - z0) < .3);
        require(!boxed.fits() && boxed.distance() <= .7F + 1e-4F, "no room: smallest distance and a warning: " + boxed);
    }

    private static void input() {
        require(HoloInputArbiter.hologramWins(true, 2.0, Double.POSITIVE_INFINITY, 6.0), "panel with nothing behind wins");
        require(HoloInputArbiter.hologramWins(true, 2.0, 3.5, 6.0), "panel in front of a block wins");
        require(!HoloInputArbiter.hologramWins(true, 2.0, 1.5, 6.0), "block or mob in front of the panel: world");
        require(!HoloInputArbiter.hologramWins(true, 7.0, Double.POSITIVE_INFINITY, 6.0), "out of reach: world");
        require(!HoloInputArbiter.hologramWins(false, -1, Double.POSITIVE_INFINITY, 6.0), "no panel: world");
        var a = new HoloInputArbiter();
        require(a.press(0, true) == HoloInputArbiter.Owner.HOLO && a.anyHolo(), "press on the panel");
        require(a.owner(0) == HoloInputArbiter.Owner.HOLO, "stays the panel's while held, wherever the aim goes");
        require(a.release(0) == HoloInputArbiter.Owner.HOLO && a.owner(0) == HoloInputArbiter.Owner.NONE, "release to the same owner");
        require(a.press(1, false) == HoloInputArbiter.Owner.WORLD && !a.anyHolo(), "press in the world stays the world's");
        require(a.press(HoloInputArbiter.keySource(HoloInputArbiter.ATTACK), true) == HoloInputArbiter.Owner.HOLO
            && a.owner(1) == HoloInputArbiter.Owner.WORLD, "sources are independent");
        require(a.press(99, true) == HoloInputArbiter.Owner.WORLD && a.release(99) == HoloInputArbiter.Owner.NONE, "invalid source");
        a.clear();
        require(!a.anyHolo() && a.owner(1) == HoloInputArbiter.Owner.NONE, "logout clears");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
