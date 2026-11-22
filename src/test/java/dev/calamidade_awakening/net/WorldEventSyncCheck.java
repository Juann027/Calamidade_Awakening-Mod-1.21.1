package dev.calamidade_awakening.net;

import dev.calamidade_awakening.api.v1.WorldEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** 0.12.0: the world_event_v1 packet round-trips every field, before and after the release. */
public final class WorldEventSyncCheck {
    public static void run() {
        var nether = ResourceLocation.withDefaultNamespace("the_nether");
        for (var scope : new WorldEvents.Scope[] {WorldEvents.Scope.DIMENSION, WorldEvents.Scope.SERVER}) {
            var before = new WorldEventSync(7, "vfxsample:eclipse", scope, nether, new Vec3(10.5, 70, -300.25), 123456789L,
                -4242L, -1, 80);
            require(roundTrip(before).equals(before), "state before the release round-trips: " + scope);
            var after = new WorldEventSync(7, "vfxsample:eclipse", scope, nether, Vec3.ZERO, 5, 1, 1234, 100);
            require(roundTrip(after).equals(after), "state after the release round-trips: " + scope);
        }
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        new WorldEventSync(1, "a:b", WorldEvents.Scope.SERVER, nether, Vec3.ZERO, 0, 0, -1, 0).write(buf);
        require(buf.readableBytes() < 80, "a small packet, once per change: " + buf.readableBytes());
        System.out.println("WorldEventSyncCheck PASS");
    }

    private static WorldEventSync roundTrip(WorldEventSync packet) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        var back = WorldEventSync.read(buf);
        require(buf.readableBytes() == 0, "packet fully read");
        return back;
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
