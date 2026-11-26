package dev.calamidade_awakening.net;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 0.15.0: diagnostics and control of live effect instances, whatever their scope (operators, permission 2).
 * <ul>
 * <li>{@code /calamidade fx list} — every instance and every global event: instance number, effect, scope, dimension,
 * source (who started it), owner (the controller its life depends on), anchor (what it follows), state, position.</li>
 * <li>{@code /calamidade fx orphans} — sustained instances whose owner controller is gone.</li>
 * <li>{@code /calamidade fx stop <n>} — ends one instance immediately (no exit); {@code /calamidade fx release <n>}
 * — cinematic release: a sustained instance plays its exit and ends.</li>
 * <li>{@code /calamidade fx stopall} — ends every instance (not the global events; use {@code /calamidade world}).</li>
 * </ul>
 * Several instances of one effect are normal (overlaps, transitions): the commands act on one instance by number.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class FxCommand {
    private FxCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("calamidade").requires(source -> source.hasPermission(2))
            .then(Commands.literal("fx")
                .then(Commands.literal("list").executes(ctx -> list(ctx, false)))
                .then(Commands.literal("orphans").executes(ctx -> list(ctx, true)))
                .then(Commands.literal("stop").then(Commands.argument("n", LongArgumentType.longArg(0))
                    .executes(ctx -> act(ctx, true))))
                .then(Commands.literal("release").then(Commands.argument("n", LongArgumentType.longArg(0))
                    .executes(ctx -> act(ctx, false))))
                .then(Commands.literal("stopall").executes(FxCommand::stopAll))));
    }

    /** One line of the listing. */
    public static String line(StageNetwork.Info i) {
        return String.format(java.util.Locale.ROOT, "%s%d %s · %s em %s · fonte %s · dono %s · âncora %s · %s · (%.0f %.0f %.0f)%s",
            i.orphan() ? "ÓRFÃO " : "", i.order(), i.effect(), i.scope(), i.dimension(), i.source(), i.owner(), i.anchor(), i.state(),
            i.center().x, i.center().y, i.center().z, i.note().isEmpty() ? "" : " · " + i.note());
    }

    private static int list(CommandContext<CommandSourceStack> ctx, boolean orphansOnly) {
        int n = 0;
        for (StageNetwork.Info i : StageNetwork.describe()) {
            if (orphansOnly && !i.orphan()) continue;
            String text = line(i);
            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
            n++;
        }
        if (!orphansOnly) for (String g : dev.calamidade_awakening.world.WorldEventServer.describeAll()) {
            ctx.getSource().sendSuccess(() -> Component.literal("global " + g), false);
            n++;
        }
        int count = n;
        int released = StageNetwork.orphansReleased();
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("command.calamidade_awakening.fx.count",
            "%s item(s) · orphans released automatically since start: %s", count, released), false);
        return n;
    }

    private static int act(CommandContext<CommandSourceStack> ctx, boolean stop) {
        long n = LongArgumentType.getLong(ctx, "n");
        boolean ok = StageNetwork.act(n, stop);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatableWithFallback("command.calamidade_awakening.fx.none",
                "No instance %s (or it cannot be released: not sustained, or already released)", n));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> stop
            ? Component.translatableWithFallback("command.calamidade_awakening.fx.stopped", "Instance %s stopped", n)
            : Component.translatableWithFallback("command.calamidade_awakening.fx.released", "Instance %s released (playing its exit)", n), true);
        return 1;
    }

    private static int stopAll(CommandContext<CommandSourceStack> ctx) {
        int done = 0;
        for (StageNetwork.Info i : StageNetwork.describe()) if (StageNetwork.act(i.order(), true)) done++;
        int count = done;
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("command.calamidade_awakening.fx.stoppedall",
            "%s instance(s) stopped", count), true);
        return done;
    }
}
