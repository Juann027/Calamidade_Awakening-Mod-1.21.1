package dev.calamidade_awakening.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** 0.10.0: the event_v2 packet carries the orientation (yaw, pitch, follow-body flag) and round-trips it. */
public final class StageEventCheck {
    public static void run() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var caster = new UUID(5, 6);
        var anchor = new UUID(7, 8);
        var fixed = new StageEvent(-2, 41, 1000, caster, dimension, new Vec3(1.5, 64, -3.25), 99, -1, null, Vec3.ZERO,
            -37.5f, 12.25f, false);
        require(roundTrip(fixed).equals(fixed), "fixed oriented event round-trips");
        var follower = new StageEvent(-3, 42, 1001, caster, dimension, new Vec3(0, 70, 0), 7, 12, anchor,
            new Vec3(0, 1.2, .8), 15, -5, true);
        require(roundTrip(follower).equals(follower), "anchored follow-body event round-trips");
        var plain = new StageEvent(-2, 43, 5, new UUID(0, 0), dimension, Vec3.ZERO, 1);
        var back = roundTrip(plain);
        require(back.equals(plain) && back.yaw() == 0 && back.pitch() == 0 && !back.followBody()
            && back.basis().identity(), "0.9-style event: no orientation");
        // A fixed instance cannot follow a body; the reader drops the flag.
        var stray = new StageEvent(-2, 44, 5, caster, dimension, Vec3.ZERO, 1, -1, null, Vec3.ZERO, 10, 0, true);
        require(!roundTrip(stray).followBody(), "follow flag without an anchor is ignored");
        var broken = new StageEvent(-2, 45, 5, caster, dimension, Vec3.ZERO, 1, -1, null, Vec3.ZERO, Float.NaN, 400, false);
        var safe = roundTrip(broken);
        require(safe.yaw() == 0 && safe.pitch() == 0, "non-finite orientation becomes none");
        var wild = new StageEvent(-2, 46, 5, caster, dimension, Vec3.ZERO, 1, -1, null, Vec3.ZERO, 540, 120, false);
        var tamed = roundTrip(wild);
        require(tamed.yaw() == -180 && tamed.pitch() == 90, "yaw wrapped and pitch clamped on read");
        require(StageEvent.PACKET != null, "event_v2 packet");
        System.out.println("StageEventCheck PASS");
    }

    private static StageEvent roundTrip(StageEvent event) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        event.writeBody(buf);
        var read = StageEvent.readBody(event.visual(), buf);
        require(buf.readableBytes() == 0, "whole packet consumed");
        return read;
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
