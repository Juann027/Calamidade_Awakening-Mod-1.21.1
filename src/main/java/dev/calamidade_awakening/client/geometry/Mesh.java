package dev.calamidade_awakening.client.geometry;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Emissão de triângulos no espaço relativo à câmera. */
public final class Mesh {
    private final VertexConsumer out;
    private final Matrix4f matrix;
    private final Vec3 camera;
    private int remaining;

    public Mesh(VertexConsumer out, Matrix4f matrix, Vec3 camera, int vertexBudget) {
        this.out = out;
        this.matrix = matrix;
        this.camera = camera;
        this.remaining = vertexBudget;
    }

    public void triangle(Vec3 a, Vec3 b, Vec3 c, Color ca, Color cb, Color cc) {
        if (remaining < 3) return;
        vertex(a, ca);
        vertex(b, cb);
        vertex(c, cc);
        remaining -= 3;
    }

    public int remaining() { return remaining; }

    private void vertex(Vec3 p, Color color) {
        Vec3 local = p.subtract(camera);
        out.addVertex(matrix, (float) local.x, (float) local.y, (float) local.z)
            .setColor(color.r(), color.g(), color.b(), color.a());
    }

    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Color near, Color far) {
        if (remaining < 6) return;
        triangle(a, b, c, near, near, far);
        triangle(a, c, d, near, far, far);
    }

    public void ring(Vec3 center, Vec3 u, Vec3 v, double radius, double width,
                     int segments, Color color) {
        segments = Math.min(segments, remaining / 12);
        if (segments < 3 || radius < 0 || width <= 0) return;
        double inner = Math.max(0, radius - width / 2);
        double outer = radius + width / 2;
        for (int i = 0; i < segments; i++) {
            Vec3 a = radial(u, v, i * Math.TAU / segments);
            Vec3 b = radial(u, v, (i + 1) * Math.TAU / segments);
            quad(center.add(a.scale(inner)), center.add(b.scale(inner)),
                center.add(b.scale(radius)), center.add(a.scale(radius)), color.fade(0), color);
            quad(center.add(a.scale(radius)), center.add(b.scale(radius)),
                center.add(b.scale(outer)), center.add(a.scale(outer)), color, color.fade(0));
        }
    }

    public void billboard(Vec3 center, Vec3 right, Vec3 up, double size, Color color) {
        if (remaining < 12) return;
        Vec3 a = right.scale(size);
        Vec3 b = up.scale(size);
        Vec3[] corners = {center.add(a), center.add(b), center.subtract(a), center.subtract(b)};
        for (int i = 0; i < 4; i++) {
            triangle(center, corners[i], corners[(i + 1) % 4], color, color.fade(0), color.fade(0));
        }
    }

    public void ribbon(Vec3 from, Vec3 to, Vec3 side, double width, Color color) {
        Vec3 w = side.normalize().scale(width);
        quad(from.add(w), from.subtract(w), to.subtract(w), to.add(w), color, color.fade(0));
    }

    public void beam(Vec3 from, Vec3 to, double radius, int segments, Color color) {
        segments = Math.min(segments, remaining / 6);
        if (segments < 3) return;
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1e-9) return;
        Vec3 normal = axis.normalize();
        Vec3 reference = Math.abs(normal.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = normal.cross(reference).normalize();
        Vec3 v = normal.cross(u);
        for (int i = 0; i < segments; i++) {
            Vec3 a = radial(u, v, i * Math.TAU / segments).scale(radius);
            Vec3 b = radial(u, v, (i + 1) * Math.TAU / segments).scale(radius);
            quad(from.add(a), from.add(b), to.add(b), to.add(a), color.fade(0), color);
        }
    }

    /** Uma esfera paramétrica, com cor diferente nos polos e no equador. */
    public void sphere(Vec3 center, double radius, int latitudes, int longitudes,
                       Color pole, Color equator) {
        if (radius <= 0 || latitudes < 2 || longitudes < 3) return;
        int cells = remaining / 6;
        if (cells < 6) return;
        if ((long) latitudes * longitudes > cells) {
            double scale = Math.sqrt(cells / ((double) latitudes * longitudes));
            latitudes = Math.clamp((int) (latitudes * scale), 2, cells / 3);
            longitudes = Math.min(longitudes, cells / latitudes);
        }
        for (int i = 0; i < latitudes; i++) {
            double a = i * Math.PI / latitudes;
            double b = (i + 1) * Math.PI / latitudes;
            Color c1 = pole.mix(equator, Math.sin(a));
            Color c2 = pole.mix(equator, Math.sin(b));
            for (int j = 0; j < longitudes; j++) {
                double u = j * Math.TAU / longitudes;
                double v = (j + 1) * Math.TAU / longitudes;
                quad(center.add(spherical(a, u).scale(radius)), center.add(spherical(a, v).scale(radius)),
                    center.add(spherical(b, v).scale(radius)), center.add(spherical(b, u).scale(radius)), c1, c2);
            }
        }
    }

    private static Vec3 spherical(double latitude, double longitude) {
        return new Vec3(Math.sin(latitude) * Math.cos(longitude), Math.cos(latitude),
            Math.sin(latitude) * Math.sin(longitude));
    }

    private static Vec3 radial(Vec3 u, Vec3 v, double angle) {
        return u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
    }
}
