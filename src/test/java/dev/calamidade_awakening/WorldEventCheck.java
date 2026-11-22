package dev.calamidade_awakening;

import dev.calamidade_awakening.api.SkyMath;
import dev.calamidade_awakening.api.WorldEventRules;
import dev.calamidade_awakening.api.WorldEventRules.Active;
import dev.calamidade_awakening.api.WorldEventRules.Verdict;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.WorldEvents;
import dev.calamidade_awakening.world.WorldEventData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import java.util.List;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/** Global events (0.12.0): admission, scopes, late entry, end, persistence and distant bolts. */
final class WorldEventCheck {
    private WorldEventCheck() {}

    static void run() {
        admission();
        scopes();
        lateEntry();
        persistence();
        options();
        bolts();
        dev.calamidade_awakening.net.WorldEventSyncCheck.run();
        System.out.println("WorldEventCheck PASS");
    }

    private static void admission() {
        var none = List.<Active>of();
        require(WorldEventRules.admit(none, "a:x", 50, 4).verdict() == Verdict.ADMIT, "empty: admit");
        var two = List.of(new Active("a:x", 50, 1), new Active("a:y", 10, 2));
        require(WorldEventRules.admit(two, "a:x", 99, 4).verdict() == Verdict.DUPLICATE, "one per effect");
        require(WorldEventRules.admit(two, "a:z", 0, 4).verdict() == Verdict.ADMIT, "below the limit");
        var full = WorldEventRules.admit(two, "a:z", 11, 2);
        require(full.verdict() == Verdict.REPLACE && full.victim() == 1, "full: replaces the lowest priority");
        require(WorldEventRules.admit(two, "a:z", 10, 2).verdict() == Verdict.REFUSE, "equal priority does not replace");
        var ties = List.of(new Active("a:p", 20, 9), new Active("a:q", 20, 3), new Active("a:r", 60, 1));
        var tie = WorldEventRules.admit(ties, "a:s", 30, 3);
        require(tie.verdict() == Verdict.REPLACE && tie.victim() == 1, "ties: the oldest goes");
        require(WorldEventRules.admit(two, "a:z", 99, 0).verdict() == Verdict.REPLACE, "limit clamped to at least 1");
        var many = new java.util.ArrayList<Active>();
        for (int i = 0; i < 20; i++) many.add(new Active("a:" + i, 0, i));
        require(WorldEventRules.admit(many.subList(0, 16), "a:new", 1, 99).verdict() == Verdict.REPLACE,
            "limit clamped to " + WorldEventRules.MAX_LIMIT);
    }

    private static void scopes() {
        require(WorldEventRules.applies(WorldEvents.Scope.SERVER, "minecraft:overworld", "minecraft:the_nether"),
            "SERVER: every dimension");
        require(WorldEventRules.applies(WorldEvents.Scope.DIMENSION, "minecraft:overworld", "minecraft:overworld"),
            "DIMENSION: its own");
        require(!WorldEventRules.applies(WorldEvents.Scope.DIMENSION, "minecraft:overworld", "vfxsample:dimensao_teste"),
            "DIMENSION: not the others");
        require(!WorldEventRules.applies(WorldEvents.Scope.AREA, "minecraft:overworld", "minecraft:overworld"),
            "AREA is not a global event");
    }

    private static void lateEntry() {
        // Eclipse-like event: 60 ticks in, loop 60..300, exit 300..400 (release jumps there).
        var t = Timeline.builder(400).track("cobrir", keyframe(0, 0f), keyframe(60, 1f), keyframe(300, 1f), keyframe(400, 0f))
            .sustain(60, 300).build();
        long start = 1_000_000;
        // A player who arrives at a game time sees the same local age as the server.
        for (long now : new long[] {start, start + 30, start + 61, start + 1000, start + 77_777}) {
            long age = WorldEventRules.age(now, start);
            double server = t.localAge(age, -1), client = WorldEventRules.localAge(t, age, -1);
            require(server == client, "late entry, same phase at " + age);
            require(t.value("cobrir", client) == (age >= 60 ? 1f : t.value("cobrir", age)), "covered while looping");
        }
        require(WorldEventRules.localAge(null, 42.5, -1) == 42.5, "no timeline: local is real");
        // Release at real age 5000: the exit plays for 100 ticks wherever the client is.
        long released = 5000;
        require(WorldEventRules.localAge(t, 5000, released) == 300, "release jumps to the exit");
        require(WorldEventRules.localAge(t, 5050, released) == 350, "exit continues");
        require(!WorldEventRules.finished(t, 400, 5099, released) && WorldEventRules.finished(t, 400, 5100, released),
            "ends when the exit played out");
        require(!WorldEventRules.finished(t, 400, 1_000_000, -1), "a sustained event never ends without release");
        require(WorldEventRules.finished(null, 80, 80, -1) && !WorldEventRules.finished(null, 80, 79, -1),
            "without timeline: the duration");
        // The clock survives a restart: the start tick is kept and the game time continues from the saved value.
        long savedTime = start + 4000, resumed = savedTime + 1;
        require(WorldEventRules.localAge(t, WorldEventRules.age(resumed, start), -1)
            == t.localAge(WorldEventRules.age(savedTime, start) + 1, -1), "restart resumes the phase");
    }

    private static void persistence() {
        var a = new WorldEventData.Saved("vfxsample:eclipse", WorldEvents.Scope.SERVER, "minecraft:overworld", 1.5, 70,
            -3.25, 123456789L, -77L, -1, 80, 4321);
        var b = new WorldEventData.Saved("vfxsample:caos", WorldEvents.Scope.DIMENSION, "vfxsample:dimensao_teste", 0, 0, 0,
            5, 6, 7000, 0, 7100);
        require(WorldEventData.Saved.fromTag(a.toTag()).equals(a) && WorldEventData.Saved.fromTag(b.toTag()).equals(b),
            "saved event round-trips through NBT");
        ListTag list = WorldEventData.write(List.of(a, b));
        CompoundTag malformed = new CompoundTag();
        malformed.putString("scope", "NOWHERE");
        list.add(malformed);
        CompoundTag missing = a.toTag();
        missing.remove("effect");
        list.add(missing);
        // Through the same binary format the world file uses.
        CompoundTag root = new CompoundTag();
        root.put("events", list);
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            NbtIo.write(root, new java.io.DataOutputStream(bytes));
            root = NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
        var back = WorldEventData.read(root.getList("events", net.minecraft.nbt.Tag.TAG_COMPOUND));
        require(back.equals(List.of(a, b)), "list round-trips and skips malformed entries: " + back);
        var clamped = a.toTag();
        clamped.putInt("priority", 900);
        require(WorldEventData.Saved.fromTag(clamped).priority() == WorldEvents.MAX_PRIORITY, "priority clamped");
    }

    private static void options() {
        var d = WorldEvents.Options.defaults();
        require(d.position() == null && !d.persistent() && d.priority() == 50, "defaults");
        var o = d.at(new net.minecraft.world.phys.Vec3(1, 2, 3)).persistent(true).priority(100);
        require(o.persistent() && o.priority() == 100 && o.position().y == 2 && !d.persistent(), "copies");
        expectRejected(() -> d.priority(101), "priority above 100");
        expectRejected(() -> d.priority(-1), "negative priority");
        expectRejected(() -> d.at(new net.minecraft.world.phys.Vec3(Double.NaN, 0, 0)), "NaN position");
    }

    private static void bolts() {
        var first = SkyMath.bolts(42, 100.3, 1, 0, 180, 1000, -2000, false);
        var again = SkyMath.bolts(42, 100.3, 1, 0, 180, 1000, -2000, false);
        require(first.equals(again), "deterministic: same seed and time, same bolts");
        int total = 0, differ = 0;
        for (double s = 0; s < 60; s += .05) {
            var a = SkyMath.bolts(42, s, 1, 0, 180, 1000, -2000, false);
            var b = SkyMath.bolts(43, s, 1, 0, 180, 1000, -2000, false);
            if (!a.equals(b)) differ++;
            total += a.size();
            require(a.size() <= SkyMath.MAX_BOLTS, "at most " + SkyMath.MAX_BOLTS);
            for (var bolt : a) {
                double d = Math.hypot(bolt.x() - 992, bolt.z() + 2016);   // snapped to the 32-block grid
                require(d >= SkyMath.BOLT_NEAR - 1e-6 && d <= SkyMath.BOLT_FAR + 1e-6, "distance range: " + d);
                require(s >= bolt.start() && s < bolt.start() + bolt.life(), "alive now");
                require(bolt.envelope() >= 0 && bolt.envelope() <= 1, "envelope range");
            }
        }
        require(total > 300 && differ > 300, "activity 1: about two strikes per second (" + total + "), seed matters");
        require(SkyMath.bolts(42, 10, 0, 0, 180, 0, 0, false).isEmpty(), "activity 0: none");
        // Same strikes for every player (their own position): only the anchor moves.
        var here = SkyMath.bolts(7, 33.3, 1, 0, 180, 0, 0, false);
        var there = SkyMath.bolts(7, 33.3, 1, 0, 180, 6400, 3200, false);
        require(here.size() == there.size(), "same rhythm for every player");
        for (int i = 0; i < here.size(); i++)
            require(Math.abs(there.get(i).x() - here.get(i).x() - 6400) < 1e-9 && here.get(i).start() == there.get(i).start(),
                "same strike, around each player");
        // Sector: azimuth 90 (west), 40 degrees wide.
        for (double s = 0; s < 30; s += .1) for (var bolt : SkyMath.bolts(9, s, 1, 90, 40, 0, 0, false)) {
            double az = Sky.Direction.toward(0, 0, 0, bolt.x(), 0, bolt.z()).azimuth();
            require(Math.abs(az - 90) <= 20.001, "inside the sector: " + az);
        }
        // Calm envelope: smooth, no restrike, at most 0.6.
        float previous = -1;
        boolean falling = false;
        for (double x = 0; x <= 1; x += .01) {
            float e = SkyMath.boltEnvelope(x, true);
            require(e <= .6f + 1e-6f, "calm stays dim");
            if (previous >= 0 && e < previous) falling = true;
            require(!(falling && e > previous + 1e-6f), "calm: no restrike");
            previous = e;
        }
        require(SkyMath.boltEnvelope(.02, false) > SkyMath.boltEnvelope(.02, true), "strobe only without calm");
    }

    private static void expectRejected(Runnable action, String test) {
        try {
            action.run();
            throw new AssertionError(test + " accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
