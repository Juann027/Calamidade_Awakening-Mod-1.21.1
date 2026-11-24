package dev.bossestesting.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bossestesting.BossesTesting;
import dev.bossestesting.BtConfig;
import dev.bossestesting.ai.Brain;
import dev.bossestesting.ai.Phase1Brain;
import dev.bossestesting.ai.Phase2Brain;
import dev.bossestesting.encounter.BossDefs;
import dev.bossestesting.encounter.Encounter;
import dev.bossestesting.encounter.Encounters;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoFinale;
import dev.bossestesting.entity.JarGolem;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.net.MusicPayload;
import dev.bossestesting.registry.ModEntities;
import dev.calamidade_awakening.api.v1.Bosses;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.WorldEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /bossestesting (permission 2). The boss never spawns by itself: these commands are the only way in.
 * Syntax in README.md.
 */
public final class BtCommands {
    private static final Map<String, ResourceLocation> SKIES = Map.of("p1", Fx.SKY_P1, "p1_fury", Fx.SKY_P1_FURY, "transform", Fx.SKY_TRANSFORM,
        "p2", Fx.SKY_P2, "overload", Fx.SKY_OVERLOAD);
    private static final Map<UUID, WorldEvents.Handle> TEST_SKY = new HashMap<>();
    private static final UUID TEST_MUSIC = UUID.nameUUIDFromBytes("bossestesting:test".getBytes());

    private BtCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bossestesting").requires(s -> s.hasPermission(2))
            .then(Commands.literal("spawn")
                .executes(c -> spawn(c, false))
                .then(Commands.literal("phase1").executes(c -> spawn(c, false)))
                .then(Commands.literal("phase2").executes(c -> spawn(c, true))))
            .then(Commands.literal("transform").executes(BtCommands::transform))
            .then(Commands.literal("overloaded")
                .then(Commands.literal("on").executes(c -> overloaded(c, true)))
                .then(Commands.literal("off").executes(c -> overloaded(c, false))))
            .then(Commands.literal("attack").then(Commands.argument("move", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(moveNames(), b)).executes(BtCommands::attack)))
            .then(Commands.literal("freeze")
                .then(Commands.literal("on").executes(c -> freeze(c, true)))
                .then(Commands.literal("off").executes(c -> freeze(c, false))))
            .then(Commands.literal("damage").then(Commands.argument("amount", FloatArgumentType.floatArg(0)).executes(BtCommands::damage)))
            .then(Commands.literal("test")
                .then(Commands.literal("vfx").then(Commands.argument("effect", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(effectNames(), b)).executes(BtCommands::vfx)))
                .then(Commands.literal("sky").then(Commands.argument("sky", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(skyNames(), b)).executes(BtCommands::sky)))
                .then(Commands.literal("music")
                    .then(Commands.literal("play").executes(c -> music(c, true)))
                    .then(Commands.literal("stop").executes(c -> music(c, false))))
                .then(Commands.literal("bar").executes(BtCommands::bar)))
            .then(Commands.literal("destruction")
                .then(Commands.literal("off").executes(c -> destruction(c, BtConfig.Destruction.OFF)))
                .then(Commands.literal("low").executes(c -> destruction(c, BtConfig.Destruction.LOW)))
                .then(Commands.literal("normal").executes(c -> destruction(c, BtConfig.Destruction.NORMAL)))
                .then(Commands.literal("epic").executes(c -> destruction(c, BtConfig.Destruction.EPIC)))
                .then(Commands.literal("demo")
                    .then(Commands.literal("on").executes(c -> demo(c, true)))
                    .then(Commands.literal("off").executes(c -> demo(c, false)))))
            .then(Commands.literal("status").executes(BtCommands::status))
            .then(Commands.literal("clear").executes(BtCommands::clear)));
    }

    // ---------------------------------------------------------------- spawn

    private static int spawn(CommandContext<CommandSourceStack> c, boolean phase2) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 spot = player.position().add(look.scale(16));
        float yaw = player.getYRot() + 180;
        if (phase2) {
            Spawner.phase2(level, spot, yaw);
            c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.spawn.phase2"), true);
        } else {
            int height = Spawner.phase1(level, spot, yaw).height();
            c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.spawn.phase1", height), true);
        }
        return 1;
    }

    // ---------------------------------------------------------------- control

    private static GuilherminoBoss nearest(CommandSourceStack s) throws CommandSyntaxException {
        Vec3 at = s.getPosition();
        ServerLevel level = s.getLevel();
        List<GuilherminoBoss> list = new ArrayList<>();
        for (var e : level.getAllEntities()) if (e instanceof GuilherminoBoss b && b.isAlive()) list.add(b);
        return list.stream().min(Comparator.comparingDouble(b -> b.distanceToSqr(at)))
            .orElseThrow(() -> new CommandSyntaxException(null, Component.translatable("command.bossestesting.none")));
    }

    private static int transform(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        GuilherminoBoss boss = nearest(c.getSource());
        if (!(boss.brain() instanceof Phase1Brain b)) {
            c.getSource().sendFailure(Component.translatable("command.bossestesting.need_phase1"));
            return 0;
        }
        b.requestTransform();
        c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.transform"), true);
        return 1;
    }

    private static int overloaded(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        GuilherminoBoss boss = nearest(c.getSource());
        if (!(boss.brain() instanceof Phase2Brain b)) {
            c.getSource().sendFailure(Component.translatable("command.bossestesting.need_phase2"));
            return 0;
        }
        if (on) b.force("overload_v2"); else b.exitOverloaded();
        c.getSource().sendSuccess(() -> Component.translatable(on ? "command.bossestesting.overloaded.on" : "command.bossestesting.overloaded.off"), true);
        return 1;
    }

    private static List<String> moveNames() {
        List<String> names = new ArrayList<>(List.of("slam", "reactor", "punch_right", "punch_left", "dash", "stagger", "fury", "transform",
            "slam_v2", "missile_barrage", "punch_stream", "exposed_reactor", "overload_v2", "charged_punch", "shield_charge", "leap_smash"));
        return names;
    }

    private static int attack(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        GuilherminoBoss boss = nearest(c.getSource());
        String move = StringArgumentType.getString(c, "move");
        Brain brain = boss.brain();
        if (brain instanceof Phase2Brain p2 && Set.of("charged_punch", "shield_charge", "leap_smash").contains(move) && !p2.overloaded())
            p2.enterOverloaded(); // Overloaded-only moves: the test turns the state on first
        boolean ok = brain.force(move);
        if (!ok) {
            c.getSource().sendFailure(Component.translatable("command.bossestesting.attack.unknown", move));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.attack", move), true);
        return 1;
    }

    private static final java.util.Set<UUID> FROZEN = new java.util.HashSet<>();

    /** True when the AI of this boss is frozen by the test command. */
    public static boolean frozen(GuilherminoBoss boss) { return FROZEN.contains(boss.getUUID()); }

    private static int freeze(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        GuilherminoBoss boss = nearest(c.getSource());
        if (on) FROZEN.add(boss.getUUID()); else FROZEN.remove(boss.getUUID());
        boss.setNoAi(on);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "command.bossestesting.freeze.on" : "command.bossestesting.freeze.off"), true);
        return 1;
    }

    private static int damage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        GuilherminoBoss boss = nearest(c.getSource());
        float amount = FloatArgumentType.getFloat(c, "amount");
        boss.hurt(boss.damageSources().generic(), amount);
        c.getSource().sendSuccess(() -> Component.literal(String.format("%.0f / %.0f", boss.getHealth(), boss.getMaxHealth())), false);
        return 1;
    }

    // ---------------------------------------------------------------- tests

    private static List<String> effectNames() {
        List<String> out = new ArrayList<>();
        for (var f : Fx.class.getFields()) {
            try {
                Object v = f.get(null);
                if (v instanceof ResourceLocation r && !r.getPath().startsWith("sky_")) out.add(r.getPath());
                if (v instanceof dev.bossestesting.fx.Variants vs) for (ResourceLocation r : vs.all()) out.add(r.getPath());
            } catch (IllegalAccessException ignored) {}
        }
        return out;
    }

    private static int vfx(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "effect");
        ResourceLocation id = BossesTesting.id(name);
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 at = player.position().add(look.scale(10));
        ServerLevel level = player.serverLevel();
        at = new Vec3(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z);
        GuilherminoBoss near = null;
        try { near = nearest(c.getSource()); } catch (CommandSyntaxException ignored) {}
        boolean needsBoss = name.startsWith("thrust") || name.startsWith("punch") || name.contains("charge") && !name.startsWith("charged")
            || name.equals("barrage") || name.startsWith("aura") || name.equals("stagger") || name.equals("dash") || name.equals("fury")
            || name.equals("overload_v2") || name.equals("overload_vent");
        var placement = needsBoss && near != null && near.distanceTo(player) < 64 ? Placement.on(near).followingBody()
            : name.startsWith("cam_") ? null : Placement.at(at, player.getYRot(), 0).withCaster(player);
        var h = name.startsWith("cam_") ? Effects.start(id, player, at, player.getYRot(), 0) : Effects.start(id, level, placement);
        if (h.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.bossestesting.vfx.refused", name));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.vfx", name), false);
        return 1;
    }

    private static List<String> skyNames() { List<String> l = new ArrayList<>(SKIES.keySet()); l.add("off"); return l; }

    private static int sky(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "sky");
        WorldEvents.Handle old = TEST_SKY.remove(player.getUUID());
        if (old != null) old.release();
        if (name.equals("off")) {
            c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.sky.off"), false);
            return 1;
        }
        ResourceLocation id = SKIES.get(name);
        if (id == null) return 0;
        var h = WorldEvents.start(id, WorldEvents.Scope.AREA, player.serverLevel(), WorldEvents.Options.defaults().at(player.position()));
        h.ifPresent(handle -> TEST_SKY.put(player.getUUID(), handle));
        c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.sky", name), false);
        return h.isPresent() ? 1 : 0;
    }

    private static int music(CommandContext<CommandSourceStack> c, boolean play) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        PacketDistributor.sendToPlayer(player, play ? MusicPayload.play(TEST_MUSIC) : MusicPayload.stop(TEST_MUSIC, 40));
        c.getSource().sendSuccess(() -> Component.translatable(play ? "command.bossestesting.music.play" : "command.bossestesting.music.stop"), false);
        return 1;
    }

    /** Bar test: freezes the nearest boss and walks its health through the phase marks every second. */
    private static int bar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        GuilherminoBoss boss = nearest(c.getSource());
        FROZEN.add(boss.getUUID());
        boss.setNoAi(true);
        float max = boss.getMaxHealth();
        boss.setHealth(max * .62f);
        c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.bar"), false);
        return 1;
    }

    private static int destruction(CommandContext<CommandSourceStack> c, BtConfig.Destruction level) {
        BtConfig.DESTRUCTION.set(level);
        BtConfig.SPEC.save();
        boolean griefing = c.getSource().getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.destruction", level.name(), BtConfig.destruction().name(),
            griefing ? "on" : "off"), true);
        return 1;
    }

    private static int demo(CommandContext<CommandSourceStack> c, boolean on) {
        BtConfig.DEMO_PROFILE.set(on);
        BtConfig.SPEC.save();
        boolean griefing = c.getSource().getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "command.bossestesting.demo.on" : "command.bossestesting.demo.off",
            griefing ? "on" : "off"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        var src = c.getSource();
        src.sendSuccess(() -> Component.literal("BossesTesting 0.1.0 · destruction " + BtConfig.destruction()
            + " (config " + BtConfig.DESTRUCTION.get() + ", demo " + BtConfig.DEMO_PROFILE.get() + ")"), false);
        for (Encounter e : Encounters.all()) {
            GuilherminoBoss b = e.boss(src.getServer());
            String who = b == null ? "-" : b.getType().getDescriptionId() + String.format(" %.0f/%.0f", b.getHealth(), b.getMaxHealth())
                + (b.brain() != null ? " · " + b.brain().lastMove() : "") + (b.flag(GuilherminoBoss.F_OVERLOADED) ? " · OVERLOADED" : "");
            src.sendSuccess(() -> Component.literal(" " + e.id().toString().substring(0, 8) + " " + e.stage() + " · music " + e.music()
                + " · participants " + e.participants().size() + " · " + who), false);
        }
        return Encounters.all().size();
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        int n = Encounters.clearAll(c.getSource().getServer());
        int removed = 0;
        for (ServerLevel level : c.getSource().getServer().getAllLevels())
            for (var e : level.getAllEntities()) {
                if (e instanceof GuilherminoBoss b) { Bosses.get(b).ifPresent(Bosses.Handle::detach); b.discard(); removed++; }
                else if (e instanceof dev.bossestesting.entity.GuilherminoMissile m) m.fizzle();
            }
        for (WorldEvents.Handle h : TEST_SKY.values()) h.release();
        TEST_SKY.clear();
        FROZEN.clear();
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p, MusicPayload.stop(TEST_MUSIC, 20));
        int total = removed;
        c.getSource().sendSuccess(() -> Component.translatable("command.bossestesting.clear", n, total), true);
        return 1;
    }

    private static final class Set {
        static java.util.Set<String> of(String... s) { return java.util.Set.of(s); }
    }
}
