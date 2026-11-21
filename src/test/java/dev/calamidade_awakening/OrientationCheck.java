package dev.calamidade_awakening;

import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Point;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

/** 0.10.0: orientation of instances (Basis, Point.facing, Placement, Context.facing). Pure transforms. */
final class OrientationCheck {
    private static final double EPS = 1e-9;

    static void run() {
        identity();
        knownDirections();
        matchesMinecraft();
        orthonormal();
        roundTrip();
        wrapAndTowards();
        points();
        placements();
        context();
        System.out.println("OrientationCheck PASS");
    }

    private static void identity() {
        require(Basis.of(0, 0) == Basis.IDENTITY && Basis.IDENTITY.identity(), "identity singleton");
        require(Basis.of(-0f, -0f) == Basis.IDENTITY && Basis.of(360, 0) == Basis.IDENTITY, "identity after wrap");
        Basis b = Basis.IDENTITY;
        require(b.worldX(1.5, -2, 3) == 1.5 && b.worldY(1.5, -2, 3) == -2 && b.worldZ(1.5, -2, 3) == 3,
            "identity keeps the world axes (0.9 behavior)");
        require(!Basis.of(1, 0).identity() && !Basis.of(0, 1).identity(), "non-identity");
    }

    private static void knownDirections() {
        // Yaw 90 faces west (-X): forward (-1,0,0), left = south (+Z).
        Basis west = Basis.of(90, 0);
        near(west.fx, west.fy, west.fz, -1, 0, 0, "yaw 90 forward");
        near(west.lx, west.ly, west.lz, 0, 0, 1, "yaw 90 left");
        Basis east = Basis.of(-90, 0);
        near(east.fx, east.fy, east.fz, 1, 0, 0, "yaw -90 forward");
        Basis north = Basis.of(180, 0);
        near(north.fx, north.fy, north.fz, 0, 0, -1, "yaw 180 forward");
        near(north.lx, north.ly, north.lz, -1, 0, 0, "yaw 180 left");
        // Facing south (yaw 0), ^1 ^ ^ (left) is east (+X), as in Minecraft's local coordinates.
        near(Basis.IDENTITY.lx, Basis.IDENTITY.ly, Basis.IDENTITY.lz, 1, 0, 0, "left at yaw 0 is east");
        Basis down = Basis.of(0, 90);
        near(down.fx, down.fy, down.fz, 0, -1, 0, "pitch 90 looks down");
        near(down.ux, down.uy, down.uz, 0, 0, 1, "pitch 90: up points where the yaw faced");
        Basis up = Basis.of(0, -90);
        near(up.fx, up.fy, up.fz, 0, 1, 0, "pitch -90 looks up");
        near(up.ux, up.uy, up.uz, 0, 0, -1, "pitch -90: up points backwards");
        // A point 2 forward, 1 up and 3 to the right of an instance facing west: world (-2, 1, -3).
        near(west.worldX(-3, 1, 2), west.worldY(-3, 1, 2), west.worldZ(-3, 1, 2), -2, 1, -3, "local to world, facing west");
    }

    private static void matchesMinecraft() {
        for (int yaw = -180; yaw < 180; yaw += 15) for (int pitch = -90; pitch <= 90; pitch += 15) {
            Basis b = Basis.of(yaw, pitch);
            Vec3 mc = Vec3.directionFromRotation(pitch, yaw);
            // Minecraft uses a sine table; 1e-3 is well above its error.
            require(Math.abs(b.fx - mc.x) < 1e-3 && Math.abs(b.fy - mc.y) < 1e-3 && Math.abs(b.fz - mc.z) < 1e-3,
                "forward equals Vec3.directionFromRotation at yaw " + yaw + " pitch " + pitch);
        }
    }

    private static void orthonormal() {
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            Basis b = Basis.of(random.nextFloat() * 720 - 360, random.nextFloat() * 180 - 90);
            require(Math.abs(len(b.lx, b.ly, b.lz) - 1) < 1e-9 && Math.abs(len(b.ux, b.uy, b.uz) - 1) < 1e-9
                && Math.abs(len(b.fx, b.fy, b.fz) - 1) < 1e-9, "unit axes");
            require(Math.abs(dot(b.lx, b.ly, b.lz, b.ux, b.uy, b.uz)) < 1e-9
                && Math.abs(dot(b.lx, b.ly, b.lz, b.fx, b.fy, b.fz)) < 1e-9
                && Math.abs(dot(b.ux, b.uy, b.uz, b.fx, b.fy, b.fz)) < 1e-9, "orthogonal axes");
            require(Math.abs(b.ly) < EPS, "left stays horizontal");
            // up = forward x left (the same handedness as the world: east x up = south).
            double cx = b.fy * b.lz - b.fz * b.ly, cy = b.fz * b.lx - b.fx * b.lz, cz = b.fx * b.ly - b.fy * b.lx;
            near(cx, cy, cz, b.ux, b.uy, b.uz, "up = forward x left");
            require(b.uy >= -EPS, "up never points below the horizon");
        }
    }

    private static void roundTrip() {
        Random random = new Random(11);
        for (int i = 0; i < 2000; i++) {
            Basis b = Basis.of(random.nextFloat() * 360 - 180, random.nextFloat() * 180 - 90);
            double x = random.nextGaussian() * 20, y = random.nextGaussian() * 20, z = random.nextGaussian() * 20;
            double wx = b.worldX(x, y, z), wy = b.worldY(x, y, z), wz = b.worldZ(x, y, z);
            near(b.localX(wx, wy, wz), b.localY(wx, wy, wz), b.localZ(wx, wy, wz), x, y, z, "local -> world -> local");
            require(Math.abs(len(wx, wy, wz) - len(x, y, z)) < 1e-9, "rotation keeps lengths");
        }
    }

    private static void wrapAndTowards() {
        require(Basis.wrapYaw(180) == -180 && Basis.wrapYaw(190) == -170 && Basis.wrapYaw(-190) == 170
            && Basis.wrapYaw(720) == 0 && Basis.wrapYaw(-0f) == 0 && Math.abs(Basis.wrapYaw(359.5f) + .5f) < 1e-4
            && Basis.wrapYaw(-180) == -180, "yaw wrap to [-180, 180)");
        require(Basis.of(10, 120).pitch == 90 && Basis.of(10, -120).pitch == -90, "pitch clamped");
        expectRejected(() -> Basis.of(Float.NaN, 0), "NaN yaw");
        expectRejected(() -> Basis.of(0, Float.POSITIVE_INFINITY), "infinite pitch");
        Random random = new Random(3);
        for (int i = 0; i < 500; i++) {
            float yaw = random.nextFloat() * 359 - 179.5f, pitch = random.nextFloat() * 170 - 85;
            Basis b = Basis.of(yaw, pitch);
            require(Math.abs(Basis.yawTowards(b.fx, b.fz) - yaw) < 1e-3, "yawTowards inverts the forward axis");
            require(Math.abs(Basis.pitchTowards(b.fx, b.fy, b.fz) - pitch) < 1e-3, "pitchTowards inverts it");
        }
        require(Basis.yawTowards(0, 0) == 0 && Basis.pitchTowards(0, 0, 0) == 0, "towards a zero vector");
        require(Basis.yawTowards(-1, 0) == 90 && Basis.yawTowards(1, 0) == -90 && Basis.yawTowards(0, 1) == 0,
            "towards the axes");
    }

    private static void points() {
        require(Point.facing(1, 2, 3).equals(Point.center(-1, 2, 3)), "facing = center(-right, up, forward)");
        require(Point.facing(0, 1, 0).equals(Point.center(0, 1, 0)) && Point.facing(0, 1, 0).x() == 0
            && 1 / Point.facing(0, 1, 0).x() > 0, "facing without -0");
        require(Point.facing(0, 0, 5).source() == Point.Source.CENTER, "facing is a center point");
        expectRejected(() -> Point.facing(Double.NaN, 0, 0), "NaN facing");
        expectRejected(() -> Point.facing(0, 0, 2000), "facing beyond the offset limit");
    }

    private static void placements() {
        Placement fixed = Placement.at(new Vec3(1, 2, 3));
        require(!fixed.anchored() && fixed.yaw() == 0 && fixed.pitch() == 0 && !fixed.followBody() && fixed.caster() == null,
            "fixed placement defaults");
        Placement turned = fixed.withOrientation(450, -30);
        require(turned.yaw() == 90 && turned.pitch() == -30 && fixed.yaw() == 0, "withOrientation wraps and copies");
        require(fixed.withYaw(-0f).yaw() == 0 && 1 / fixed.withYaw(-0f).yaw() > 0, "no -0 yaw");
        expectRejected(() -> fixed.withOrientation(0, 91), "pitch above 90");
        expectRejected(() -> fixed.withOrientation(Float.NaN, 0), "NaN yaw");
        expectRejected(() -> Placement.at(new Vec3(Double.NaN, 0, 0)), "NaN position");
        expectRejected(fixed::followingBody, "followingBody without an anchor");
        Placement ahead = Placement.at(Vec3.ZERO, 90, 0).shifted(1, 2, 3);   // facing west: right is north (-Z)
        near(ahead.position().x, ahead.position().y, ahead.position().z, -3, 2, -1, "shifted turns with the yaw");
        Placement plain = Placement.at(Vec3.ZERO).shifted(1, 0, 0);
        near(plain.position().x, plain.position().y, plain.position().z, -1, 0, 0, "without orientation right is west");
        Placement look = Placement.at(Vec3.ZERO).facing(new Vec3(-5, 0, 0));
        require(Math.abs(look.yaw() - 90) < 1e-4 && look.pitch() == 0, "facing a point to the west");
        Placement below = Placement.at(new Vec3(0, 10, 0)).facing(new Vec3(0, 0, 10));
        require(Math.abs(below.pitch() - 45) < 1e-3 && Math.abs(below.yaw()) < 1e-4, "facing down at 45 degrees");
        require(Placement.at(Vec3.ZERO, 30, 5).facing(Vec3.ZERO).yaw() == 30, "facing the start keeps the orientation");
        expectRejected(() -> Placement.on(null), "null anchor");
    }

    private static void context() {
        var oriented = new Effects.Context(null, Vec3.ZERO, null, null, 0, 0, null, null, 0, 0, 90, 0);
        Vec3 f = oriented.forward();
        near(f.x, f.y, f.z, -1, 0, 0, "Context.forward facing west");
        Vec3 r = oriented.facing(2, 1, 0);
        near(r.x, r.y, r.z, 0, 1, -2, "Context.facing right of west is north");
        var old = new Effects.Context(null, Vec3.ZERO, null, null, 0, 3, null, null, 3, 3);
        require(old.yaw() == 0 && old.pitch() == 0, "0.9 Context form has no orientation");
        Vec3 g = old.forward();
        near(g.x, g.y, g.z, 0, 0, 1, "0.9 form faces south");
        var oldest = new Effects.Context(null, Vec3.ZERO, null, null, 0, 4, null);
        require(oldest.localAge() == 4 && oldest.yaw() == 0, "0.6 Context form");
    }

    private static double len(double x, double y, double z) { return Math.sqrt(x * x + y * y + z * z); }
    private static double dot(double ax, double ay, double az, double bx, double by, double bz) { return ax * bx + ay * by + az * bz; }

    private static void near(double x, double y, double z, double ex, double ey, double ez, String test) {
        require(Math.abs(x - ex) < 1e-6 && Math.abs(y - ey) < 1e-6 && Math.abs(z - ez) < 1e-6,
            test + ": got (" + x + ", " + y + ", " + z + ")");
    }

    private static void expectRejected(Runnable action, String test) {
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return;
        }
        throw new AssertionError("accepted: " + test);
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
