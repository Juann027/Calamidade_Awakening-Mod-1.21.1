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
 * reserve of 16 slots for players at most 48 of them run at once.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class BenchCommand {
    private static final String PREFIX = "command.calamidade_awakening.";
    private static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "nexus");
    private static final List<Effects.Handle> BENCH = new ArrayList<>();

    private BenchCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("calamidade").requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(BenchCommand::status))
            .then(Commands.literal("bench")
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                    .executes(ctx -> bench(ctx, DEFAULT))
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
        stopAll();
        // Arc of up to 90 degrees centered on the player's horizontal view, 14 blocks away, at eye height.
        Vec3 look = player.getLookAngle();
        double yaw = Math.atan2(look.z, look.x);
        Vec3 eye = player.getEyePosition();
        int started = 0;
        for (int i = 0; i < count; i++) {
            double angle = yaw + (count == 1 ? 0 : (i / (double) (count - 1) - .5) * Math.PI / 2);
            Vec3 position = eye.add(Math.cos(angle) * 14, 0, Math.sin(angle) * 14);
            var handle = Effects.start(effect, player.serverLevel(), position);
            if (handle.isEmpty()) break;
            BENCH.add(handle.get());
            started++;
        }
        int done = started;
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "bench",
            "Started %s of %s × %s (live on server: %s)", done, count, effect.toString(), StageNetwork.liveCount()), true);
        return done;
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
        return live;
    }

    private static int stopAll() {
        int stopped = 0;
        for (Effects.Handle handle : BENCH) if (handle.stop()) stopped++;
        BENCH.clear();
        return stopped;
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) { BENCH.clear(); }
}
