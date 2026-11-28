# Receita 27 — Chefe de demonstração: o Sentinela do Eclipse

**Objetivo.** Ensinar a Boss API (0.13.0) com um chefe pequeno: um golem de ferro vanilla, criado só por comando, recebe um controlador com três fases, barra segmentada com o nome da fase, um anchor nos olhos (capturado do modelo no cliente, estático no servidor), um feixe que sai dos olhos com dano calculado no servidor e o eclipse da receita 25 em área na fase final. Nada aparece sozinho: sem o comando, não há chefe.

| | |
|---|---|
| Teste no jogo | `/vfxsample chefe` (o golem aparece 8 blocos à frente), bata nele até 40% da vida; `/calamidade boss list`, `/calamidade boss phase @e[type=iron_golem,limit=1,sort=nearest] furia`; `/vfxsample chefe cratera on` liga a cratera opcional |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/SentinelaEclipse.java` (sem shader); a camada do golem em `SentinelaEclipse.Client`, registrada em `SampleClient` |
| Usa | `Boss.builder`, `Boss.Bar` (`segments`), fases com `enter`, `sustain`, `every`, `worldEvent(EclipseGlobal.ID, AREA)`, `invulnerable`, `label`, `color`; `transition(..., RUGIDO)` (sequência), `Trigger.afterTicks`, `healthBelow`, `death`; `Bosses.define` e `Bosses.attach`; `RigAnchors.define` e `approximate`; `Point.socket`, `Point.socketAim`, `Effects.anchorSocket`; `AnchorCapture.capture` numa `RenderLayer` |
| Custo | 1 instância de aura (luz), 1 feixe HERO a cada 3 a 5 s (1 feixe e 2 luzes), na fúria o eclipse (2 camadas de céu) e uma atmosfera; o controlador usa até 4 das 8 vagas de `maxPerBoss`; rede: um `boss_v1` por troca de fase e a quem chega, a barra vanilla só quando a vida muda 0,5% |

## Fases

| Fase | Entra | Durante | Sai |
|---|---|---|---|
| `despertar` | anel de luz que cresce (`DESPERTAR`) | invulnerável (opt-in) | 60 ticks depois |
| `combate` | aura nos olhos (`AURA`, sustain, centro preso ao anchor `olhos`) | a cada 100 ticks vira a cabeça para o jogador mais próximo e dispara `OLHAR` | vida ≤ 40%: o `RUGIDO` toca (40 ticks, impact frame) e trava a máquina |
| `furia` | céu vermelho (`FURIA`, sustain) e o eclipse da receita 25 **em área** (instância comum na posição do golem); cratera opcional | disparo a cada 60 ticks; barra vermelha | só pela morte |
| `derrota` | clarão e som (`QUEDA`) | — | o eclipse e a aura são soltos ao sair da fúria; a barra some quando o golem é removido |

## Anchor visual × autoritativo

- **Cliente:** `SentinelaEclipse.Client.Olhos` é uma `RenderLayer` do golem que não desenha nada: quando um efeito pediu o anchor (`AnchorCapture.wanted`), aplica a raiz e a cabeça do modelo (`translateAndRotate`), sobe 7 px e vai 5,5 px para a frente, e chama `AnchorCapture.capture`. O feixe e a luz saem dos olhos como a cabeça foi desenhada naquele quadro (o motor monta os feixes depois de todas as entidades). Fora da tela, a última captura acompanha o golem por 10 ticks; depois, o anchor estático.
- **Servidor:** o dano do marcador `disparo` usa `RigAnchors.approximate(golem, "olhos")` (definição estática + posição e olhar do golem no servidor). A pose do cliente nunca volta ao servidor.

## Terreno

A cratera é **desligada por padrão**. Ligada (`/vfxsample chefe cratera on`), a fúria abre uma vez uma cratera de raio 3 e profundidade 2 só com `carve` (sem `burn`, `fill` nem `structure`), dentro das regras e orçamentos da Terrain API e desfazível com `/calamidade terrain undo`. Ataques frequentes não mexem no terreno.

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/SentinelaEclipse.java -->
```java
package example.vfxsample.receitas;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Boss;
import dev.calamidade_awakening.api.v1.Bosses;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.RigAnchors;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.calamidade_awakening.api.v1.WorldEvents;
import dev.calamidade_awakening.api.v1.client.AnchorCapture;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import java.util.Comparator;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 27 — chefe de demonstração da Boss API: o Sentinela do Eclipse (docs/ai/receitas/27-chefe-sentinela.md),
 * motor 0.13.0. Um golem de ferro vanilla, criado só pelo comando, recebe um controlador de chefe com três fases:
 * <ol>
 * <li><b>despertar</b> (3 s, invulnerável): anel de luz e brilho nos olhos;</li>
 * <li><b>combate</b>: aura nos olhos (o efeito inteiro segue o anchor "olhos") e, a cada 5 s, carrega energia nos olhos
 * e dispara um feixe que sai do anchor na direção do olhar; dano no marcador "disparo", calculado no servidor com
 * {@link RigAnchors#approximate} (nunca com a pose do cliente);</li>
 * <li><b>fúria</b> (vida abaixo de 40%, depois de um rugido de 2 s que trava a máquina): o eclipse da receita 25 em
 * área, céu vermelho e disparos a cada 3 s. A cratera é opcional e desligada (/vfxsample chefe cratera on): só
 * {@code carve}, raio 3, uma vez.</li>
 * </ol>
 * Na morte: fase "derrota" (clarão e som), o eclipse é solto e a barra some. Barra segmentada com o nome da fase. No
 * cliente, uma RenderLayer do golem informa a pose real dos olhos (cabeça animada); sem ela vale o anchor estático.
 * Sem GeckoLib. Teste: /vfxsample chefe (cria o golem 8 blocos à frente).
 */
public final class SentinelaEclipse {
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("vfxsample", path); }

    public static final ResourceLocation ID = id("sentinela");
    static final ResourceLocation DESPERTAR = id("sentinela_despertar");
    static final ResourceLocation AURA = id("sentinela_aura");
    static final ResourceLocation OLHAR = id("sentinela_olhar");
    static final ResourceLocation RUGIDO = id("sentinela_rugido");
    static final ResourceLocation FURIA = id("sentinela_furia");
    static final ResourceLocation QUEDA = id("sentinela_queda");
    static final ResourceLocation CRATERA = id("sentinela_cratera");

    /** Olhos do golem: da altura dos olhos, 0,35 à frente, girando com a cabeça (anchor estático, também no servidor). */
    static final RigAnchors.Spec OLHOS = RigAnchors.Spec.head(0, .05, .35);

    /** Carga de 30 ticks nos olhos, disparo no 30, feixe até o 45, apaga até o 60. */
    static final Timeline OLHAR_TIMELINE = Timeline.builder(60)
        .track("carga", keyframe(0, 0f), keyframe(30, 1f, Easing.EASE_IN), keyframe(34, 0f, Easing.EASE_OUT))
        .track("feixe", keyframe(29, 0f), keyframe(33, 1f, Easing.EASE_OUT), keyframe(45, 1f), keyframe(60, 0f, Easing.EASE_IN))
        .marker("disparo", 31)
        .build();

    /** Do anchor "olhos" até o primeiro bloco à frente do olhar (32 blocos no máximo). */
    static final Beam FEIXE = Beam.builder(Point.socket("olhos", 0, 0, .15), Point.socketAim("olhos", 32))
        .width(.18f, 1.1f).color(0xfffff4e0, 0xffff5020).pulses(3, 2.5f, 1.2f).noise(.05f)
        .track(Beam.Param.REVEAL, "feixe").track(Beam.Param.INTENSITY, "feixe")
        .build();

    static final Light CARGA = Light.point(Point.socket("olhos", 0, 0, .2)).color(0xffff6030).radius(5).intensity(2.6f)
        .track(Light.Param.INTENSITY, "carga").build();
    static final Light LUZ_FEIXE = Light.along(FEIXE).radius(3).intensity(1.6f).build();

    /** Aura: luz nos olhos em laço (sustain), solta quando a fase acaba. */
    static final Timeline AURA_TIMELINE = Timeline.builder(120)
        .track("pulso", keyframe(0, 0f), keyframe(20, 1f), keyframe(60, .6f, Easing.SMOOTH), keyframe(100, 1f, Easing.SMOOTH),
            keyframe(120, 0f))
        .sustain(20, 100)
        .build();

    static final Timeline RUGIDO_TIMELINE = Timeline.builder(40).marker("rugido", 6).build();

    static final Timeline FURIA_TIMELINE = Timeline.builder(200)
        .track("peso", keyframe(0, 0f), keyframe(40, 1f, Easing.EASE_OUT), keyframe(160, 1f), keyframe(200, 0f))
        .sustain(40, 160)
        .build();

    static final Atmosphere CEU_VERMELHO = Atmosphere.builder()
        .sky(0xff200608, 0xff7a2010, .6f).fog(0xff3a0c08, .2f, 48, 160).ambient(0xffff8060, .2f)
        .track(Atmosphere.Param.WEIGHT, "peso").build();

    /** Cratera opcional: só remove blocos (sem burn, fill nem structure), raio 3, profundidade 2. */
    static final TerrainEdit CRATERA_EDIT = TerrainEdit.carve(TerrainShape.crater(3, 2)).commitAt("impacto").build();

    /** /vfxsample chefe cratera on|off (desligada por padrão). */
    static boolean cratera = false;

    static final Boss SENTINELA = Boss.builder(ID)
        .bar(Boss.Bar.of(Component.translatable("boss.vfxsample.sentinela")).color(Boss.BarColor.PURPLE).segments(true).range(64))
        .phase("despertar", p -> p.enter(DESPERTAR).invulnerable().label("boss.vfxsample.sentinela.despertar"))
        .phase("combate", p -> p.sustain(AURA).every(100, SentinelaEclipse::disparar).label("boss.vfxsample.sentinela.combate"))
        .phase("furia", p -> p.sustain(FURIA).worldEvent(EclipseGlobal.ID, WorldEvents.Scope.AREA).color(Boss.BarColor.RED)
            .every(60, SentinelaEclipse::disparar).onEnter(SentinelaEclipse::fendaOpcional).label("boss.vfxsample.sentinela.furia"))
        .phase("derrota", p -> p.enter(QUEDA).label("boss.vfxsample.sentinela.derrota"))
        .transition("despertar", "combate", Boss.Trigger.afterTicks(60))
        .transition("combate", "furia", Boss.Trigger.healthBelow(.4f), RUGIDO)
        .transition(Boss.ANY, "derrota", Boss.Trigger.death())
        .build();

    private SentinelaEclipse() {}

    public static void register() {
        RigAnchors.define(EntityType.IRON_GOLEM, "olhos", OLHOS);
        var nada = Effects.Shape.NONE;
        var sem = Effects.Material.NONE;
        Effects.register(DESPERTAR, new Effects.Definition(50, 6, 0xffb080ff, nada, sem, null, null));
        Effects.timeline(DESPERTAR, Timeline.builder(50).track("onda", keyframe(0, 0f), keyframe(40, 6f, Easing.EASE_OUT)).build());
        Effects.lights(DESPERTAR, Light.ring(Point.center(), 1, 1.2f).color(0xffb080ff).intensity(2.2f)
            .track(Light.Param.RADIUS, "onda").build());
        Effects.cue(DESPERTAR, 0, ctx -> som(ctx, SoundEvents.BEACON_ACTIVATE, .6f));

        Effects.register(AURA, new Effects.Definition(120, 1.5, 0xffb080ff, nada, sem, null, null).withRange(96));
        Effects.timeline(AURA, AURA_TIMELINE);
        Effects.anchorSocket(AURA, "olhos");  // o centro da instância segue os olhos
        Effects.lights(AURA, Light.point(Point.center()).color(0xffc090ff).radius(3).intensity(1.4f)
            .flicker(.15f, 1.5f).track(Light.Param.INTENSITY, "pulso").build());

        // Raio 14: o frustum (2,5 x 14 = 35 blocos) cobre o feixe de até 32.
        Effects.register(OLHAR, new Effects.Definition(60, 14, 0xffff5020, nada, sem, null, null).withRange(96)
            .withPriority(Effects.Priority.HERO));
        Effects.timeline(OLHAR, OLHAR_TIMELINE);
        Effects.beams(OLHAR, FEIXE);
        Effects.lights(OLHAR, CARGA, LUZ_FEIXE);
        Effects.cue(OLHAR, 0, ctx -> som(ctx, SoundEvents.BEACON_POWER_SELECT, 1.4f));
        Effects.cue(OLHAR, "disparo", SentinelaEclipse::acertar);

        Effects.register(RUGIDO, new Effects.Definition(40, 4, 0xffff4020, nada, sem, null, null,
            new Effects.Impact(6, 5, .6f, Effects.ImpactStyle.TINTED_FLASH)).withRange(96));
        Effects.timeline(RUGIDO, RUGIDO_TIMELINE);
        Effects.cue(RUGIDO, "rugido", ctx -> som(ctx, SoundEvents.RAVAGER_ROAR, .7f));

        Effects.register(FURIA, new Effects.Definition(200, 4, 0xffff4020, nada, sem, null, null).withRange(128));
        Effects.timeline(FURIA, FURIA_TIMELINE);
        Effects.atmosphere(FURIA, CEU_VERMELHO);

        Effects.register(QUEDA, new Effects.Definition(40, 5, 0xffffffff, nada, sem, null, null,
            new Effects.Impact(2, 4, .5f, Effects.ImpactStyle.TINTED_FLASH)).withRange(96));
        Effects.lights(QUEDA, Light.point(Point.center(0, 1.5, 0)).color(0xffffe0c0).radius(10).intensity(3f).build());
        Effects.cue(QUEDA, 0, ctx -> som(ctx, SoundEvents.IRON_GOLEM_DEATH, .6f));

        Effects.register(CRATERA, new Effects.Definition(30, 4, 0xffff8040, nada, sem, null, null,
            new Effects.Impact(4, 4, .5f, Effects.ImpactStyle.TINTED_FLASH)));
        Effects.timeline(CRATERA, Timeline.builder(30).marker("impacto", 4).build());
        Effects.terrain(CRATERA, CRATERA_EDIT);

        Bosses.define(SENTINELA);
    }

    /** Ação periódica: vira a cabeça para o jogador mais próximo (até 24 blocos) e dispara o olhar. */
    static void disparar(Boss.Context ctx) {
        LivingEntity golem = ctx.entity();
        Player alvo = ctx.level().getNearestPlayer(golem, 24);
        if (alvo == null || alvo.isSpectator()) return;
        if (golem instanceof IronGolem mob) mob.getLookControl().setLookAt(alvo, 60, 60);
        ctx.start(OLHAR);
    }

    /**
     * Servidor, no marcador "disparo": a pose aproximada dos olhos (anchor estático + cabeça no servidor) define o
     * raio; quem estiver a até 1 bloco dele leva 4 de dano. A pose capturada no cliente nunca entra aqui.
     */
    static void acertar(Effects.Context ctx) {
        if (ctx.anchor() == null) return;
        RigAnchors.Pose olhos = RigAnchors.approximate(ctx.anchor(), "olhos");
        Vec3 a = olhos.position(), b = a.add(olhos.forward().scale(32));
        for (LivingEntity alvo : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.5),
                e -> e != ctx.anchor() && e.isAlive())) {
            Vec3 c = alvo.getBoundingBox().getCenter();
            Vec3 ab = b.subtract(a);
            double t = Math.clamp(c.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1);
            if (a.add(ab.scale(t)).distanceTo(c) <= 1 + alvo.getBbWidth() / 2)
                alvo.hurt(ctx.level().damageSources().indirectMagic(ctx.anchor(), ctx.anchor()), 4);
        }
    }

    /** Fúria: uma cratera pequena e opcional (só carve) aos pés do golem, uma vez. */
    static void fendaOpcional(Boss.Context ctx) {
        if (cratera) ctx.start(CRATERA, dev.calamidade_awakening.api.v1.Placement.at(ctx.entity().position()));
    }

    private static void som(Effects.Context ctx, net.minecraft.sounds.SoundEvent som, float pitch) {
        ctx.level().playSound(null, BlockPos.containing(ctx.center()), som, SoundSource.HOSTILE, 1.2f, pitch);
    }

    /** /vfxsample chefe [cratera on|off]: cria o golem 8 blocos à frente e anexa o Sentinela. */
    public static LiteralArgumentBuilder<CommandSourceStack> comando() {
        var root = Commands.literal("boss").executes(ctx -> criar(ctx.getSource()));
        example.vfxsample.Cmd.then(root, Commands.literal("crater")
            .then(Commands.literal("on").executes(ctx -> crateraLigada(ctx.getSource(), true)))
            .then(Commands.literal("off").executes(ctx -> crateraLigada(ctx.getSource(), false))), "cratera");
        return root;
    }

    private static int criar(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var level = player.serverLevel();
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem == null) return 0;
        Vec3 at = Receitas.chao(player, 8);
        golem.moveTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
        golem.setCustomName(Component.translatable("boss.vfxsample.sentinela"));
        golem.setPersistenceRequired();
        level.addFreshEntity(golem);
        var handle = Bosses.attach(golem, ID);
        source.sendSuccess(() -> Component.translatable(handle.isPresent() ? "command.vfxsample.chefe.ok" : "command.vfxsample.chefe.recusado"), true);
        return handle.isPresent() ? 1 : 0;
    }

    private static int crateraLigada(CommandSourceStack source, boolean ligada) {
        cratera = ligada;
        source.sendSuccess(() -> Component.translatable(ligada ? "command.vfxsample.chefe.cratera.on" : "command.vfxsample.chefe.cratera.off"), true);
        return 1;
    }

    /** Só no cliente: uma RenderLayer do golem informa onde os olhos foram desenhados neste quadro. */
    public static final class Client {
        private Client() {}

        public static void layers(EntityRenderersEvent.AddLayers event) {
            IronGolemRenderer renderer = event.getRenderer(EntityType.IRON_GOLEM);
            if (renderer != null) renderer.addLayer(new Olhos(renderer));
        }

        /** Desenha nada: só captura a pose dos olhos (cabeça + 7 px acima do pivô, 5,5 px à frente) quando pedida. */
        static final class Olhos extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
            Olhos(IronGolemRenderer parent) { super(parent); }

            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, IronGolem golem, float limbSwing,
                               float limbAmount, float partial, float age, float headYaw, float headPitch) {
                if (!AnchorCapture.wanted(golem, "olhos")) return;
                var root = getParentModel().root();
                pose.pushPose();
                root.translateAndRotate(pose);
                root.getChild("head").translateAndRotate(pose);
                pose.translate(0, -7 / 16f, -5.5f / 16f);
                AnchorCapture.capture(golem, "olhos", pose.last().pose());
                pose.popPose();
            }
        }
    }
}
```
