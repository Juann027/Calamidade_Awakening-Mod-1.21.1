package dev.calamidade_awakening.terrain;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.calamidade_awakening.CalamidadeAwakening;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /calamidade terrain list} and {@code /calamidade terrain undo [último|ultimo|<id>]} (0.11.0), permission
 * level 2 like the rest of {@code /calamidade}. Undo writes the old blocks back with the same batched writer, only where
 * the edit's blocks are still there.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class TerrainCommand {
    private static final String PREFIX = "command.calamidade_awakening.terrain.";

    private TerrainCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("calamidade").requires(source -> source.hasPermission(2))
            .then(Commands.literal("terrain")
                .then(Commands.literal("list").executes(TerrainCommand::list))
                .then(Commands.literal("undo").executes(ctx -> undo(ctx, -1))
                    .then(Commands.literal("last").executes(ctx -> undo(ctx, -1)))
                    // Legacy Portuguese aliases (hidden from the docs): same implementation.
                    .then(Commands.literal("último").executes(ctx -> undo(ctx, -1)))
                    .then(Commands.literal("ultimo").executes(ctx -> undo(ctx, -1)))
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> undo(ctx, IntegerArgumentType.getInteger(ctx, "id")))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var lines = TerrainWorks.list(ctx.getSource().getServer());
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "empty",
                "No terrain edit to undo"), false);
        }
        for (String line : lines) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        String report = TerrainWorks.lastReport();
        if (!report.isEmpty())
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "last", "Last: %s", report), false);
        return lines.size();
    }

    private static int undo(CommandContext<CommandSourceStack> ctx, int id) {
        String undone = TerrainWorks.undo(ctx.getSource().getServer(), id);
        if (undone == null) {
            ctx.getSource().sendFailure(Component.translatableWithFallback(PREFIX + "none", "No terrain edit to undo"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(PREFIX + "undo", "Undoing %s", undone), true);
        return 1;
    }
}
