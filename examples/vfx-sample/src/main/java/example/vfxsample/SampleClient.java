package example.vfxsample;

import cyou.rimuru.rimulib.mc1211.client.api.FilmApi;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// Sem atributo bus: o FML escolhe o barramento pelo tipo do evento (FMLClientSetupEvent é IModBusEvent).
@EventBusSubscriber(modid="vfxsample",value=Dist.CLIENT)
public final class SampleClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        AddonLayers.world(Sample.SIGNAL,world -> {
            double phase = world.time().progress()*Math.TAU;
            for (int i=0; i<6; i++) {
                double a=phase+i*Math.TAU/6;
                Vec3 tip = new Vec3(Math.cos(a)*2.3,.22*Math.sin(2*a),Math.sin(a)*2.3);
                Vec3 edge = new Vec3(-Math.sin(a)*.25,0,Math.cos(a)*.25);
                world.triangle(tip,tip.scale(.62).add(edge),tip.scale(.62).subtract(edge),0xe6ffba68);
            }
        });
        AddonLayers.panel(Sample.SIGNAL,panel -> {
            var graphics = panel.graphics();
            var font = net.minecraft.client.Minecraft.getInstance().font;
            var text = net.minecraft.network.chat.Component.translatable("panel.vfxsample.signal");
            int width = font.width(text)+14;
            graphics.fill(5,5,width,22,0xe5192f36);
            graphics.fill(5,5,7,22,0xff65e8c9);
            graphics.drawString(font,text,10,10,0xffffffff,false);
        });
        // 1.4.0 — post guiado pela timeline: fases em vez de contas com a idade, trilha enviada ao shader.
        // O cartão já usa um passe (signal.fsh); em LOW não sobra vaga e os anéis são omitidos.
        AddonLayers.post(Sample.SIGNAL, post -> {
            var t = post.timeline();
            float impact = t.weight("impacto");
            if (t.localAge() >= t.timeline().start("impacto") - 2) post.pass("vfxsample:signal_ripple", u -> {
                u.v3("RippleCenter", post.center());
                u.f4("RippleState", (float) t.since("impacto"), impact, post.time().opacity(), (float) post.time().radius());
                u.f1("RippleFlash", post.flashAllowed() ? 1 : 0);
                post.uploadTracks(u, "Track_"); // uniform float Track_intensidade
            });
            post.bloom(t.value("intensidade") * .25f);
            if (post.flashAllowed()) post.shake(impact * .12f);
            // Breves no impacto; zoomBlur some quando o jogador desliga os efeitos de movimento.
            post.zoomBlur(impact * .35f);
            post.aberration(impact * .5f);
        });
        // 1.4.0 — câmera alinhada às fases: aproximação na formação, soco curto no impacto e travelling no eco.
        AddonLayers.film(Sample.SIGNAL, film -> {
            var t = film.timeline();
            int impact = t.start("impacto"), echo = t.end("impacto"), end = t.end("eco") - 20;
            return java.util.List.of(
                FilmApi.cut(0, impact, (s, partial, k) ->
                    FilmApi.pose(s.aroundTarget(partial, 3 - k, 1.5 + k, 5 - 2 * k), s.target(), 60f)),
                FilmApi.cut(impact, echo, (s, partial, k) ->
                    FilmApi.pose(s.aroundTarget(partial, 2, 2.5, 3.5 + k), s.target(), 52f + 14f * (float) k)),
                FilmApi.sweep(echo, end, 10, (s, partial, k) ->
                    FilmApi.pose(s.aroundTarget(partial, -4 + 8 * k, 3, 7), s.target(), 70f - 6f * (float) k)));
        });
        // 0.6.0 — evento de início em todos os clientes: um som local discreto para quem assiste de fora.
        AddonLayers.start(Sample.SIGNAL, start -> {
            if (start.caster() || start.age() > 2) return;
            start.level().playLocalSound(start.center().x, start.center().y, start.center().z,
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS,
                .8f, 1.2f, false);
        });
        beacon();
        // 1.5.0 — camadas de cliente das receitas (docs/ai/receitas/).
        example.vfxsample.receitas.Receitas.clientSetup();
        AddonLayers.hud(Sample.SIGNAL,hud -> {
            int x=hud.graphics().guiWidth()/2, y=hud.graphics().guiHeight()/2+22;
            hud.graphics().fill(x-20,y,x+20,y+2,0xaa182d36);
            hud.graphics().fill(x-20,y,x-20+(int)(40*hud.time().progress()),y+2,0xff65e8c9);
        });
    }

    /**
     * 1.4.0 — Farol: feixe vertical cuja altura segue a trilha "feixe" e um anel que pulsa com "pulso".
     * Nada aqui sabe do loop: o motor entrega a idade local, que repete [40, 80) até o release.
     */
    private static void beacon() {
        AddonLayers.world(Sample.BEACON, world -> {
            var t = world.timeline();
            float beam = t.value("feixe"), pulse = t.value("pulso");
            if (beam <= .001f) return;
            double height = 28 * beam;
            int core = argb(.85f * beam, 0xffe2a0), halo = argb(.35f * beam, 0xffc060);
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI / 3;
                Vec3 side = new Vec3(Math.cos(a), 0, Math.sin(a));
                world.ribbon(Vec3.ZERO, new Vec3(0, height, 0), side, .35, core);
                world.ribbon(Vec3.ZERO, new Vec3(0, height * .8, 0), side, .9, halo);
            }
            // Anel no chão que se abre a cada volta do loop.
            double radius = 1 + 2.5 * pulse;
            int ring = argb(.6f * (1 - pulse) * beam, 0xfff0b0);
            for (int i = 0; i < 24; i++) {
                double a = i * Math.TAU / 24, b = (i + 1) * Math.TAU / 24;
                Vec3 p = new Vec3(Math.cos(a) * radius, .05, Math.sin(a) * radius);
                Vec3 q = new Vec3(Math.cos(b) * radius, .05, Math.sin(b) * radius);
                world.triangle(p, q, q.scale(.85), ring);
                world.triangle(p, q.scale(.85), p.scale(.85), ring);
            }
        });
        AddonLayers.post(Sample.BEACON, post -> {
            var t = post.timeline();
            post.bloom(t.value("feixe") * .15f + t.value("pulso") * .08f);
            // Vinheta quente só perto do farol, e grão leve enquanto ele apaga.
            float near = (float) Math.clamp(1 - post.distance() / 24, 0, 1);
            post.vignette(0xff3a2208, t.value("feixe") * near * .25f);
            post.grain(t.weight("apagar") * .2f);
        });
    }

    private static int argb(float alpha, int rgb) {
        return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xffffff);
    }
}
