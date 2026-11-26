package dev.calamidade_awakening.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /calamidade world list}, {@code /calamidade world stop <id>} and {@code /calamidade world release <id>}
 * (0.12.0), permission level 2 like the rest of {@code /calamidade}. {@code <id>} is the effect ({@code ns:path}) or
 * the event key shown by {@code list} ({@code #3}).
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class WorldEventCommand {
    private static final String PREFIX = "command.calamidade_awakening.world.";

    private WorldEventCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("calamidade").requires(source -> source.hasPermission(2))
            .then(Commands.literal("world")
                .then(Commands.literal("list").executes(WorldEventCommand::list))
                .then(Commands.literal("stop").then(target().executes(ctx -> act(ctx, true))))
                .then(Commands.literal("release").then(target().executes(ctx -> act(ctx, false))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("id", StringArgumentType.greedyString()).suggests((ctx, builder) ->
            SharedSuggestionProvider.suggest(WorldEventServer.activeIds(), builder));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var lines = new java.util.ArrayList<>(WorldEventServer.describe());
        // 0.15.0: area events are ordinary instances; list them too, so "no global event" never hides an eclipse.
        for (var i : dev.calamidade_awakening.net.StageNetwork.describe())
            if (i.scope().equals("AREA")) lines.add(dev.calamidade_awakening.net.FxCommand.line(i) + "  (/calamidade fx stop|release " + i.order() + ")");
        if (lines.isEmpty())
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "empty", "No global event"), false);
        for (String line : lines) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return lines.size();
    }

    private static int act(CommandContext<CommandSourceStack> ctx, boolean stop) {
        String target = StringArgumentType.getString(ctx, "id").trim();
        var events = WorldEventServer.matching(target);
        // 0.15.0: an effect id also matches the AREA instances of that effect.
        int area = 0;
        for (var i : dev.calamidade_awakening.net.StageNetwork.describe())
            if (i.scope().equals("AREA") && i.effect().equals(target) && dev.calamidade_awakening.net.StageNetwork.act(i.order(), stop)) area++;
        if (events.isEmpty() && area > 0) {
            int n = area;
            ctx.getSource().sendSuccess(() -> Component.literal((stop ? "Stopped " : "Released ") + n + " AREA instance(s)"), true);
            return area;
        }
        if (events.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatableWithFallback(PREFIX + "none", "No global event %s", target));
            return 0;
        }
        int done = 0;
        for (var g : events) if (stop ? g.stop() : g.release()) done++;
        int count = done;
        ctx.getSource().sendSuccess(() -> stop
            ? Component.translatableWithFallback(PREFIX + "stopped", "Stopped %s global event(s)", count)
            : Component.translatableWithFallback(PREFIX + "released", "Released %s global event(s) (only events with a sustain loop)", count), true);
        return done;
    }
}
