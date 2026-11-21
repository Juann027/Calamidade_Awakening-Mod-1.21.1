package dev.calamidade_awakening;

import dev.calamidade_awakening.api.Cooldowns;
import dev.calamidade_awakening.api.SourceLimits;
import dev.calamidade_awakening.api.SourceLimits.Rules;
import dev.calamidade_awakening.api.SourceLimits.Verdict;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.api.v1.EffectSource.Kind;
import dev.calamidade_awakening.api.v1.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** 0.10.0: sources, per-source limits, the slots reserved for players and cooldowns per source. */
final class SourceCheck {
    static void run() {
        sources();
        rules();
        admission();
        simulation();
        cooldowns();
        placements();
        System.out.println("SourceCheck PASS");
    }

    private static void sources() {
        var a = new UUID(1, 1);
        var overworld = ResourceLocation.withDefaultNamespace("overworld");
        var nether = ResourceLocation.withDefaultNamespace("the_nether");
        require(EffectSource.player(a).equals(EffectSource.player(a)) && EffectSource.player(a).hashCode()
            == EffectSource.player(a).hashCode(), "player equality");
        require(!EffectSource.player(a).equals(EffectSource.entity(a)), "player and entity with one UUID differ");
        require(EffectSource.player(a).kind() == Kind.PLAYER && EffectSource.entity(a).kind() == Kind.ENTITY, "kinds");
        var pos = new BlockPos.MutableBlockPos(10, 64, -3);
        var block = EffectSource.block(overworld, pos);
        pos.set(0, 0, 0);
        require(block.pos().equals(new BlockPos(10, 64, -3)), "block position copied (mutable positions are safe)");
        require(block.equals(EffectSource.block(overworld, new BlockPos(10, 64, -3))), "block equality");
        require(!block.equals(EffectSource.block(nether, new BlockPos(10, 64, -3))), "same position, other dimension");
        require(!block.equals(EffectSource.block(overworld, new BlockPos(10, 65, -3))), "other position");
        require(block.uuid() == null && block.dimension().equals(overworld) && EffectSource.player(a).pos() == null,
            "identity fields");
        require(EffectSource.script() == EffectSource.script() && EffectSource.script().kind() == Kind.SCRIPT
            && EffectSource.script().uuid() == null, "script singleton");
        require(block.toString().equals("BLOCK(minecraft:overworld 10 64 -3)") && EffectSource.script().toString().equals("SCRIPT"),
            "readable form");
        expect(() -> EffectSource.player((UUID) null), "null player");
        expect(() -> EffectSource.block(overworld, null), "null block position");
    }

    private static void rules() {
        Rules d = Rules.DEFAULT;
        require(d.perPlayer() == 16 && d.perEntity() == 4 && d.perBlock() == 2 && d.playerReserve() == 16, "defaults");
        require(d.perSource(Kind.SCRIPT) == SourceLimits.TOTAL && d.perSource(Kind.BLOCK) == 2, "per source");
        expect(() -> new Rules(0, 4, 2, 16), "per-player 0");
        expect(() -> new Rules(16, 65, 2, 16), "per-entity above 64");
        expect(() -> new Rules(16, 4, 2, -1), "negative reserve");
        new Rules(64, 64, 64, 64);
        new Rules(1, 1, 1, 0);
    }

    private static void admission() {
        Rules d = Rules.DEFAULT;
        require(SourceLimits.admit(d, Kind.PLAYER, 0, 0, 0) == Verdict.OK, "empty server");
        require(SourceLimits.admit(d, Kind.PLAYER, 64, 0, 0) == Verdict.FULL
            && SourceLimits.admit(d, Kind.SCRIPT, 64, 64, 0) == Verdict.FULL, "64 is the hard total");
        // Non-players stop at 48 with the default reserve; players continue to 64.
        require(SourceLimits.admit(d, Kind.ENTITY, 48, 48, 0) == Verdict.RESERVED, "mob uses only the shared slots");
        require(SourceLimits.admit(d, Kind.BLOCK, 48, 48, 0) == Verdict.RESERVED, "block too");
        require(SourceLimits.admit(d, Kind.SCRIPT, 48, 48, 0) == Verdict.RESERVED, "script too");
        require(SourceLimits.admit(d, Kind.SCRIPT, 47, 47, 0) == Verdict.OK, "47 shared slots used: one left");
        require(SourceLimits.admit(d, Kind.PLAYER, 63, 48, 0) == Verdict.OK, "a player takes the reserve");
        // Player instances do not consume the shared slots.
        require(SourceLimits.admit(d, Kind.ENTITY, 60, 40, 0) == Verdict.OK, "players hold 20, mobs still fit");
        // Per source.
        require(SourceLimits.admit(d, Kind.ENTITY, 10, 10, 4) == Verdict.SOURCE_LIMIT
            && SourceLimits.admit(d, Kind.ENTITY, 10, 10, 3) == Verdict.OK, "4 per mob");
        require(SourceLimits.admit(d, Kind.BLOCK, 10, 10, 2) == Verdict.SOURCE_LIMIT
            && SourceLimits.admit(d, Kind.BLOCK, 10, 10, 1) == Verdict.OK, "2 per block");
        require(SourceLimits.admit(d, Kind.PLAYER, 20, 0, 16) == Verdict.SOURCE_LIMIT, "16 per player");
        require(SourceLimits.admit(d, Kind.SCRIPT, 40, 40, 40) == Verdict.OK, "script has no per-source limit");
        // No reserve: everyone shares 64.
        Rules open = new Rules(16, 4, 2, 0);
        require(SourceLimits.admit(open, Kind.SCRIPT, 63, 63, 63) == Verdict.OK, "no reserve");
        Rules closed = new Rules(16, 4, 2, 64);
        require(SourceLimits.admit(closed, Kind.ENTITY, 0, 0, 0) == Verdict.RESERVED
            && SourceLimits.admit(closed, Kind.PLAYER, 0, 0, 0) == Verdict.OK, "reserve 64: players only");
    }

    /** Random starts and ends: the invariants hold whatever the order. */
    private static void simulation() {
        record Live(Kind kind, int id) {}
        Random random = new Random(42);
        Rules rules = Rules.DEFAULT;
        List<Live> live = new ArrayList<>();
        int refusedReserve = 0;
        for (int step = 0; step < 20000; step++) {
            if (!live.isEmpty() && random.nextInt(3) == 0) { live.remove(random.nextInt(live.size())); continue; }
            Kind kind = Kind.values()[random.nextInt(4)];
            int id = random.nextInt(kind == Kind.SCRIPT ? 1 : 6);
            int nonPlayer = (int) live.stream().filter(l -> l.kind() != Kind.PLAYER).count();
            int same = (int) live.stream().filter(l -> l.kind() == kind && l.id() == id).count();
            Verdict verdict = SourceLimits.admit(rules, kind, live.size(), nonPlayer, same);
            if (verdict == Verdict.RESERVED) refusedReserve++;
            if (verdict.ok()) live.add(new Live(kind, id));
            nonPlayer = (int) live.stream().filter(l -> l.kind() != Kind.PLAYER).count();
            require(live.size() <= SourceLimits.TOTAL && nonPlayer <= SourceLimits.TOTAL - rules.playerReserve(),
                "total and reserve never exceeded");
            for (Kind k : Kind.values()) for (int i = 0; i < 6; i++) {
                final Kind kk = k; final int ii = i;
                long count = live.stream().filter(l -> l.kind() == kk && l.id() == ii).count();
                require(k == Kind.SCRIPT || count <= rules.perSource(k), "per-source limit never exceeded");
            }
        }
        require(refusedReserve > 0, "the simulation reached the reserve");
    }

    private static void cooldowns() {
        var cooldowns = new Cooldowns();
        var zombie = EffectSource.entity(new UUID(9, 9));
        var pillar = EffectSource.block(ResourceLocation.withDefaultNamespace("overworld"), new BlockPos(1, 2, 3));
        var samePillar = EffectSource.block(ResourceLocation.withDefaultNamespace("overworld"), new BlockPos(1, 2, 3));
        var player = EffectSource.player(new UUID(9, 9));
        cooldowns.start(zombie, -2, 100, 40);
        require(!cooldowns.ready(zombie, -2, 139) && cooldowns.ready(zombie, -2, 140), "entity cooldown");
        require(cooldowns.ready(player, -2, 120), "a player with the mob's UUID is another source");
        require(cooldowns.ready(zombie, -3, 120), "other effect of the same mob");
        cooldowns.start(pillar, -2, 100, 20);
        require(!cooldowns.ready(samePillar, -2, 110) && cooldowns.remaining(samePillar, -2, 110) == 10,
            "block cooldown by value (dimension and position)");
        cooldowns.cancel(samePillar, -2);
        require(cooldowns.ready(pillar, -2, 110), "cancel clears it");
        cooldowns.start(new UUID(1, 2), 4, 0, 10);
        require(!cooldowns.ready(new UUID(1, 2), 4, 5), "0.5 UUID keys still work");
        cooldowns.prune(200);
        require(cooldowns.size() == 0, "prune");
    }

    private static void placements() {
        Placement fixed = Placement.at(new Vec3(0, 64, 0));
        require(fixed.source().equals(EffectSource.script()) && fixed.cooldown() == 0 && fixed.block() == null,
            "a fixed placement without caster is a script");
        var block = EffectSource.block(ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO);
        require(fixed.withSource(block).source().equals(block), "explicit source");
        require(fixed.withSource(block).withSource(null).source().equals(EffectSource.script()), "null restores the default");
        require(fixed.withCooldown(40).cooldown() == 40 && fixed.withCooldown(40).withYaw(90).cooldown() == 40,
            "cooldown kept across copies");
        require(fixed.withSource(block).withOrientation(10, 5).shifted(1, 0, 0).source().equals(block), "source kept");
        expect(() -> fixed.withCooldown(-1), "negative cooldown");
        expect(() -> fixed.withCooldown(72001), "cooldown above 72000");
        expect(() -> fixed.boundTo(null), "null block entity");
    }

    private static void expect(Runnable action, String test) {
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
