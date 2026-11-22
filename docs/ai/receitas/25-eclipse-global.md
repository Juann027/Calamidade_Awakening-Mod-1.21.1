# Receita 25 — Eclipse como evento global do servidor

**Objetivo.** Um eclipse que todo o servidor vê ao mesmo tempo, em qualquer dimensão, inclusive quem entra depois: o disco cobre o sol, a coroa aparece, o mundo escurece até 15% do brilho, raios caem ao longe em volta de cada jogador. Persistente: sobrevive a reiniciar o servidor. Fica na totalidade até o release.

| | |
|---|---|
| Teste no jogo | `/vfxsample evento eclipse start` (de dia, olhe o sol), `/vfxsample evento eclipse release`, `/vfxsample evento eclipse stop`; `/calamidade world list` |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/EclipseGlobal.java` (sem shader) |
| Usa | `WorldEvents.start(id, Scope.SERVER, overworld, Options.defaults().persistent(true).priority(80))`, `WorldEvents.get`, `Sky.eclipse()` com `darken`, `Sky.bolts()`, `Atmosphere`, sustain e release, cue em todos os jogadores |
| Custo | 2 camadas no passe `sky` (o sol e os raios) e o passe de atmosfera; rede: um pacote por jogador no início, no release e a quem chega depois |

## Timeline

| Trilha ou marcador | Valores | Para quê |
|---|---|---|
| `cobrir` | 0 → 1 (400) … 1 (780) → 0 (1100) | progresso do eclipse: o disco chega ao centro do sol em 20 s |
| `raios` | 0 até 300, 0,7 no loop, 0 em 880 | atividade dos raios ao longe |
| `noite` | peso da atmosfera | céu escuro, estrelas, névoa leve |
| `totalidade` (400) | marcador | trovão distante para todos (a cada volta do loop) |
| `sustain(400, 780)` | loop | a totalidade fica até o release; o loop cabe no platô do envelope (até 72% da duração) |

**Escurecimento.** `darken(.85f)`: na totalidade o mundo fica com 15% do brilho; o motor multiplica pela fração coberta do sol à quarta potência (só escurece perto da totalidade) e aplica as regras da atmosfera: intensidade dos efeitos de tela e subida lenta com "ocultar clarões". A coroa e os raios continuam acesos.

**Dimensões.** No Overworld, o disco fica sobre o sol do jogo (que é apagado e redesenhado redondo); de noite o sol está abaixo do horizonte, como num eclipse real. No Nether e no End, que não têm sol do jogo, a camada fica num ponto fixo (azimute 0, elevação 50); no Nether, o teto esconde o céu e fica o escurecimento. Na dimensão de teste (receita 26), sobre o sol dela.

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/EclipseGlobal.java -->
```java
package example.vfxsample.receitas;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.calamidade_awakening.api.v1.WorldEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 25 — eclipse como evento global do servidor (docs/ai/receitas/25-eclipse-global.md).
 * Um efeito comum (Timeline com sustain, Sky e Atmosphere) iniciado com WorldEvents no escopo SERVER: todo jogador, em
 * qualquer dimensão, vê no próprio céu o disco cobrir o sol (no Nether e no End, num ponto fixo do céu), a coroa, os
 * raios ao longe em volta dele e o mundo escurecer até 15% do brilho; quem entra depois cai na fase certa. Persistente:
 * sobrevive a reiniciar o servidor. Fica na totalidade até o release, depois o sol volta.
 * Teste: /vfxsample evento eclipse start | release | stop (de dia, olhando o sol).
 */
public final class EclipseGlobal {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "evento_eclipse");

    /** Entrada em 20 s, totalidade em loop (19 s por volta) até o release, saída em 16 s. */
    static final Timeline TIMELINE = Timeline.builder(1100)
        .track("cobrir", keyframe(0, 0f), keyframe(400, 1f, Easing.EASE_IN_OUT), keyframe(780, 1f),
            keyframe(1100, 0f, Easing.EASE_IN_OUT))
        .track("raios", keyframe(0, 0f), keyframe(300, 0f), keyframe(400, .7f, Easing.SMOOTH), keyframe(780, .7f),
            keyframe(880, 0f, Easing.SMOOTH))
        .track("noite", keyframe(0, 0f), keyframe(400, 1f, Easing.EASE_IN), keyframe(780, 1f),
            keyframe(1100, 0f, Easing.EASE_OUT))
        .marker("totalidade", 400)
        .sustain(400, 780)   // dentro do platô do envelope (até 72% de 1100)
        .build();

    static final Sky ECLIPSE = Sky.eclipse()
        .rotation(200)
        .darken(.85f)            // 15% do brilho na totalidade
        .track(Sky.Param.PROGRESS, "cobrir")
        .build();

    static final Sky RAIOS = Sky.bolts()
        .track(Sky.Param.PROGRESS, "raios")
        .build();

    static final Atmosphere NOITE_DO_ECLIPSE = Atmosphere.builder()
        .sky(0xff020308, 0xff4a3048, .85f)
        .fog(0xff181424, .25f, 80, 256)
        .ambient(0xffb8c8ff, .15f)
        .stars(.9f)
        .track(Atmosphere.Param.WEIGHT, "noite")
        .build();

    private EclipseGlobal() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(1100, 8, 0xffeaf2ff, Effects.Shape.NONE, Effects.Material.NONE, null, null)
            .withRange(1024).withPriority(Effects.Priority.HERO));
        Effects.timeline(ID, TIMELINE);
        Effects.atmosphere(ID, NOITE_DO_ECLIPSE);
        Effects.sky(ID, ECLIPSE, RAIOS);
        // Na totalidade (a cada volta do loop), um trovão distante para todo jogador do servidor.
        Effects.cue(ID, "totalidade", ctx -> {
            for (ServerPlayer player : ctx.level().getServer().getPlayerList().getPlayers())
                player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, .7f, .6f);
        });
    }

    /** /vfxsample evento eclipse start | release | stop. */
    public static LiteralArgumentBuilder<CommandSourceStack> comando() {
        return Commands.literal("evento").then(Commands.literal("eclipse")
            .then(Commands.literal("start").executes(ctx -> {
                var source = ctx.getSource();
                var handle = WorldEvents.start(ID, WorldEvents.Scope.SERVER, source.getServer().overworld(),
                    WorldEvents.Options.defaults().persistent(true).priority(80));
                source.sendSuccess(() -> Component.translatable(handle.isPresent() ? "command.vfxsample.evento.started"
                    : "command.vfxsample.evento.refused"), true);
                return handle.isPresent() ? 1 : 0;
            }))
            .then(Commands.literal("release").executes(ctx -> {
                boolean ok = WorldEvents.get(ctx.getSource().getServer(), ID).map(WorldEvents.Handle::release).orElse(false);
                ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "command.vfxsample.evento.released"
                    : "command.vfxsample.evento.none"), true);
                return ok ? 1 : 0;
            }))
            .then(Commands.literal("stop").executes(ctx -> {
                boolean ok = WorldEvents.get(ctx.getSource().getServer(), ID).map(WorldEvents.Handle::stop).orElse(false);
                ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "command.vfxsample.evento.stopped"
                    : "command.vfxsample.evento.none"), true);
                return ok ? 1 : 0;
            })));
    }
}
```

## O que conferir no jogo

1. `start` de dia: em 20 s o disco cobre o sol; a mordida aparece só sobre o sol (o disco não é visível no céu claro).
2. Totalidade: coroa com serpentinas, estrelas, mundo a 15% do brilho, raios caindo ao longe (o terreno na frente os esconde) e um trovão; F7 "Eventos globais: 1", "Céu: 2 camadas · passe sim".
3. Outro jogador no Nether ou no End vê o evento no próprio céu ao mesmo tempo; quem faz login, troca de dimensão ou morre e renasce durante a totalidade volta direto nela.
4. Reinicie o servidor durante a totalidade: o eclipse volta na mesma fase (`/calamidade world list` mostra o tempo).
5. `release`: em 16 s o sol volta (o anel de diamante pisca na saída; com "ocultar clarões", não). `stop`: some na hora para todos.
6. Intensidade dos efeitos de tela em 0: o mundo não escurece, mas o disco, a coroa e os raios continuam (são o aspecto do céu).
