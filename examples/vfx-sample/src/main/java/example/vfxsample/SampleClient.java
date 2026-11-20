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
        // 0.6.1 — passe próprio com uniforms livres e pós-processamento por fase, dentro do orçamento do quadro.
        // O cartão já usa um passe (signal.fsh); em LOW não sobra vaga e os anéis são omitidos.
        AddonLayers.post(Sample.SIGNAL, post -> {
            float pulse = (float)Math.exp(-Math.abs(post.age() - 40) / 4.0); // pico no tick do impacto
            if (post.age() >= 36) post.pass("vfxsample:signal_ripple", u -> {
                u.v3("RippleCenter", post.center());
                u.f4("RippleState", (float)post.age(), pulse, post.time().opacity(), (float)post.time().radius());
                u.f1("RippleFlash", post.flashAllowed() ? 1 : 0);
            });
            post.bloom(pulse * .25f);
            if (post.flashAllowed()) post.shake(pulse * .12f);
        });
        // 0.6.0 — câmera própria: corte seco de aproximação e travelling lateral, substituindo a lista do cartão.
        AddonLayers.film(Sample.SIGNAL, film -> java.util.List.of(
            FilmApi.cut(0, 36, (s, partial, k) ->
                FilmApi.pose(s.aroundTarget(partial, 3 - k, 1.5 + k, 5 - 2 * k), s.target(), 60f)),
            FilmApi.sweep(36, 100, 10, (s, partial, k) ->
                FilmApi.pose(s.aroundTarget(partial, -4 + 8 * k, 3, 7), s.target(), 70f - 6f * (float) k))));
        // 0.6.0 — evento de início em todos os clientes: um som local discreto para quem assiste de fora.
        AddonLayers.start(Sample.SIGNAL, start -> {
            if (start.caster() || start.age() > 2) return;
            start.level().playLocalSound(start.center().x, start.center().y, start.center().z,
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS,
                .8f, 1.2f, false);
        });
        AddonLayers.hud(Sample.SIGNAL,hud -> {
            int x=hud.graphics().guiWidth()/2, y=hud.graphics().guiHeight()/2+22;
            hud.graphics().fill(x-20,y,x+20,y+2,0xaa182d36);
            hud.graphics().fill(x-20,y,x-20+(int)(40*hud.time().progress()),y+2,0xff65e8c9);
        });
    }
}
