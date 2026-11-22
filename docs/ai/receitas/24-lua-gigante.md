# Receita 24 — Lua gigante que nasce e cruza o céu

**Objetivo.** Um planeta com anéis que nasce no horizonte, sobe e cruza o céu em um minuto, mudando de fase — usando as trilhas de direção da Sky API.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita lua_gigante` (60 s) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/LuaGigante.java` (sem shader) |
| Usa | `Sky.body()` com `tilt` (anéis), trilhas `ELEVATION` e `AZIMUTH` (graus somados à direção da âncora) e `PROGRESS` (fase) |
| Custo | 1 camada no passe `sky` |

## Timeline

| Trilha | Valores | Para quê |
|---|---|---|
| `subir` | -8 → 150 graus (EASE_IN_OUT) | soma à elevação da âncora (um ponto no horizonte 4000 blocos à frente): nasce abaixo do horizonte, passa pelo zênite e desce do outro lado |
| `girar` | 0 → 50 graus | soma ao azimute: o caminho é inclinado |
| `fase` | 0,3 → 1 → 0,6 | crescente, cheia, minguante |

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/LuaGigante.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 24 — lua gigante que nasce e cruza o céu (docs/ai/receitas/24-lua-gigante.md).
 * Uma camada CELESTIAL_BODY com anéis, presa a um ponto no horizonte à frente de quem chamou; as trilhas ELEVATION e
 * AZIMUTH somam graus a essa direção: ela nasce abaixo do horizonte, sobe e cruza o céu em 60 s, enquanto a fase vai
 * de crescente a cheia.
 * Teste: /vfxsample receita lua_gigante (60 s).
 */
public final class LuaGigante {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_lua_gigante");

    static final Timeline TIMELINE = Timeline.builder(1200)
        .track("subir", keyframe(0, -8f), keyframe(1200, 150f, Easing.EASE_IN_OUT))
        .track("girar", keyframe(0, 0f), keyframe(1200, 50f, Easing.EASE_IN_OUT))
        .track("fase", keyframe(0, .3f), keyframe(600, 1f, Easing.SMOOTH), keyframe(1200, .6f, Easing.SMOOTH))
        .build();

    static final Sky LUA = Sky.body()
        .centerOffset(0, 0, 4000)
        .size(42)
        .tilt(22)
        .rotation(-30)
        .color(0xffd9b08a, 0xff8ab8ff)
        .track(Sky.Param.ELEVATION, "subir")
        .track(Sky.Param.AZIMUTH, "girar")
        .track(Sky.Param.PROGRESS, "fase")
        .build();

    private LuaGigante() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(1200, 40, 0xffd9b08a, Effects.Shape.NONE, Effects.Material.NONE, null, null)
            .withRange(1024));
        Effects.timeline(ID, TIMELINE);
        Effects.sky(ID, LUA);
    }

    static Optional<Effects.Handle> iniciar(ServerPlayer player) {
        return Effects.start(ID, player.serverLevel(), Placement.at(player.position(), player.getYRot(), 0).withCaster(player));
    }
}
```

## O que conferir no jogo

1. O planeta aparece cortado pelo horizonte e pelo terreno (só pixels de céu), à frente de onde você olhava.
2. Sobe devagar, cruza perto do zênite e desce atrás; os anéis passam na frente do planeta embaixo e atrás dele em cima.
3. A parte iluminada muda (fase), com a borda azul da atmosfera do lado da luz.
4. Ande 100 blocos: ele quase não se move (ponto a 4000 blocos).
