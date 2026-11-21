package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;

/**
 * Receita 20 — só a cratera (docs/ai/receitas/20-cratera-simples.md), motor 0.11.0, para estudo.
 * Escavação com borda irregular e casca queimada no marcador "impacto" (tick 10), sob um clarão curto; o calor padrão
 * (200 ticks) esfria. Sem dublê, sem feixe, sem luz própria: a luz que esfria é a do motor.
 * Teste: /vfxsample receita cratera_simples (desfazer: /calamidade terrain undo)
 */
public final class CrateraSimples {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_cratera_simples");

    static final Timeline TIMELINE = Timeline.builder(240).marker("impacto", 10).build();

    static final TerrainEdit CRATERA = TerrainEdit.carve(TerrainShape.crater(7, 4).rough(1, 3))
        .commitAt("impacto")
        .burn(TerrainPalette.burnt(), 2)
        .build();

    private CrateraSimples() {}

    public static void register() {
        // Raio 12: o frustum (30 blocos) cobre a cratera de 7 com a casca.
        Effects.register(ID, new Effects.Definition(240, 12, 0xffff5a14, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(10, 5, .7f, Effects.ImpactStyle.MONOCHROME)));
        Effects.timeline(ID, TIMELINE);
        Effects.terrain(ID, CRATERA);
    }
}
