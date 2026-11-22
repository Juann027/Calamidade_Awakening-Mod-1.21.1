package dev.calamidade_awakening.net;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Effects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Reproducible load for measurements:
 * /calamidade bench <count> [effect] places count instances in an arc 14 blocks ahead of the player,
 * /calamidade bench stop ends them, /calamidade status reports live instances (by source kind and with the limits of
 * the server config since 0.10.0). Requires permission level 2. Bench instances are SCRIPT starts, so with the default
 * reserve of 16 slots for players at most 48 of them run at once. Since 0.11.2, /calamidade bench <count> mix cycles
 * through the ring, cube, web and sky recipes of the vfx-sample (engine effects when it is absent), and a bench
 * instance that ends starts again at its place until bench stop, so the load stays for measurements.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class BenchCommand {
    private static final String PREFIX = "command.calamidade_awakening.";
    private static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "nexus");
    /** The varied load of 0.11.2 (ring, cube, web, sky), used when the vfx-sample is installed. */
    static final List<String> MIX = List.of("vfxsample:receita_anel", "vfxsample:receita_cubo",
        "vfxsample:receita_teia", "vfxsample:receita_ceu");
    /** Engine effects for the mix without the vfx-sample. */
    static final List<String> MIX_ENGINE = List.of("calamidade_awakening:nexus", "calamidade_awakening:prism",
        "calamidade_awakening:lattice", "calamidade_awakening:aurora");

    /** One bench instance and where to start it again when it ends (0.11.2). */
    private static final class Slot {
        final ResourceLocation effect;
        final net.minecraft.server.level.ServerLevel level;
        final Vec3 position;
        Effects.Handle handle;

        Slot(ResourceLocation effect, net.minecraft.server.level.ServerLevel level, Vec3 position, Effects.Handle handle) {
            this.effect = effect; this.level = level; this.position = position; this.handle = handle;
        }
    }
    private static final List<Slot> BENCH = new ArrayList<>();

    private BenchCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("calamidade").requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(BenchCommand::status))
            .then(Commands.literal("bench")
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                    .executes(ctx -> bench(ctx, DEFAULT))
                    .then(Commands.literal("mix").executes(BenchCommand::mix))
                    .then(Commands.argument("effect", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VisualKind.networkKeys(), builder))
                        .executes(ctx -> bench(ctx, ResourceLocationArgument.getId(ctx, "effect")))))));
    }

    private static int bench(CommandContext<CommandSourceStack> ctx, ResourceLocation effect) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        if (VisualKind.localId(effect.toString()) == -1) {
            ctx.getSource().sendFailure(Component.translatableWithFallback(PREFIX + "unknown", "Unknown effect %s", effect.toString()));
            return 0;
        }
        int done = arc(player, count, List.of(effect));
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "bench",
            "Started %s of %s × %s (live on server: %s)", done, count, effect.toString(), StageNetwork.liveCount()), true);
        return done;
    }

    /** 0.11.2: the varied load, cycling ring, cube, web and sky (or engine effects without the vfx-sample). */
    private static int mix(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        List<String> names = MIX.stream().allMatch(id -> VisualKind.localId(id) != -1) ? MIX : MIX_ENGINE;
        List<ResourceLocation> effects = names.stream().filter(id -> VisualKind.localId(id) != -1)
            .map(ResourceLocation::parse).toList();
        int done = arc(player, count, effects);
        String list = String.join(", ", names);
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "bench.mix",
            "Mixed load: %s of %s (%s) · live on server: %s", done, count, list, StageNetwork.liveCount()), true);
        return done;
    }

    /** Arc of up to 90 degrees centered on the player's horizontal view, 14 blocks away, at eye height. */
    private static int arc(ServerPlayer player, int count, List<ResourceLocation> effects) {
        stopAll();
        if (effects.isEmpty()) return 0;
        Vec3 look = player.getLookAngle();
        double yaw = Math.atan2(look.z, look.x);
        Vec3 eye = player.getEyePosition();
        int started = 0;
        for (int i = 0; i < count; i++) {
            double angle = yaw + (count == 1 ? 0 : (i / (double) (count - 1) - .5) * Math.PI / 2);
            Vec3 position = eye.add(Math.cos(angle) * 14, 0, Math.sin(angle) * 14);
            ResourceLocation effect = effects.get(i % effects.size());
            var handle = Effects.start(effect, player.serverLevel(), position);
            if (handle.isEmpty()) break;
            BENCH.add(new Slot(effect, player.serverLevel(), position, handle.get()));
            started++;
        }
        return started;
    }

    /** 0.11.2: a bench instance that ended starts again at its place, so a measurement keeps its load. */
    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (Slot slot : BENCH) {
            if (slot.handle.alive()) continue;
            Effects.start(slot.effect, slot.level, slot.position).ifPresent(handle -> slot.handle = handle);
        }
    }

    private static int stop(CommandSourceStack source) {
        int stopped = stopAll();
        source.sendSuccess(() -> Component.translatableWithFallback(PREFIX + "stopped",
            "Stopped %s benchmark instances", stopped), true);
        return stopped;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        int live = StageNetwork.liveCount();
        int[] by = StageNetwork.liveBySource();
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "status",
            "Live instances on server: %s / 64", live), false);
        // 0.10.0: by source kind, the limits of the server config and the starts they refused.
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "status.sources",
            "By source: %s players · %s mobs · %s blocks · %s scripts", by[0], by[1], by[2], by[3]), false);
        var rules = dev.calamidade_awakening.config.ServerConfig.rules();
        int[] refused = StageNetwork.refused();
        int refusedTotal = refused[1] + refused[2] + refused[3];
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "status.limits",
            "Limits: %s per player · %s per mob · %s per block · %s slots reserved for players · refused %s (full %s, reserve %s, per source %s)",
            rules.perPlayer(), rules.perEntity(), rules.perBlock(), rules.playerReserve(), refusedTotal,
            refused[1], refused[2], refused[3]), false);
        // 0.11.0: terrain edits writing, snapshots kept, totals and refusals by reason.
        int[] t = dev.calamidade_awakening.terrain.TerrainWorks.counters();
        int terrainRefused = 0;
        for (int i = 5; i < t.length; i++) terrainRefused += t[i];
        int refusedSum = terrainRefused;
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "status.terrain",
            "Terrain: %s writing · %s undoable · %s commits · %s blocks written · refused %s (config %s, dimension %s, mobGriefing %s, event %s, volume %s, structure %s, error %s, explosion event %s, permission %s, protected %s)",
            t[1], t[0], t[2], t[4], refusedSum, t[5], t[6], t[7], t[8], t[9], t[10], t[11], t[12], t[13], t[14]), false);
        return live;
    }

    private static int stopAll() {
        int stopped = 0;
        for (Slot slot : BENCH) if (slot.handle.stop()) stopped++;
        BENCH.clear();
        return stopped;
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) { BENCH.clear(); }
}
