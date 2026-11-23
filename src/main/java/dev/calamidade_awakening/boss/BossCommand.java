package dev.calamidade_awakening.boss;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.v1.Bosses;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.Optional;

/**
 * {@code /calamidade boss list|phase <entity> <phase>|signal <entity> <signal>|detach <entity>} (0.13.0, operators):
 * inspect and drive boss controllers while testing a fight. Attaching is the addon's job; there is no attach command.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class BossCommand {
    private BossCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("calamidade").requires(s -> s.hasPermission(2))
            .then(Commands.literal("boss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(BossCommand::list))
                .then(Commands.literal("phase").then(Commands.argument("entity", EntityArgument.entity())
                    .then(Commands.argument("phase", StringArgumentType.word()).executes(c -> act(c, "phase")))))
                .then(Commands.literal("signal").then(Commands.argument("entity", EntityArgument.entity())
                    .then(Commands.argument("signal", StringArgumentType.word()).executes(c -> act(c, "signal")))))
                .then(Commands.literal("detach").then(Commands.argument("entity", EntityArgument.entity())
                    .executes(c -> act(c, "detach"))))));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var all = Bosses.active(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("Bosses: " + all.size()), false);
        for (Bosses.Handle h : all) {
            String line = h.id() + " " + h.entity().getUUID() + " phase=" + h.phase() + " ticks=" + h.phaseTicks()
                + (h.locked() ? " (sequence)" : "") + " health=" + Math.round(h.entity().getHealth()) + "/"
                + Math.round(h.entity().getMaxHealth());
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int act(CommandContext<CommandSourceStack> c, String what) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(c, "entity");
        Optional<Bosses.Handle> handle = Bosses.get(entity);
        if (handle.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No boss controller on " + entity.getName().getString()));
            return 0;
        }
        boolean ok;
        try {
            ok = switch (what) {
                case "phase" -> handle.get().phase(StringArgumentType.getString(c, "phase"));
                case "signal" -> handle.get().signal(StringArgumentType.getString(c, "signal"));
                default -> handle.get().detach();
            };
        } catch (IllegalArgumentException bad) {
            c.getSource().sendFailure(Component.literal(bad.getMessage()));
            return 0;
        }
        if (!ok) {
            c.getSource().sendFailure(Component.literal(what + " refused"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(what + " ok: " + handle.get()), true);
        return 1;
    }
}
