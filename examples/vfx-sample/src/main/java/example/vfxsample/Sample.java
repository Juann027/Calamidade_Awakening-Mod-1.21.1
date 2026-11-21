package example.vfxsample;

import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

@Mod("vfxsample")
public final class Sample {
    static final ResourceLocation SIGNAL = ResourceLocation.fromNamespaceAndPath("vfxsample", "signal");
    static final ResourceLocation BEACON = ResourceLocation.fromNamespaceAndPath("vfxsample", "farol");

    /**
     * 1.4.0 — roteiro do Sinal de Maré. Formação até o impacto no tick 40, eco até o fim. A trilha de intensidade
     * guia os anéis do shader (uploadTracks) e o marcador "impacto" dispara o dano no servidor.
     */
    static final Timeline SIGNAL_TIMELINE = Timeline.builder(120)
        .phase("formacao", 0, 40, 8, 6)
        .phase("impacto", 38, 54, 2, 12)
        .phase("eco", 46, 120, 10, 34)
        .track("intensidade",
            keyframe(0, 0f),
            keyframe(36, .45f, Easing.EASE_IN),
            keyframe(40, 1f, Easing.OVERSHOOT),
            keyframe(60, .5f, Easing.SMOOTH),
            keyframe(120, 0f, Easing.EASE_OUT))
        // 1.6.0 — luz breve no impacto: sobe em 4 ticks e apaga até o tick 58.
        .track("luz", keyframe(36, 0f), keyframe(40, 1f, Easing.EASE_OUT), keyframe(58, 0f, Easing.SMOOTH))
        .marker("impacto", 40)
        .build();

    /**
     * 1.5.0 — atmosfera curta do Sinal de Maré: céu levemente dourado, exposição um pouco acima e um toque quente,
     * só enquanto a fase "impacto" (ticks 38 a 54) tem peso. O motor mistura com outras atmosferas e limita a
     * exposição para quem oculta clarões.
     */
    static final Atmosphere SIGNAL_ATMOSPHERE = Atmosphere.builder()
        .sky(0xff4a3008, 0xffffc060, .35f)
        .exposure(1.18f)
        .ambient(0xffffd8a0, .12f)
        .weightPhase("impacto")
        .build();

    /**
     * 1.6.0 — luz visual do impacto: verde-água, alcance 9 blocos, só enquanto a trilha "luz" sobe e desce. Ilumina o
     * chão e as criaturas atingidas; não muda o nível de luz do jogo.
     */
    static final Light SIGNAL_LIGHT = Light.point(Point.center(0, .5, 0))
        .color(0xff65e8c9)
        .intensity(2.6f)
        .radius(9)
        .falloff(1.5f)
        .track(Light.Param.WEIGHT, "luz")
        .build();

    /**
     * 1.4.0 — Farol: efeito ambiente. Acende em 40 ticks, repete o trecho [40, 80) até receber release e então
     * apaga de 80 a 140. As trilhas valem o mesmo nas duas bordas do loop (40 e 80), então a volta não tem salto.
     */
    static final Timeline BEACON_TIMELINE = Timeline.builder(140)
        .phase("acender", 0, 40, 30, 0)
        .phase("brilho", 40, 80, 0, 0)
        .phase("apagar", 80, 140, 0, 50)
        .track("feixe", keyframe(0, 0f), keyframe(40, 1f, Easing.EASE_OUT), keyframe(80, 1f), keyframe(140, 0f, Easing.SMOOTH))
        .track("pulso", keyframe(40, 0f), keyframe(60, 1f, Easing.SMOOTH), keyframe(80, 0f, Easing.SMOOTH))
        // 1.5.0 — névoa do sustain: sobe ao acender, fica igual em 40 e 80 (loop e release sem salto), some ao apagar.
        .track("nevoa", keyframe(0, 0f), keyframe(40, .55f, Easing.SMOOTH), keyframe(80, .55f), keyframe(140, 0f, Easing.SMOOTH))
        .marker("pico", 60)
        .marker("apagar", 80)
        .sustain(40, 80)
        .build();

    /** 1.5.0 — névoa âmbar em volta do farol enquanto ele está aceso; a densidade vem da trilha "nevoa". */
    static final Atmosphere BEACON_ATMOSPHERE = Atmosphere.builder()
        .fog(0xffe0a050, .55f, 6, 48)
        .ambient(0xffffc080, .08f)
        .track(Atmosphere.Param.FOG_DENSITY, "nevoa")
        .build();

    private final Map<UUID, Effects.Handle> last = new HashMap<>();
    private final Map<UUID, Effects.Handle> beacons = new HashMap<>();

    public Sample(net.neoforged.bus.api.IEventBus bus) {
        dev.calamidade_awakening.api.v1.Addons.resources("vfxsample", Sample.class);
        Effects.register(SIGNAL, new Effects.Definition(120, 2, 0xff65e8c9,
            Effects.Shape.RINGS, Effects.Material.CRYSTAL,
            ResourceLocation.withDefaultNamespace("end_rod"), SIGNAL,
            new Effects.Impact(40, 3, .75f, Effects.ImpactStyle.MONOCHROME)));
        // Ordem exigida: register, timeline, atmosphere, lights, cues.
        Effects.timeline(SIGNAL, SIGNAL_TIMELINE);
        Effects.atmosphere(SIGNAL, SIGNAL_ATMOSPHERE);
        Effects.lights(SIGNAL, SIGNAL_LIGHT);
        dev.calamidade_awakening.api.v1.Addons.publish(SIGNAL,new dev.calamidade_awakening.api.v1.Addons.Card(
            "ability.vfxsample.signal", "Tidal Signal", "RESONANCE", 130, SIGNAL,
            java.util.List.of(new dev.calamidade_awakening.api.v1.Addons.Shot(30,new Vec3(4,2,6),62),
                new dev.calamidade_awakening.api.v1.Addons.Shot(90,new Vec3(-3,3,7),68))),
            new dev.calamidade_awakening.api.v1.Addons.Range(.5f,1f,2f),
            (player,target,power) -> Effects.start(SIGNAL,player,target.add(0,power-1,0)).isPresent());
        // Gameplay no marcador "impacto" (tick 40, o mesmo do impact frame): dano leve e empurrão.
        Effects.cue(SIGNAL, "impacto", Sample::impact);

        Effects.register(BEACON, new Effects.Definition(140, 3, 0xffffd27a,
            Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(256));
        Effects.timeline(BEACON, BEACON_TIMELINE);
        Effects.atmosphere(BEACON, BEACON_ATMOSPHERE);
        // "pico" fica dentro do loop: toca a cada volta. "apagar" é o início do release: toca uma vez.
        Effects.cue(BEACON, "pico", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, .7f));
        Effects.cue(BEACON, "apagar", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 1f));
        Effects.cue(BEACON, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1f));

        // 1.5.0 — receitas de docs/ai/receitas/, cada uma um efeito real: /vfxsample receita <nome>.
        // 1.7.0 — também o cajado, os pilares e o zumbi, que registram itens e blocos no barramento do mod.
        example.vfxsample.receitas.Receitas.register(bus);

        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::logout);
        NeoForge.EVENT_BUS.addListener(this::shutdown);
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vfxsample").requires(source -> source.hasPermission(2))
            .then(Commands.literal("at").executes(ctx -> play(ctx.getSource().getPlayerOrException(), false)))
            .then(Commands.literal("follow").executes(ctx -> play(ctx.getSource().getPlayerOrException(), true)))
            .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource().getPlayerOrException().getUUID()) ? 1 : 0))
            .then(Commands.literal("farol")
                .then(Commands.literal("start").executes(ctx -> beaconStart(ctx.getSource())))
                .then(Commands.literal("release").executes(ctx -> beaconRelease(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> beaconStop(ctx.getSource()))))
            .then(example.vfxsample.receitas.Receitas.comando())
            .then(example.vfxsample.receitas.Receitas.comandoZumbi()));
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

    /** Acende um farol 6 blocos à frente, no chão. Um por jogador: o anterior é encerrado. */
    private int beaconStart(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        beaconStopFor(player.getUUID());
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        Vec3 at = player.position().add(look.normalize().scale(6));
        var instance = Effects.start(BEACON, player.serverLevel(), at);
        instance.ifPresent(handle -> beacons.put(player.getUUID(), handle));
        source.sendSuccess(() -> Component.translatable(instance.isPresent()
            ? "command.vfxsample.farol.started" : "command.vfxsample.farol.failed"), false);
        return instance.isPresent() ? 1 : 0;
    }

    /** Termina o loop: o farol apaga em 60 ticks. O handle fica guardado até o próximo start ou stop. */
    private int beaconRelease(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var handle = beacons.get(source.getPlayerOrException().getUUID());
        boolean released = handle != null && handle.release();
        source.sendSuccess(() -> Component.translatable(released
            ? "command.vfxsample.farol.released" : "command.vfxsample.farol.none"), false);
        return released ? 1 : 0;
    }

    private int beaconStop(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean stopped = beaconStopFor(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> Component.translatable(stopped
            ? "command.vfxsample.farol.stopped" : "command.vfxsample.farol.none"), false);
        return stopped ? 1 : 0;
    }

    private boolean beaconStopFor(UUID player) {
        var handle = beacons.remove(player);
        return handle != null && handle.stop();
    }

    private void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player.getUUID());
            beaconStopFor(player.getUUID());
            example.vfxsample.receitas.Receitas.parar(player.getUUID());
        }
    }
    private void shutdown(ServerStoppedEvent event) {
        last.clear();
        beacons.clear();
        example.vfxsample.receitas.Receitas.limpar();
    }
}
