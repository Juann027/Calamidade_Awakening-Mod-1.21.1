package example.vfxsample;

import dev.calamidade_awakening.api.v1.Effects;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod("vfxsample")
public final class Sample {
    static final ResourceLocation SIGNAL = ResourceLocation.fromNamespaceAndPath("vfxsample", "signal");
    private final Map<UUID, Effects.Handle> last = new HashMap<>();

    public Sample() {
        dev.calamidade_awakening.api.v1.Addons.resources("vfxsample", Sample.class);
        Effects.register(SIGNAL, new Effects.Definition(120, 2, 0xff65e8c9,
            Effects.Shape.RINGS, Effects.Material.CRYSTAL,
            ResourceLocation.withDefaultNamespace("end_rod"), SIGNAL,
            new Effects.Impact(40, 3, .75f, Effects.ImpactStyle.MONOCHROME)));
        dev.calamidade_awakening.api.v1.Addons.publish(SIGNAL,new dev.calamidade_awakening.api.v1.Addons.Card(
            "ability.vfxsample.signal", "Tidal Signal", "RESONANCE", 130, SIGNAL,
            java.util.List.of(new dev.calamidade_awakening.api.v1.Addons.Shot(30,new Vec3(4,2,6),62),
                new dev.calamidade_awakening.api.v1.Addons.Shot(90,new Vec3(-3,3,7),68))),
            new dev.calamidade_awakening.api.v1.Addons.Range(.5f,1f,2f),
            (player,target,power) -> Effects.start(SIGNAL,player,target.add(0,power-1,0)).isPresent());
        // Gameplay no mesmo tick do impact frame (40): dano leve e empurrão a partir do centro.
        Effects.onImpact(SIGNAL, Sample::impact);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::logout);
        NeoForge.EVENT_BUS.addListener(this::shutdown);
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vfxsample").requires(source -> source.hasPermission(2))
            .then(Commands.literal("at").executes(ctx -> play(ctx.getSource().getPlayerOrException(), false)))
            .then(Commands.literal("follow").executes(ctx -> play(ctx.getSource().getPlayerOrException(), true)))
            .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource().getPlayerOrException().getUUID()) ? 1 : 0)));
    }

    private static void impact(Effects.Context context) {
        var sources = context.level().damageSources();
        var source = context.caster() == null ? sources.magic() : sources.indirectMagic(context.caster(), context.caster());
        for (var target : context.living(3)) {
            target.hurt(source, 4);
            // knockback empurra no sentido oposto ao vetor recebido: alvo -> centro afasta do centro.
            target.knockback(.6, context.center().x - target.getX(), context.center().z - target.getZ());
        }
    }

    private int play(ServerPlayer player, boolean follow) {
        stop(player.getUUID());
        var instance = follow ? Effects.follow(SIGNAL, player, new Vec3(0, 1.5, 0))
            : Effects.start(SIGNAL, player.serverLevel(), player.getEyePosition().add(player.getLookAngle().scale(4)));
        instance.ifPresent(handle -> last.put(player.getUUID(), handle));
        return instance.isPresent() ? 1 : 0;
    }

    private boolean stop(UUID player) {
        var handle = last.remove(player);
        return handle != null && handle.stop();
    }

    private void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player.getUUID());
    }
    private void shutdown(ServerStoppedEvent event) { last.clear(); }
}
