package dev.bossestesting.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Procedural jet flames in the bone frame (additive, unlit): crossed tapered quads with a white-hot core, a colored
 * body and a flickering tip; plus shock diamonds along the jet. Used by the thrusters of the landing and the missile
 * exhaust. Drawn with {@code RenderType.lightning()} (position + color, additive).
 */
public final class Flames {
    private Flames() {}

    /**
     * Draws a jet from the origin along `dir` (unit, bone frame) of `length` blocks and `width` at the nozzle.
     * Colors ARGB; `seed` changes the flicker.
     */
    public static void jet(PoseStack pose, MultiBufferSource buffers, float dx, float dy, float dz, float length, float width,
                           int core, int body, float time, int seed) {
        if (length <= .01f) return;
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        // Two perpendicular planes containing the jet axis.
        float[][] sides = perpendiculars(dx, dy, dz);
        float flick = (float) (.85 + .15 * Math.sin(time * 1.9 + seed) * Math.sin(time * 3.7 + seed * 2));
        float len = length * flick;
        for (float[] s : sides) {
            quad(v, m, s, dx, dy, dz, 0, width, len, width * .15f, body, body & 0xffffff);
            quad(v, m, s, dx, dy, dz, 0, width * .45f, len * .55f, width * .05f, core, core & 0x00ffffff | 0x40000000);
        }
        // Shock diamonds: small bright lozenges along the core.
        for (int i = 1; i <= 3; i++) {
            float t = len * (.16f * i + .02f * (float) Math.sin(time * 2 + i));
            float w = width * (.32f - .06f * i);
            for (float[] s : sides) diamond(v, m, s, dx, dy, dz, t, w, core);
        }
    }

    private static float[][] perpendiculars(float dx, float dy, float dz) {
        float ax = Math.abs(dy) < .9f ? 0 : 1, ay = Math.abs(dy) < .9f ? 1 : 0;
        float px = dy * 0 - dz * ay, py = dz * ax - dx * 0, pz = dx * ay - dy * ax;
        float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        px /= pl; py /= pl; pz /= pl;
        float qx = dy * pz - dz * py, qy = dz * px - dx * pz, qz = dx * py - dy * px;
        return new float[][]{{px, py, pz}, {qx, qy, qz}};
    }

    /** Tapered band from `a` (width wa) to `b` (width wb) along the axis, colors at both ends. */
    private static void quad(VertexConsumer v, Matrix4f m, float[] s, float dx, float dy, float dz, float a, float wa, float b, float wb,
                             int ca, int cb) {
        v.addVertex(m, dx * a - s[0] * wa, dy * a - s[1] * wa, dz * a - s[2] * wa).setColor(ca);
        v.addVertex(m, dx * a + s[0] * wa, dy * a + s[1] * wa, dz * a + s[2] * wa).setColor(ca);
        v.addVertex(m, dx * b + s[0] * wb, dy * b + s[1] * wb, dz * b + s[2] * wb).setColor(cb);
        v.addVertex(m, dx * b - s[0] * wb, dy * b - s[1] * wb, dz * b - s[2] * wb).setColor(cb);
    }

    private static void diamond(VertexConsumer v, Matrix4f m, float[] s, float dx, float dy, float dz, float t, float w, int c) {
        int edge = c & 0x00ffffff;
        v.addVertex(m, dx * (t - w), dy * (t - w), dz * (t - w)).setColor(edge);
        v.addVertex(m, dx * t + s[0] * w * .5f, dy * t + s[1] * w * .5f, dz * t + s[2] * w * .5f).setColor(c);
        v.addVertex(m, dx * (t + w), dy * (t + w), dz * (t + w)).setColor(edge);
        v.addVertex(m, dx * t - s[0] * w * .5f, dy * t - s[1] * w * .5f, dz * t - s[2] * w * .5f).setColor(c);
    }
}
