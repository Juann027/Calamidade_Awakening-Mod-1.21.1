package dev.calamidade_awakening.client.holo;

import dev.calamidade_awakening.api.holo.HoloGeometry;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One Atlas panel in 3D: rest rectangle (layout units), animated state of the frame (depth, turn, scale, opacity) and
 * the matrices frozen for the frame. Drawing, aim ray, clicks and the constellation stars all use these same matrices.
 * Adapted from the RespectCraft holographic menu ({@code HoloPanel}, stages 4.8.1 to 4.8.4-B, same author).
 */
final class HoloPanel {
    final String id;
    final HoloSurface surface = new HoloSurface();
    /** Rectangle in layout units. */
    float x, y, w, h;
    /** Animated state: offset toward the viewer, turn (degrees), scale and opacity of glass and content. */
    float dz, yaw, pitch, roll, scale = 1F, alpha, contentAlpha;
    /** Accent colour of the border. */
    int accent;
    boolean visible, interactive = true, hot;
    /** Aim on this panel this frame (local units). */
    float aimX = -9999F, aimY = -9999F;

    final Matrix4f model = new Matrix4f(), modelView = new Matrix4f(), pvm = new Matrix4f(), inverse = new Matrix4f(),
        inverseModelView = new Matrix4f();
    private final Vector3f scratch = new Vector3f();
    /** Out of the view in the last frame (its surface is not redrawn). */
    boolean culled;

    private final List<HoloZone> zones = new ArrayList<>();
    private int zoneCount;

    HoloPanel(String id) { this.id = id; }

    HoloPanel rect(float x, float y, float w, float h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        return this;
    }

    /** Freezes the frame matrices from view*layout and projection*view*layout. */
    void freeze(Matrix4f viewLayout, Matrix4f projViewLayout) {
        HoloGeometry.panel(model, x, y, w, h, .5F, .5F, dz, yaw, pitch, roll, scale, scale);
        viewLayout.mul(model, modelView);
        projViewLayout.mul(model, pvm);
        pvm.invert(inverse);
        modelView.invert(inverseModelView);
    }

    /** Aim ray (eye space, unit direction) inside the panel, front face only; local point and distance in {@code out}. */
    boolean rayHit(float ox, float oy, float oz, float dx, float dy, float dz, Vector3f out) {
        if (Math.abs(scale) < .05F || alpha < .05F) return false;
        if (inverseModelView.transformPosition(scratch.set(ox, oy, oz)).z <= 0F) return false;
        return HoloGeometry.rayHit(inverseModelView, ox, oy, oz, dx, dy, dz, w, h, out);
    }

    /** Aim ray on the panel plane without clipping (captured drag leaving the panel). */
    boolean rayPlane(float ox, float oy, float oz, float dx, float dy, float dz, Vector3f out) {
        return HoloGeometry.rayLocal(inverseModelView, ox, oy, oz, dx, dy, dz, out);
    }

    /** Cursor (NDC) inside the panel (cursor mode). */
    boolean hit(float ndcX, float ndcY, Vector3f out) {
        if (Math.abs(scale) < .05F || alpha < .05F) return false;
        return HoloGeometry.hit(inverse, ndcX, ndcY, w, h, out);
    }

    /** Eye position in the panel's local space. */
    Vector3f eyeLocal(Vector3f out) { return inverseModelView.transformPosition(out.set(0F, 0F, 0F)); }

    /** Every corner outside the same side of the view volume. */
    boolean offscreen() {
        int left = 0, right = 0, below = 0, above = 0, behind = 0;
        for (int i = 0; i < 4; i++) {
            float lx = (i & 1) == 0 ? 0F : w, ly = (i & 2) == 0 ? 0F : h;
            float cx = pvm.m00() * lx + pvm.m10() * ly + pvm.m30();
            float cy = pvm.m01() * lx + pvm.m11() * ly + pvm.m31();
            float cw = pvm.m03() * lx + pvm.m13() * ly + pvm.m33();
            if (cw <= 0F) behind++;
            if (cx < -cw) left++;
            if (cx > cw) right++;
            if (cy < -cw) below++;
            if (cy > cw) above++;
        }
        return behind == 4 || left == 4 || right == 4 || below == 4 || above == 4;
    }

    void resetZones() { zoneCount = 0; }

    HoloZone zone(float zx, float zy, float zw, float zh) {
        HoloZone zone;
        if (zoneCount < zones.size()) zone = zones.get(zoneCount);
        else zones.add(zone = new HoloZone());
        zoneCount++;
        return zone.set(zx, zy, zw, zh);
    }

    /** Topmost region with a click or drag at the local point, or null. */
    HoloZone zoneAt(float lx, float ly) {
        for (int i = zoneCount - 1; i >= 0; i--) {
            HoloZone z = zones.get(i);
            if (z.contains(lx, ly) && (z.click != null || z.drag != null)) return z;
        }
        return null;
    }

    /** Topmost region with its own wheel at the local point, or null. */
    HoloZone scrollZoneAt(float lx, float ly) {
        for (int i = zoneCount - 1; i >= 0; i--) {
            HoloZone z = zones.get(i);
            if (z.contains(lx, ly) && z.scroll != null) return z;
        }
        return null;
    }

    void close() {
        surface.close();
        zoneCount = 0;
        zones.clear();
    }
}
