package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 6 — efeito ambiente com sustain (docs/ai/receitas/06-ambiente-sustain.md).
 * Vaga-lumes em malha de mundo (AddonLayers.world) que surgem em 40 ticks, repetem o trecho [40, 120) até o
 * release e somem em 80 ticks. O movimento tem período igual ao loop (80 ticks), então a volta não salta.
 * Teste: /vfxsample receita ambiente, depois /vfxsample receita release (ou stop).
 */
public final class AmbienteVagalumes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_ambiente");

    static final int LOOP_INICIO = 40, LOOP_FIM = 120;
    static final int VAGALUMES = 48;

    static final Timeline TIMELINE = Timeline.builder(200)
        .phase("surgir", 0, LOOP_INICIO, 30, 0)
        .phase("sumir", LOOP_FIM, 200, 0, 70)
        // Mesmo valor em 40, 120 e no início da saída: loop e release sem salto.
        .track("densidade", keyframe(0, 0f), keyframe(LOOP_INICIO, 1f, Easing.SMOOTH), keyframe(LOOP_FIM, 1f),
            keyframe(200, 0f, Easing.SMOOTH))
        .track("pulso", keyframe(LOOP_INICIO, 0f), keyframe(80, 1f, Easing.SMOOTH), keyframe(LOOP_FIM, 0f, Easing.SMOOTH))
        .marker("pulso", 80)      // dentro do loop: o cue toca a cada volta
        .marker("sumir", LOOP_FIM) // início da saída: toca uma vez, no release
        .sustain(LOOP_INICIO, LOOP_FIM)
        .build();

    private AmbienteVagalumes() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(200, 6, 0xffd8ff70, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "pulso", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, .8f, 1.6f));
        Effects.cue(ID, "sumir", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.AMBIENT, .8f, .7f));
    }

    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.world(ID, world -> {
                var t = world.timeline();
                float densidade = t.value("densidade");
                if (densidade <= .001f) return;
                // Fase periódica no loop: em 40 e em 120 vale o mesmo ângulo, então a volta é contínua.
                double fase = (t.localAge() - LOOP_INICIO) * Math.TAU / (LOOP_FIM - LOOP_INICIO);
                float semente = world.time().seed();
                for (int i = 0; i < VAGALUMES && world.remainingVertices() >= 12; i++) {
                    double a = hash(i, semente) * Math.TAU, raio = 1 + 5 * hash(i + 97, semente);
                    double altura = .3 + 2.7 * hash(i + 191, semente), volta = Math.floor(hash(i + 7, semente) * 3) + 1;
                    Vec3 p = new Vec3(Math.cos(a + .3 * Math.sin(fase * volta + i)) * raio,
                        altura + .4 * Math.sin(fase * volta + i * 1.7),
                        Math.sin(a + .3 * Math.sin(fase * volta + i)) * raio);
                    float pisca = (float) (.35 + .65 * Math.max(0, Math.sin(fase * volta * 2 + i * 2.3)));
                    int cor = argb(.9f * densidade * pisca, 0xd8ff70);
                    // Dois laços cruzados formam um ponto de luz visível de quase todo ângulo.
                    Vec3 cima = new Vec3(0, .09, 0);
                    world.ribbon(p.subtract(cima), p.add(cima), new Vec3(1, 0, 0), .18, cor);
                    world.ribbon(p.subtract(cima), p.add(cima), new Vec3(0, 0, 1), .18, cor);
                }
            });
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                float perto = (float) Math.clamp(1 - post.distance() / 16, 0, 1);
                post.vignette(0xff0c1a08, t.value("densidade") * perto * .25f);
                post.bloom(t.value("densidade") * .1f + t.value("pulso") * .06f);
            });
        }

        private static double hash(int i, float seed) {
            double x = Math.sin(i * 12.9898 + seed * 78.233) * 43758.5453;
            return x - Math.floor(x);
        }

        private static int argb(float alpha, int rgb) {
            return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xffffff);
        }
    }
}
