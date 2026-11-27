package dev.calamidade_awakening;

import dev.calamidade_awakening.api.EnginePasses;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/** Light API (0.9.0). Pure; run by FrameBudgetCheck inside checkBudget. */
final class LightCheck {
    private LightCheck() {}

    static void run() {
        builder();
        sampling();
        flicker();
        registration();
        caps();
        cookies();
        oriented();
        atlas();
        fase2();
        fase3();
    }

    /** 0.15 fase 3: quadtree atlas down to 128, priority selection with hysteresis, demand, resolution, tiles. */
    private static void fase3() {
        var A = dev.calamidade_awakening.api.CookieAtlas.class;
        var a = new dev.calamidade_awakening.api.CookieAtlas();
        java.util.List<dev.calamidade_awakening.api.CookieAtlas.Cell> cells = new java.util.ArrayList<>();
        for (int i = 0; i < 256; i++) cells.add(a.allocate(128, 100));
        require(cells.stream().allMatch(java.util.Objects::nonNull) && a.allocate(1, 1) == null && a.occupancy() == 1f,
            "256 cells of 128 fill the atlas");
        for (int i = 0; i < cells.size(); i++) for (int j = i + 1; j < cells.size(); j++) {
            var p = cells.get(i); var q = cells.get(j);
            require(p.x() + p.size() <= q.x() || q.x() + q.size() <= p.x() || p.y() + p.size() <= q.y() || q.y() + q.size() <= p.y(),
                "128 cells overlap");
        }
        for (int i = 0; i < 16; i++) a.free(cells.get(i));
        var whole = a.allocate(512, 512);
        require(whole != null && whole.size() == 512 && whole.x() == 0 && whole.y() == 0, "sixteen freed 128 cells merge back into a 512");
        a.clear();
        var s1 = a.allocate(200, 200);
        var t1 = a.allocate(100, 100);
        require(s1.size() == 256 && t1.size() == 128 && t1.big() == s1.big(), "best fit: a 128 inside the already split 512");
        require(dev.calamidade_awakening.api.CookieAtlas.cellFor(64, 64) == 128 && dev.calamidade_awakening.api.CookieAtlas.cellFor(129, 10) == 256,
            "cellFor 128/256/512");
        require(dev.calamidade_awakening.api.CookieAtlas.largeSide(dev.calamidade_awakening.api.Quality.LOW) == 512
            && dev.calamidade_awakening.api.CookieAtlas.largeSide(dev.calamidade_awakening.api.Quality.BALANCED) == 1024
            && dev.calamidade_awakening.api.CookieAtlas.largeSide(dev.calamidade_awakening.api.Quality.HIGH) == 2048,
            "large side by tier, not by the (now larger) lights cap");
        // Selection: tier first, then score; under the cap everything stays; the previous frame wins ties.
        int[] tier = {0, 0, 0, 3, 1};
        float[] score = {9, 8, 7, .001f, 5};
        boolean[] keep = dev.calamidade_awakening.api.LightBudget.select(5, tier, score, null, 2);
        require(keep[3] && keep[4] && !keep[0], "a CRITICAL reticle is kept before brighter decorative lights");
        keep = dev.calamidade_awakening.api.LightBudget.select(5, tier, score, null, 9);
        require(keep[0] && keep[1] && keep[2] && keep[3] && keep[4], "under the cap every light is kept");
        int[] same = {1, 1};
        float[] close = {1f, 1.2f};
        require(dev.calamidade_awakening.api.LightBudget.select(2, same, close, new boolean[] {true, false}, 1)[0],
            "hysteresis: the light kept last frame stays against a slightly brighter one");
        require(dev.calamidade_awakening.api.LightBudget.select(2, same, new float[] {1f, 2f}, new boolean[] {true, false}, 1)[1],
            "hysteresis is bounded: a much brighter light still wins");
        // Flicker scenario: two lights trade 10% of score every frame; the kept one never changes.
        boolean[] prev = {true, false};
        int swaps = 0;
        for (int f = 0; f < 100; f++) {
            float[] sc = f % 2 == 0 ? new float[] {1f, 1.1f} : new float[] {1.1f, 1f};
            boolean[] k = dev.calamidade_awakening.api.LightBudget.select(2, same, sc, prev, 1);
            if (k[0] != prev[0]) swaps++;
            prev = k;
        }
        require(swaps == 0, "no blinking between two similar lights: " + swaps);
        require(dev.calamidade_awakening.api.LightBudget.tier(Light.Priority.NORMAL, true) == 2
            && dev.calamidade_awakening.api.LightBudget.tier(Light.Priority.DECORATIVE, true) == 0
            && dev.calamidade_awakening.api.LightBudget.tier(Light.Priority.CRITICAL, false) == 3, "HERO makes NORMAL lights IMPORTANT");
        require(Light.point(Point.center()).build().priority() == Light.Priority.NORMAL
            && Light.point(Point.center()).priority(Light.Priority.CRITICAL).build().priority() == Light.Priority.CRITICAL, "priority opt-in");
        require(Light.MAX_PER_EFFECT == 32, "32 lights per effect");
        // Demand: from the footprint, sheet frames share the side, importance, bounds 128..2048.
        float far = dev.calamidade_awakening.api.LightBudget.footprint(5, 200, 1080, 70);
        float near = dev.calamidade_awakening.api.LightBudget.footprint(5, 6, 1080, 70);
        require(far < 60 && near > 900, "footprint: " + far + " " + near);
        require(dev.calamidade_awakening.api.LightBudget.demand(far, 1, 1, 1) == 128
            && dev.calamidade_awakening.api.LightBudget.demand(900, 1, 1, 1) == 1024 && dev.calamidade_awakening.api.LightBudget.demand(near, 1, 1, 1) == 2048
            && dev.calamidade_awakening.api.LightBudget.demand(100, 4, 4, 1) == 512
            && dev.calamidade_awakening.api.LightBudget.demand(100, 1, 1, 3) == 256
            && dev.calamidade_awakening.api.LightBudget.demand(5000, 1, 1, 3) == 2048, "demand sides");
        require(dev.calamidade_awakening.api.LightBudget.floor(3) == 256 && dev.calamidade_awakening.api.LightBudget.floor(0) == 128,
            "CRITICAL never under 256");
        // Resolution hysteresis: oscillating wishes never change the side; a steady one does, once, then cools down.
        var res = new dev.calamidade_awakening.api.LightBudget.Resolution(512);
        int changes = 0;
        for (int f = 0; f < 1000; f++) if (res.update(f % 20 < 10 ? 256 : 512)) changes++;
        require(changes == 0, "no resolution change while the wish oscillates: " + changes);
        changes = 0;
        for (int f = 0; f < 2000; f++) if (res.update(256)) { changes++; res.changed(256); }
        require(changes == 1 && res.side() == 256, "one change for a steady wish: " + changes);
        int up = -1;
        for (int f = 0; f < 2000 && up < 0; f++) if (res.update(512)) up = f;
        require(up >= dev.calamidade_awakening.api.LightBudget.Resolution.UP_FRAMES - 1, "going back up waits: " + up);
        // Screen tiles: a light in front covers some tiles, one behind none, one around the camera all.
        var proj = new org.joml.Matrix4f().perspective((float) Math.toRadians(70), 16 / 9f, .05f, 1000f);
        float[] m = new float[16];
        proj.get(m);
        int[] r = new int[4];
        require(dev.calamidade_awakening.api.LightBudget.tiles(m, 0, 0, -30, 2, r) && r[0] > 0 && r[2] < 15 && r[0] <= r[2],
            "light ahead covers a few tiles: " + java.util.Arrays.toString(r));
        require(!dev.calamidade_awakening.api.LightBudget.tiles(m, 0, 0, 30, 2, r), "light behind covers none");
        require(dev.calamidade_awakening.api.LightBudget.tiles(m, 0, 0, 0, 3, r) && r[0] == 0 && r[2] == 15 && r[3] == 8,
            "camera inside: every tile");
        float[] masks = new float[dev.calamidade_awakening.api.LightBudget.TILES * 8];
        dev.calamidade_awakening.api.LightBudget.mark(masks, new int[] {0, 0, 1, 0}, 191);
        dev.calamidade_awakening.api.LightBudget.mark(masks, new int[] {0, 0, 0, 0}, 168);
        require((int) masks[7] == ((1 << 23) | 1) && (int) masks[15] == 1 << 23 && masks[7] == (float) ((1 << 23) | 1),
            "24-bit masks are exact in a float");
        require(dev.calamidade_awakening.api.LightBudget.score(2, 1, 100) < dev.calamidade_awakening.api.LightBudget.score(2, 1, 10)
            && dev.calamidade_awakening.api.LightBudget.score(2, 5, 1) == 2, "score falls with distance, 1 coverage inside");
        // Reduction from the image's own mip chain: the drawing stays (exact texels of the lower level), cells shrink.
        int[] img = new int[512 * 256];
        for (int i = 0; i < img.length; i++) img[i] = ((i / 512) % 64 < 32) == ((i % 512) % 64 < 32) ? 0xff2040ff : 0x00000000;
        int[] sh = dev.calamidade_awakening.client.LightCookiesAccess.shrink(img, 512, 256, 1);
        require(sh[0] == 256 && sh[1] == 128 && sh[2] == 256 && sh[4] == 1 && sh[5] == 1, "512x256 sheet at 256: " + java.util.Arrays.toString(sh));
        sh = dev.calamidade_awakening.client.LightCookiesAccess.shrink(img, 512, 256, 2);
        require(sh[0] == 128 && sh[1] == 64 && sh[2] == 128 && sh[3] == 8 && sh[5] == 1, "at 128: " + java.util.Arrays.toString(sh));
        // Frames of a 4 x 2 sheet stored at 128 x 64 stay 32 texels wide; the max level keeps them apart.
        require(dev.calamidade_awakening.api.CookieAtlas.maxLod(32, 32) == 4, "frames of a reduced sheet never mix");
        require(A != null, "atlas class");
    }

    /** 0.15 fase 2: EMISSIVE, OVERRIDE, nitidez, resolução, potência de 2 e atlas grande. */
    private static void fase2() {
        require(Light.Blend.SUBTRACT.ordinal() == 2 && Light.Blend.EMISSIVE.ordinal() == 3 && Light.Blend.OVERRIDE.ordinal() == 4
            && Light.Param.COLOR.ordinal() + 1 == Light.Param.BLEND_STRENGTH.ordinal(), "enums only grow at the end");
        var c = Light.Cookie.of("ns:textures/lights/a.png");
        require(c.filter() == Light.Cookie.Filter.SMOOTH && c.sharpness() == 0 && c.lodBias() == 0
            && c.resolution() == Light.Cookie.Resolution.STANDARD, "cookie fase 2 defaults");
        require(c.pixelated().filter() == Light.Cookie.Filter.PIXELATED && c.pixelated().isPixelated(), "pixelated() = PIXELATED");
        require(c.filter(Light.Cookie.Filter.SHARP).sharpness() == .75f && c.sharpness(.4f).filter() == Light.Cookie.Filter.SHARP,
            "sharpness turns SHARP on");
        expectRejected(() -> c.lodBias(-2), "lod bias below -1");
        expectRejected(() -> c.sharpness(1.5f), "sharpness above 1");
        require(Light.Cookie.Resolution.ULTRA.side == 2048 && !c.equals(c.resolution(Light.Cookie.Resolution.HIGH)), "resolution");
        var t = Timeline.builder(40).track("forca", keyframe(0, 0f), keyframe(40, 1f)).build();
        var over = Light.projector(Point.center(0, 5, 0), 6, 6).cookie(c).blend(Light.Blend.OVERRIDE).blendStrength(.5f)
            .surfaceDetail(.3f).contrast(3).track(Light.Param.BLEND_STRENGTH, "forca").build();
        over.validate(t);
        var pr = over.projection(t.at(20), 1);
        require(Math.abs(pr.strength() - .5f) < 1e-4 && pr.detail() == .3f && pr.contrast() == 3, "strength from the track: " + pr.strength());
        require(Light.point(Point.center()).blend(Light.Blend.EMISSIVE).build().projected()
            && !Light.point(Point.center()).build().projected(), "EMISSIVE needs the projection, 0.9 lights do not");
        expectRejected(() -> Light.point(Point.center()).contrast(0), "contrast 0");
        // Potência de 2 e orçamento de resolução.
        require(dev.calamidade_awakening.api.CookieAtlas.pow2(222) == 256 && dev.calamidade_awakening.api.CookieAtlas.pow2(256) == 256,
            "pow2");
        int[] f = dev.calamidade_awakening.api.CookieAtlas.fit(222, 192, 512);
        require(f[0] == 256 && f[1] == 256, "222 x 192 stored as 256 x 256");
        f = dev.calamidade_awakening.api.CookieAtlas.fit(3000, 1500, 1024);
        require(f[0] == 1024 && f[1] == 512, "3000 x 1500 at 1024: " + f[0] + "x" + f[1]);
        require(dev.calamidade_awakening.api.CookieAtlas.largeSide(8) == 512 && dev.calamidade_awakening.api.CookieAtlas.largeSide(32) == 1024
            && dev.calamidade_awakening.api.CookieAtlas.largeSide(64) == 2048, "large budget LOW/BALANCED/HIGH");
        var big = new dev.calamidade_awakening.api.CookieAtlas(2048, 1024);
        var q1 = big.allocate(1024, 512);
        var q2 = big.allocate(1024, 1024);
        require(q1 != null && q2 != null && q1.size() == 1024 && big.allocate(2048, 2048) == null, "large atlas: quarters of 1024");
        big.clear();
        var whole = big.allocate(2048, 2048);
        require(whole != null && whole.size() == 2048 && big.allocate(64, 64) == null, "large atlas: one 2048");
        // Imagem fora de potência de 2: sem borda transparente na célula (o ladrilho não escurece nos mipmaps).
        int[] px = new int[222 * 192];
        java.util.Arrays.fill(px, 0xffffffff);
        var d = dev.calamidade_awakening.client.LightCookiesAccess.chain(px, 222, 192, 512);
        require(d[0] == 256 && d[1] == 256 && d[2] == 256 && d[3] == 0xffffffff, "non-POT resampled to fill 256 x 256: " + java.util.Arrays.toString(d));
        d = dev.calamidade_awakening.client.LightCookiesAccess.chain(new int[4096 * 16], 4096, 16, 2048);
        require(d[0] == 2048 && d[1] == 8 && d[2] == 2048, "very wide image at 2048: " + java.util.Arrays.toString(d));
    }

    /** 0.15.0: cookie validation, sheets and the frame clock. */
    private static void cookies() {
        var c = Light.Cookie.of("meumod:textures/lights/energia.png");
        require(c.mode() == Light.Cookie.Mode.MASK && c.wrap() == Light.Cookie.Wrap.CLAMP && c.frames() == 1
            && c.fallback() == Light.Cookie.Fallback.AVERAGE && c.scale() == 1 && !c.isPixelated(), "cookie defaults");
        expectRejected(() -> Light.Cookie.of("textures/x.png"), "cookie without namespace");
        expectRejected(() -> Light.Cookie.of("ns:x.jpg"), "cookie not png");
        expectRejected(() -> Light.Cookie.of("NS:x.png"), "cookie upper case");
        expectRejected(() -> c.sheet(0, 1), "sheet 0 columns");
        expectRejected(() -> c.sheet(64, 64), "sheet too many frames");
        expectRejected(() -> c.sheet(4, 4).frames(17), "frames above the grid");
        expectRejected(() -> c.scale(0), "cookie scale 0");
        var sheet = c.sheet(4, 2).fps(10);
        require(sheet.frames() == 8 && sheet.frameAt(0, Float.NaN) == 0 && sheet.frameAt(.25, Float.NaN) == 2
            && sheet.frameAt(.85, Float.NaN) == 0, "sheet loops on the clock: " + sheet.frameAt(.85, Float.NaN));
        var once = sheet.play(Light.Cookie.Play.ONCE);
        require(once.frameAt(100, Float.NaN) == 7, "ONCE holds the last frame");
        var pong = sheet.play(Light.Cookie.Play.PING_PONG);
        require(pong.frameAt(.7, Float.NaN) == 7 && pong.frameAt(1.0, Float.NaN) == 4, "PING_PONG: " + pong.frameAt(1.0, Float.NaN));
        require(sheet.frameAt(0, 5.6f) == 5 && sheet.blendFrames(true).frameAt(0, 5.6f) > 5.5f, "FRAME track wins; blend keeps the fraction");
        require(c.frameAt(10, Float.NaN) == 0, "one frame");
        require(c.equals(Light.Cookie.of("meumod:textures/lights/energia.png")) && !c.equals(c.colored()), "cookie equality");
    }

    /** 0.15.0: spots, projectors, ramps, curves and blends. */
    private static void oriented() {
        var spot = Light.spot(Point.center(0, 4, 0)).build();
        require(spot.shape() == Light.Shape.SPOT && spot.oriented() && spot.pitch() == 90 && spot.angle() == 40
            && spot.softness() == .25f && spot.radius() == 12 && spot.cookie() == null, "spot defaults");
        var proj = Light.projector(Point.center(0, 6, 0), 10, 6).cookie("ns:textures/lights/r.png").roll(30).build();
        require(proj.shape() == Light.Shape.PROJECTOR && proj.sizeX() == 10 && proj.sizeY() == 6 && proj.cookie() != null
            && proj.roll() == 30 && proj.falloff() == 1, "projector");
        expectRejected(() -> Light.point(Point.center()).cookie("ns:x.png"), "cookie on a point");
        expectRejected(() -> Light.point(Point.center()).direction(0, 45), "direction on a point");
        expectRejected(() -> Light.projector(Point.center(), 4, 4).angle(30), "angle on a projector");
        expectRejected(() -> Light.spot(Point.center()).angle(175), "angle too wide");
        expectRejected(() -> Light.spot(Point.center()).direction(0, 95), "pitch out of range");
        expectRejected(() -> Light.point(Point.center()).colorRamp(0xffffffff), "ramp of one color");
        var disc = Light.disc(Point.center(), 5).cookie("ns:textures/lights/r.png").build();
        require(disc.oriented() && !Light.disc(Point.center(), 5).build().oriented(), "disc oriented only with a cookie");
        require(Light.rampColor(new int[] {0xff000000, 0xffffffff}, .5f) == 0xff808080, "ramp midpoint");
        require(Light.rampColor(new int[] {0xffff0000, 0xff00ff00, 0xff0000ff}, 1) == 0xff0000ff, "ramp end");
        var timeline = Timeline.builder(40).track("cor", keyframe(0, 0f), keyframe(40, 1f)).track("giro", keyframe(0, 0f), keyframe(40, 90f)).build();
        var ramp = Light.spot(Point.center()).colorRamp(0xffff0000, 0xff0000ff).track(Light.Param.COLOR, "cor")
            .track(Light.Param.YAW, "giro").direction(10, 45).build();
        ramp.validate(timeline);
        var state = ramp.sample(timeline.at(40), 1, 2, 0, 1);
        require(state.color() == 0xff0000ff, "ramp read by the COLOR track: " + Integer.toHexString(state.color()));
        var pr = ramp.projection(timeline.at(40), 2);
        require(pr.yaw() == 100 && pr.pitch() == 45 && pr.frame() == -1, "yaw track adds to the base yaw: " + pr.yaw());
        var plain = Light.point(Point.center()).build();
        require(plain.attenuation() == Light.Attenuation.POWER && plain.blend() == Light.Blend.ADD
            && plain.gradientStops() == 0 && !plain.oriented(), "0.9 defaults keep the fast path");
        var grad = Light.point(Point.center()).distanceGradient(0xffffffff, 0xffff0000, 0xff000000).blend(Light.Blend.SOFT)
            .attenuation(Light.Attenuation.SMOOTH).build();
        require(grad.gradientStops() == 3 && grad.gradientStop(1) == 0xffff0000, "distance gradient stops");
        // Enums only grow at the end (binary compatibility of addons compiled against 0.14).
        require(Light.Shape.RING.ordinal() == 3 && Light.Shape.SPOT.ordinal() == 4 && Light.Param.WEIGHT.ordinal() == 10,
            "enum order kept");
    }

    /** 0.15.0: atlas cells never overlap, frames stay inside their image, textured caps by profile. */
    private static void atlas() {
        var a = new dev.calamidade_awakening.api.CookieAtlas();
        java.util.List<dev.calamidade_awakening.api.CookieAtlas.Cell> cells = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) cells.add(a.allocate(512, 300));
        for (int i = 0; i < 32; i++) cells.add(a.allocate(200, 256));
        require(cells.stream().allMatch(java.util.Objects::nonNull) && a.usedQuarters() == 64, "8 big + 32 small fill the atlas");
        require(a.allocate(10, 10) == null && a.allocate(512, 512) == null, "full atlas refuses");
        for (int i = 0; i < cells.size(); i++) for (int j = i + 1; j < cells.size(); j++) {
            var p = cells.get(i); var q = cells.get(j);
            boolean apart = p.x() + p.size() <= q.x() || q.x() + q.size() <= p.x() || p.y() + p.size() <= q.y() || q.y() + q.size() <= p.y();
            require(apart, "cells overlap: " + p + " " + q);
        }
        for (var cell : cells) require(cell.x() % cell.size() == 0 && cell.y() % cell.size() == 0, "cells aligned to their size");
        a.free(cells.get(8)); a.free(cells.get(9)); a.free(cells.get(10)); a.free(cells.get(11));
        var big = a.allocate(400, 400);
        require(big != null && big.size() == 512, "four freed quarters make a big cell again");
        float[] r = dev.calamidade_awakening.api.CookieAtlas.frameRect(new dev.calamidade_awakening.api.CookieAtlas.Cell(512, 0, 512),
            512, 256, 4, 2, 5, new float[4]);
        require(Math.abs(r[0] - (512 + 128) / 2048f) < 1e-6 && Math.abs(r[1] - 128 / 2048f) < 1e-6 && Math.abs(r[2] - 128 / 2048f) < 1e-6,
            "frame 5 of a 4 x 2 sheet");
        require(dev.calamidade_awakening.api.CookieAtlas.halvings(4096, 1024) == 3 && dev.calamidade_awakening.api.CookieAtlas.halvings(512, 512) == 0,
            "halvings");
        require(dev.calamidade_awakening.api.CookieAtlas.textured(8) == 2 && dev.calamidade_awakening.api.CookieAtlas.textured(32) == 8
            && dev.calamidade_awakening.api.CookieAtlas.textured(64) == 16 && dev.calamidade_awakening.api.CookieAtlas.textured(0) == 0,
            "textured lights per profile");
        require(dev.calamidade_awakening.api.CookieAtlas.maxLod(128, 128) == 6 && dev.calamidade_awakening.api.CookieAtlas.maxLod(512, 512) == 8,
            "max lod keeps frames apart");
        require(dev.calamidade_awakening.api.CookieAtlas.BYTES > 22_000_000L && dev.calamidade_awakening.api.CookieAtlas.BYTES < 23_000_000L,
            "atlas memory about 22 MB: " + dev.calamidade_awakening.api.CookieAtlas.BYTES);
    }

    private static void builder() {
        var p = Light.point(Point.center(0, 1, 0)).build();
        require(p.shape() == Light.Shape.POINT && p.color() == 0xffffffff && p.intensity() == 1 && p.radius() == 8
            && p.falloff() == 2 && p.flicker() == 0 && !p.animated() && p.to() == null && p.beam() == null, "point defaults");
        var s = Light.segment(Point.center(), Point.center(0, 10, 0)).build();
        require(s.shape() == Light.Shape.SEGMENT && s.radius() == 4, "segment defaults");
        expectRejected(() -> Light.segment(Point.center(), Point.center()), "equal segment ends");
        var d = Light.disc(Point.center(), 12).build();
        require(d.shape() == Light.Shape.DISC && d.radius() == 12 && d.width() == 3 && d.height() == 3, "disc defaults");
        var r = Light.ring(Point.center(), 6, 2).height(4).build();
        require(r.shape() == Light.Shape.RING && r.radius() == 6 && r.width() == 2 && r.height() == 4, "ring");
        expectRejected(() -> Light.disc(Point.center(), -1), "negative disc");
        expectRejected(() -> Light.ring(Point.center(), 6, 70), "ring too wide");
        expectRejected(() -> Light.point(Point.center()).intensity(9), "intensity too high");
        expectRejected(() -> Light.point(Point.center()).radius(200), "radius too large");
        expectRejected(() -> Light.point(Point.center()).falloff(0), "falloff too small");
        expectRejected(() -> Light.point(Point.center()).flicker(1.5f, 2), "flicker above 1");
        expectRejected(() -> Light.point(Point.center()).flicker(.5f, 30), "flicker too fast");
        expectRejected(() -> Light.point(Point.center()).height(0), "height 0");
        expectRejected(() -> Light.point(Point.center()).track(Light.Param.RADIUS, "Raio"), "bad track name");
        expectRejected(() -> Light.point(Point.center()).track(Light.Param.RADIUS, "a").track(Light.Param.RADIUS, "b"), "double track");
        var beam = Beam.builder(Point.center(), Point.center(0, 50, 0)).color(0xffffb040).build();
        var along = Light.along(beam).build();
        require(along.shape() == Light.Shape.SEGMENT && along.beam() == beam && along.from().equals(beam.from())
            && along.to().equals(beam.to()) && along.color() == 0xffffb040, "along takes the beam's ends and halo color");
        require(Light.along(beam).color(0xff00ff00).build().color() == 0xff00ff00, "explicit color wins");
        var animated = Light.ring(Point.center(), 0, 2).track(Light.Param.RADIUS, "onda").weightPhase("choque").build();
        expectRejected(() -> animated.validate(null), "animated light without timeline");
        expectRejected(() -> animated.validate(Timeline.builder(40).phase("choque", 0, 40, 0, 0).build()), "unknown track");
        expectRejected(() -> animated.validate(Timeline.builder(40).track("onda", keyframe(0, 0f)).build()), "unknown phase");
        animated.validate(Timeline.builder(40).phase("choque", 0, 40, 0, 0).track("onda", keyframe(0, 0f)).build());
    }

    private static void sampling() {
        var timeline = Timeline.builder(60)
            .phase("choque", 10, 50, 5, 5)
            .track("onda", keyframe(0, 0f), keyframe(40, 400f))
            .track("brilho", keyframe(0, 2f), keyframe(60, -1f))
            .track("subir", keyframe(0, 0f), keyframe(60, 3f))
            .build();
        var ring = Light.ring(Point.center(), 1, 2).color(0xff80c0ff).intensity(3)
            .track(Light.Param.RADIUS, "onda").track(Light.Param.INTENSITY, "brilho").track(Light.Param.Y, "subir")
            .weightPhase("choque").build();
        ring.validate(timeline);
        var mid = ring.sample(timeline.at(30), 1, 1.5, .3f, 1);
        require(mid.radius() == Light.MAX_RADIUS, "radius track (300 at tick 30) clamped to 128");
        require(ring.sample(timeline.at(4), 1, 0, 0, 1).radius() == 40 && ring.sample(timeline.at(4), 1, 0, 0, 1).weight() == 0,
            "radius from a track; weight 0 outside the phase");
        require(Math.abs(mid.weight() - 1) < 1e-6 && Math.abs(mid.intensity() - .5f) < 1e-5 && mid.y() == 1.5f
            && mid.color() == 0xff80c0ff && mid.width() == 2, "constants and tracks");
        require(ring.sample(timeline.at(59), 1, 0, 0, 1).intensity() == 0, "negative intensity clamped to 0");
        require(!ring.sample(timeline.at(59), 1, 0, 0, 1).visible(), "dark light hidden");
        require(ring.sample(timeline.at(30), Float.NaN, 0, 0, 1).weight() == 0, "NaN envelope");
        require(ring.sample(timeline.at(30), .5f, 0, 0, 1).weight() == .5f, "envelope scales the weight");
        var zero = Light.point(Point.center()).radius(0).build();
        require(!zero.sample(Timeline.empty(10).at(0), 1, 0, 0, 1).visible(), "zero radius hidden");
        require(Light.Param.FALLOFF.clamp(Float.NaN) == .25f && Light.Param.Y.clamp(Float.NaN) == 0
            && Light.Param.RADIUS.clamp(1e9f) == Light.MAX_RADIUS, "param clamp");
    }

    private static void flicker() {
        require(Light.flickerFactor(0, 6, 3.2, .4f) == 1 && Light.flickerFactor(.5f, 0, 3.2, .4f) == 1
            && Light.flickerFactor(.5f, 6, Double.NaN, .4f) == 1, "no flicker");
        float min = 1, max = 0;
        double jump = 0;
        float previous = Light.flickerFactor(.4f, 6, 0, .25f);
        for (int i = 1; i <= 4000; i++) {
            double t = i / 400.0; // 10 seconds at 400 samples per second
            float f = Light.flickerFactor(.4f, 6, t, .25f);
            min = Math.min(min, f); max = Math.max(max, f);
            jump = Math.max(jump, Math.abs(f - previous));
            previous = f;
        }
        require(min >= .6f - 1e-6 && max <= 1 + 1e-6, "flicker stays within [1 - amount, 1]");
        require(max - min > .15f, "flicker actually varies");
        require(jump < .05, "flicker is smooth (no jumps between close samples)");
        require(Light.flickerFactor(.4f, 6, 2.5, .25f) == Light.flickerFactor(.4f, 6, 2.5, .25f), "flicker deterministic");
        boolean differs = false;
        for (int i = 0; i < 20; i++) differs |= Light.flickerFactor(.4f, 6, i * .37, .1f) != Light.flickerFactor(.4f, 6, i * .37, .7f);
        require(differs, "seed changes the flicker");
        var fire = Light.point(Point.center()).intensity(2).flicker(.5f, 8).build();
        var timeline = Timeline.empty(100).at(10);
        boolean scaled = true;
        for (int i = 0; i < 50; i++) {
            double t = i * .13;
            float full = fire.sample(timeline, 1, t, .3f, 1).intensity();
            float none = fire.sample(timeline, 1, t, .3f, 0).intensity();
            float quarter = fire.sample(timeline, 1, t, .3f, .25f).intensity();
            scaled &= none == 2 && full >= 1 - 1e-5 && full <= 2 && quarter >= 2 * (1 - .125f) - 1e-5 && quarter <= 2;
        }
        require(scaled, "flicker scale (screen-effects intensity, calm mode)");
    }

    private static void registration() {
        var E = (java.util.function.Function<String, ResourceLocation>) path -> ResourceLocation.fromNamespaceAndPath("checkaddon", path);
        var definition = new Effects.Definition(100, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null);
        var fire = Light.point(Point.center(0, .6, 0)).build();
        expectRejected(() -> Effects.lights(E.apply("nowhere_light"), fire), "unknown effect");
        expectRejected(() -> Effects.lights(ResourceLocation.parse("calamidade_awakening:nexus"), fire), "internal effect");
        Effects.register(E.apply("campfire"), definition);
        expectRejected(() -> Effects.lights(E.apply("campfire")), "no lights");
        Light[] nine = new Light[Light.MAX_PER_EFFECT + 1];
        java.util.Arrays.fill(nine, fire);
        expectRejected(() -> Effects.lights(E.apply("campfire"), nine), (Light.MAX_PER_EFFECT + 1) + " lights");
        Light[] eight = new Light[8];
        java.util.Arrays.fill(eight, fire);
        Effects.lights(E.apply("campfire"), eight);
        expectRejected(() -> Effects.lights(E.apply("campfire"), fire), "duplicate lights");
        require(VisualKind.byId(VisualKind.localId("checkaddon:campfire")).lights().size() == 8, "8 lights registered");
        // A light that follows a beam needs that beam registered first, on the same effect.
        var beam = Beam.builder(Point.center(), Point.center(0, 40, 0)).build();
        var other = Beam.builder(Point.center(), Point.center(0, 40, 0)).build();
        Effects.register(E.apply("lit_pillar"), definition);
        expectRejected(() -> Effects.lights(E.apply("lit_pillar"), Light.along(beam).build()), "beam not registered yet");
        Effects.beams(E.apply("lit_pillar"), beam);
        expectRejected(() -> Effects.lights(E.apply("lit_pillar"), Light.along(other).build()), "equal but different beam");
        var follow = Light.along(beam).radius(6).build();
        Effects.lights(E.apply("lit_pillar"), follow, Light.disc(Point.center(), 5).build());
        var pillar = VisualKind.byId(VisualKind.localId("checkaddon:lit_pillar"));
        require(pillar.lights().size() == 2 && pillar.lights().get(0) == follow && pillar.beams().get(0) == beam, "lights after beams");
        // Animated light: timeline first.
        Effects.register(E.apply("shock"), definition);
        var wave = Light.ring(Point.center(), 0, 2).track(Light.Param.RADIUS, "onda").build();
        expectRejected(() -> Effects.lights(E.apply("shock"), wave), "animated light before timeline");
        Effects.timeline(E.apply("shock"), Timeline.builder(100).track("onda", keyframe(0, 0f), keyframe(50, 20f)).build());
        Effects.lights(E.apply("shock"), wave);
        Effects.cue(E.apply("shock"), 5, ctx -> {});
        var shock = VisualKind.byId(VisualKind.localId("checkaddon:shock"));
        require(shock.lights().get(0).sample(shock.clock().at(25), 1, 0, 0, 1).radius() == 10, "registered light samples its timeline");
        require(VisualKind.byId(VisualKind.localId("checkaddon:plain")).lights().isEmpty(), "effects without lights");
    }

    private static void caps() {
        require(Quality.LOW.lights == 8 && Quality.BALANCED.lights == 24 && Quality.HIGH.lights == 64,
            "tier light caps 8/24/64 (legacy; the profiles use 24/64/128/192 since fase 3)");
        var perEffect = new java.util.ArrayList<List<Integer>>();
        for (int e = 0; e < 12; e++) perEffect.add(java.util.Collections.nCopies(8, e));
        for (Quality q : Quality.values()) {
            var kept = EnginePasses.cap(perEffect, q.lights);
            require(kept.size() == q.lights && kept.get(0) == 0 && kept.get(kept.size() - 1) == (q.lights - 1) / 8,
                "lights of the most important effects first " + q);
        }
    }

    private static void expectRejected(Runnable action, String test) {
        try {
            action.run();
            throw new AssertionError(test + " accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
