package dev.calamidade_awakening.api.holo;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Geometry of the world panels of the Star Atlas (0.11.2), pure (JOML only) so {@code checkBudget} can check that
 * drawing and clicking use the same matrices. Adapted from the RespectCraft holographic menu (same author,
 * {@code com.respectcraft.ui.HoloGeometry}, stages 4.8.1 and 4.8.4-A); no RespectCraft class is used. Not public API.
 *
 * <p>Spaces: <b>layout</b> (units, x right, y down, z toward the viewer, origin at the top left of a fixed
 * {@code w x h} board), <b>panel local</b> (origin at the panel's top left, y down, the space its content is drawn in),
 * <b>eye</b> (camera at the origin looking down -z) and <b>world relative to the camera</b> (blocks).
 */
public final class HoloGeometry {
    private HoloGeometry() {}

    /** Distance of the reference plane so that it spans {@code screenH} units with the given vertical FOV. */
    public static float referenceDistance(float screenH, float fovDegrees) {
        return screenH / (2F * (float) Math.tan(Math.toRadians(fovDegrees) / 2.0));
    }

    /**
     * A panel in layout units: rectangle {@code x, y, w, h}, relative pivot (0..1), offset {@code dz} (positive = closer
     * to the viewer), rotation around the pivot and scale. The local space is the one its content is drawn in.
     */
    public static Matrix4f panel(Matrix4f dest, float x, float y, float w, float h, float pivotX, float pivotY, float dz,
                                 float yawDegrees, float pitchDegrees, float scaleX, float scaleY) {
        float px = pivotX * w, py = pivotY * h;
        return dest.translation(x + px, y + py, dz)
            .rotateY((float) Math.toRadians(yawDegrees))
            .rotateX((float) Math.toRadians(pitchDegrees))
            .scale(scaleX, scaleY, 1F)
            .translate(-px, -py, 0F);
    }

    /**
     * Layout to world relative to the camera (blocks). The anchor is the eye of the board: the layout point
     * {@code (centerX, centerY, eyeZ)} lands on {@code (relX, relY, relZ)} = anchor - camera, the front of the board
     * (-z of the layout) faces the anchor's yaw (Minecraft degrees: 0 = south/+z, 90 = west/-x), layout x goes to the
     * right of whoever looks along that yaw and layout y goes down. {@code blocksPerUnit} is the scale. Composed with
     * the real camera rotation it is the view of the board from the player's eyes.
     */
    public static Matrix4f layoutToWorld(Matrix4f dest, float relX, float relY, float relZ, float yawDegrees,
                                         float blocksPerUnit, float centerX, float centerY, float eyeZ) {
        return dest.translation(relX, relY, relZ)
            .rotateY((float) Math.toRadians(180.0 - yawDegrees))
            .scale(blocksPerUnit, -blocksPerUnit, blocksPerUnit)
            .translate(-centerX, -centerY, -eyeZ);
    }

    /**
     * Cursor ray (NDC) on the local plane z = 0 of a panel, from the inverse of {@code projection * view * model}.
     * {@code out} gets (local x, local y, t) with t in 0..1 from the near to the far plane. False when the ray is
     * parallel to the panel or crosses it outside the view volume.
     */
    public static boolean intersect(Matrix4f inversePvm, float ndcX, float ndcY, Vector3f out) {
        Vector4f a = new Vector4f(ndcX, ndcY, -1F, 1F);
        Vector4f b = new Vector4f(ndcX, ndcY, 1F, 1F);
        inversePvm.transform(a);
        inversePvm.transform(b);
        if (Math.abs(a.w) < 1e-9F || Math.abs(b.w) < 1e-9F) return false;
        float ax = a.x / a.w, ay = a.y / a.w, az = a.z / a.w;
        float bx = b.x / b.w, by = b.y / b.w, bz = b.z / b.w;
        float dz = bz - az;
        if (Math.abs(dz) < 1e-7F) return false;
        float t = -az / dz;
        if (t < 0F || t > 1F) return false;
        out.set(ax + (bx - ax) * t, ay + (by - ay) * t, t);
        return true;
    }

    /** {@link #intersect} accepting only points inside the rectangle {@code 0..w x 0..h}. */
    public static boolean hit(Matrix4f inversePvm, float ndcX, float ndcY, float w, float h, Vector3f out) {
        if (!intersect(inversePvm, ndcX, ndcY, out)) return false;
        float eps = 1e-3F;
        return out.x >= -eps && out.y >= -eps && out.x <= w + eps && out.y <= h + eps;
    }

    /**
     * A ray (origin and direction in the source space of {@code model}, such as the eye space) against the local plane
     * z = 0 of a panel, given the inverse of {@code model}. {@code out} gets (local x, local y, t) with t in the unit
     * of the direction (unit direction = distance). False when parallel or behind the origin. Not clipped.
     */
    public static boolean rayLocal(Matrix4f inverseModel, float ox, float oy, float oz, float dx, float dy, float dz,
                                   Vector3f out) {
        float lox = inverseModel.m00() * ox + inverseModel.m10() * oy + inverseModel.m20() * oz + inverseModel.m30();
        float loy = inverseModel.m01() * ox + inverseModel.m11() * oy + inverseModel.m21() * oz + inverseModel.m31();
        float loz = inverseModel.m02() * ox + inverseModel.m12() * oy + inverseModel.m22() * oz + inverseModel.m32();
        float ldx = inverseModel.m00() * dx + inverseModel.m10() * dy + inverseModel.m20() * dz;
        float ldy = inverseModel.m01() * dx + inverseModel.m11() * dy + inverseModel.m21() * dz;
        float ldz = inverseModel.m02() * dx + inverseModel.m12() * dy + inverseModel.m22() * dz;
        if (Math.abs(ldz) < 1e-9F) return false;
        float t = -loz / ldz;
        if (!(t > 0F)) return false;
        out.set(lox + ldx * t, loy + ldy * t, t);
        return true;
    }

    /** {@link #rayLocal} accepting only points inside the rectangle {@code 0..w x 0..h}. */
    public static boolean rayHit(Matrix4f inverseModel, float ox, float oy, float oz, float dx, float dy, float dz,
                                 float w, float h, Vector3f out) {
        if (!rayLocal(inverseModel, ox, oy, oz, dx, dy, dz, out)) return false;
        float eps = 1e-3F;
        return out.x >= -eps && out.y >= -eps && out.x <= w + eps && out.y <= h + eps;
    }

    /** Distance (local units) from a local point to the rectangle {@code 0..w x 0..h} of the plane z = 0. */
    public static float distanceToRect(float lx, float ly, float lz, float w, float h) {
        float dx = lx < 0F ? -lx : lx > w ? lx - w : 0F;
        float dy = ly < 0F ? -ly : ly > h ? ly - h : 0F;
        return (float) Math.sqrt(dx * dx + dy * dy + lz * lz);
    }
}
