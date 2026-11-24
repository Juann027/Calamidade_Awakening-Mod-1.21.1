package dev.bossestesting.fx;

import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Backdrop;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Nebula;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.SkyBlend;
import dev.calamidade_awakening.api.v1.SkyImage;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.resources.ResourceLocation;

import static dev.bossestesting.fx.Fx.*;

/**
 * Skies of the fight: global events of AREA scope started by the Boss API for each boss phase (players within the
 * effect range see them; nothing changes in the world and everything fades out when the phase or the fight ends).
 * Textures are original panoramas and flipbooks made by tools/gerar_assets.py.
 */
final class FxSky {
    private FxSky() {}

    /** 3 s fade in, loop, 3 s fade out after the release. */
    static Timeline sustained(float peak) {
        return Timeline.builder(240)
            .sustain(60, 180).release(180)
            .track("w", Kf.of(0, 0).k(60, peak, Easing.EASE_OUT).k(180, peak).k(240, 0, Easing.EASE_IN).build())
            .track("pulse", Kf.of(0, 0).k(60, .6f).k(90, 1).k(120, .5f).k(150, .9f).k(180, .6f).k(240, 0).build())
            .build();
    }

    static void sky(ResourceLocation id, float peak) {
        Effects.register(id, def(240, 24, P1_HOT).withRange(256));
        Effects.timeline(id, sustained(peak));
    }

    static void register() {
        // ---- phase 1: industrial orange haze, smog belt, a dim swollen sun over the smoke.
        sky(SKY_P1, 1);
        Effects.atmosphere(SKY_P1, Atmosphere.builder().sky(0xff2a160c, 0xffb4561c, .55f).fog(0xff6a3a1c, .28f, 28, 190)
            .ambient(0xffffa060, .12f).exposure(.96f).track(Atmosphere.Param.WEIGHT, "w").build());
        Effects.backdrop(SKY_P1, Backdrop.panorama("bossestesting:textures/sky/industrial_p1.png").opacity(.9f).spin(.15f)
            .track(Backdrop.Param.OPACITY, "w").build());
        Effects.nebulae(SKY_P1, Nebula.band(0, 90, 9).colors(0xcc3a2214, 0x66a0501c).density(.75f).detail(4).warp(1.1f).seed(41)
            .daylight(0).horizon(2).celestial(false).spin(.6f).track(Nebula.Param.OPACITY, "w").build());
        Effects.images(SKY_P1, SkyImage.of("bossestesting:textures/sky/smog_sun.png").direction(200, 24).size(22).brightness(1.4f)
            .blend(SkyBlend.LUMINOUS).track(SkyImage.Param.OPACITY, "w").build());

        // ---- phase 1, fury: the smoke catches fire, distant orange lightning.
        sky(SKY_P1_FURY, 1);
        Effects.atmosphere(SKY_P1_FURY, Atmosphere.builder().sky(0xff2a0a06, 0xffc0401a, .65f).fog(0xff5a1c0c, .34f, 22, 160)
            .ambient(0xffff6a3a, .18f).exposure(.92f).track(Atmosphere.Param.WEIGHT, "w").build());
        Effects.backdrop(SKY_P1_FURY, Backdrop.panorama("bossestesting:textures/sky/industrial_p1.png").opacity(.95f).spin(.3f).tint(0xffffc0a0)
            .track(Backdrop.Param.OPACITY, "w").build());
        Effects.nebulae(SKY_P1_FURY, Nebula.band(0, 90, 11).colors(0xdd5a1a0a, 0x88ff6a1c).density(.85f).detail(4.5f).warp(1.4f).seed(43)
            .daylight(0).horizon(2).celestial(false).spin(1.1f).brightness(1.3f).track(Nebula.Param.OPACITY, "w").build());
        Effects.images(SKY_P1_FURY, SkyImage.of("bossestesting:textures/sky/smog_sun.png").direction(200, 24).size(26).brightness(2.2f)
            .tint(0xffff8060).blend(SkyBlend.LUMINOUS).track(SkyImage.Param.OPACITY, "w").build());
        Effects.sky(SKY_P1_FURY, Sky.bolts().color(0xffffe0b0, 0xffff6a20).progress(.35f).track(Sky.Param.WEIGHT, "w").build());

        // ---- transformation: darkness, a red rift and pulsing orange storms (the TRANSFORM effect adds the pulses).
        sky(SKY_TRANSFORM, 1);
        Effects.atmosphere(SKY_TRANSFORM, Atmosphere.builder().sky(0xff050203, 0xff2a0a06, .8f).fog(0xff100404, .45f, 12, 90)
            .ambient(0xff6a1a10, .22f).exposure(.7f).track(Atmosphere.Param.WEIGHT, "w").build());
        Effects.backdrop(SKY_TRANSFORM, Backdrop.gradient(0xff040102, 0xff2a0806, 0xff000000).opacity(.95f).track(Backdrop.Param.OPACITY, "w").build());
        Effects.nebulae(SKY_TRANSFORM, Nebula.cloud(0, 80, 40).colors(0xaa6a0a06, 0x44ff3a10).density(.7f).warp(1.8f).detail(3).seed(7)
            .daylight(0).blend(SkyBlend.LUMINOUS).celestial(false).spin(2.5f).track(Nebula.Param.OPACITY, "pulse").build());
        Effects.sky(SKY_TRANSFORM, Sky.bolts().color(0xffffd0b0, 0xffff3a10).progress(.7f).track(Sky.Param.WEIGHT, "w").build());

        // ---- phase 2: cyan energy storm with red contrast, a slow vortex over the arena.
        sky(SKY_P2, 1);
        Effects.atmosphere(SKY_P2, Atmosphere.builder().sky(0xff02101a, 0xff0a4a5a, .62f).fog(0xff06262e, .34f, 22, 170)
            .ambient(0xff40e0ff, .14f).exposure(.86f).track(Atmosphere.Param.WEIGHT, "w").build());
        Effects.backdrop(SKY_P2, Backdrop.panorama("bossestesting:textures/sky/storm_p2.png").opacity(.95f).spin(-.4f).track(Backdrop.Param.OPACITY, "w").build());
        Effects.nebulae(SKY_P2,
            Nebula.cloud(0, 88, 34).colors(0xcc8a0a1a, 0x5530e8ff).density(.65f).warp(1.6f).detail(3.5f).seed(77).daylight(0)
                .blend(SkyBlend.LUMINOUS).celestial(false).spin(3).brightness(1.3f).track(Nebula.Param.OPACITY, "w").build(),
            Nebula.band(0, 90, 7).colors(0xdd061a20, 0x770a5a6a).density(.8f).detail(5).warp(1.2f).seed(19).daylight(0).horizon(1)
                .celestial(false).spin(-.8f).track(Nebula.Param.OPACITY, "w").build());
        Effects.images(SKY_P2, SkyImage.sheet("bossestesting:textures/sky/storm_eye.png", 4, 4).frames(16).fps(8).direction(0, 89).size(70)
            .blend(SkyBlend.LUMINOUS).brightness(1.2f).track(SkyImage.Param.OPACITY, "w").build());
        Effects.sky(SKY_P2,
            Sky.bolts().color(0xffe8ffff, 0xff30d8ff).progress(.55f).track(Sky.Param.WEIGHT, "w").build(),
            Sky.aurora().color(0xff30e8ff, 0xffff2a4a).intensity(.8f).track(Sky.Param.WEIGHT, "w").build());

        // ---- Overloaded: the storm doubles, the vortex speeds up, a red wound opens at the zenith.
        sky(SKY_OVERLOAD, 1);
        Effects.atmosphere(SKY_OVERLOAD, Atmosphere.builder().sky(0xff12040a, 0xff4a0a1a, .7f).fog(0xff1a0a12, .42f, 16, 140)
            .ambient(0xffff3a5a, .18f).exposure(.78f).track(Atmosphere.Param.WEIGHT, "w").build());
        Effects.backdrop(SKY_OVERLOAD, Backdrop.panorama("bossestesting:textures/sky/storm_p2.png").opacity(1).spin(-1.2f).tint(0xffffc8d0)
            .track(Backdrop.Param.OPACITY, "w").build());
        Effects.nebulae(SKY_OVERLOAD,
            Nebula.cloud(0, 88, 42).colors(0xeeff1a3a, 0x6640f0ff).density(.8f).warp(2).detail(3.5f).seed(78).daylight(0)
                .blend(SkyBlend.LUMINOUS).celestial(false).spin(7).brightness(1.8f).track(Nebula.Param.OPACITY, "pulse").build());
        Effects.images(SKY_OVERLOAD, SkyImage.sheet("bossestesting:textures/sky/storm_eye.png", 4, 4).frames(16).fps(16).direction(0, 89).size(88)
            .blend(SkyBlend.LUMINOUS).brightness(1.8f).tint(0xffffb0c0).track(SkyImage.Param.OPACITY, "w").build());
        Effects.sky(SKY_OVERLOAD,
            Sky.bolts().color(0xffffffff, 0xffff2a4a).progress(.95f).track(Sky.Param.WEIGHT, "w").build(),
            Sky.rift().direction(30, 70).size(80).color(0xffff2a4a, 0xff12020a).progress(.75f).track(Sky.Param.WEIGHT, "w").build(),
            Sky.aurora().color(0xffff2a4a, 0xff30e8ff).intensity(1.2f).track(Sky.Param.WEIGHT, "w").build());
    }
}
