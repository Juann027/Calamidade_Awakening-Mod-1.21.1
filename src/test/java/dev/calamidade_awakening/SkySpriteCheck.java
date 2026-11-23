package dev.calamidade_awakening;

import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.QualityProfile;
import dev.calamidade_awakening.api.SkyObjectsMath;
import dev.calamidade_awakening.api.SkySpriteMath;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.SkyBlend;
import dev.calamidade_awakening.api.v1.SkyImage;
import dev.calamidade_awakening.api.v1.SkyImpostor;
import dev.calamidade_awakening.api.v1.SkyProfile;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/**
 * Sky sprites (0.14.0, phase 4): SkyImage and SkyImpostor builder rules and sampling, registration, per-profile caps,
 * memory and upload budgets, deterministic flipbook frames in every play mode, freezing, the flash safety net, the
 * quad geometry and the picture plane, impostor orientation, view angles and blend weights, levels of detail with
 * hysteresis, draw runs, culling and the impostor descriptor with its error cases.
 */
final class SkySpriteCheck {
    private SkySpriteCheck() {}

    static void run() {
        builders();
        sampling();
        registration();
        caps();
        frames();
        flashes();
        geometry();
        impostors();
        lod();
        batching();
        descriptor();
        System.out.println("SkySpriteCheck PASS");
    }

    private static void builders() {
        expectRejected(() -> SkyImage.of("sem_namespace.png"), "texture without namespace");
        expectRejected(() -> SkyImage.of("ns:textures/x.jpg"), "not a png");
        expectRejected(() -> SkyImage.of(null), "null texture");
        expectRejected(() -> SkyImage.sheet("ns:x.png", 0, 1), "zero columns");
        expectRejected(() -> SkyImage.sheet("ns:x.png", 65, 1), "too many columns");
        expectRejected(() -> SkyImage.of("ns:x.png").size(0), "zero size");
        expectRejected(() -> SkyImage.of("ns:x.png").size(161), "size above 160");
        expectRejected(() -> SkyImage.of("ns:x.png").direction(0, 91), "elevation above 90");
        expectRejected(() -> SkyImage.of("ns:x.png").pivot(1.1f, 0), "pivot out of range");
        expectRejected(() -> SkyImage.of("ns:x.png").fps(0), "zero fps");
        expectRejected(() -> SkyImage.of("ns:x.png").fps(121), "fps above 120");
        expectRejected(() -> SkyImage.sheet("ns:x.png", 2, 2).frames(5), "more frames than cells");
        expectRejected(() -> SkyImage.sheet("ns:x.png", 2, 2).startFrame(4), "start frame past the end");
        expectRejected(() -> SkyImage.sheet("ns:x.png", 2, 2).play(SkyImage.Play.TRACK).build(), "TRACK without a FRAME track");
        expectRejected(() -> SkyImage.of("ns:x.png").track(SkyImage.Param.SIZE, "Ruim"), "bad track name");
        expectRejected(() -> SkyImage.of("ns:x.png").order(1001), "order out of range");
        SkyImage still = SkyImage.of("ns:textures/sky/a.png").build();
        require(still.frames() == 1 && !still.animatedFrames() && still.blend() == SkyBlend.NORMAL && still.order() == SkyImage.DEFAULT_ORDER
            && still.layer() == SkyImage.Layer.BEHIND && still.filter() == SkyImage.Filter.SMOOTH && still.daylight() == 0
            && !still.celestial() && Float.isNaN(still.height()), "still image defaults");
        SkyImage sheet = SkyImage.sheet("ns:textures/sky/b.png", 4, 2).frames(6).fps(10).safeFrame(2).build();
        require(sheet.frames() == 6 && sheet.columns() == 4 && sheet.rows() == 2 && sheet.safeFrame() == 2 && sheet.animatedFrames(), "sheet");
        require(SkyImage.sheet("ns:x.png", 3, 1).startFrame(1).build().safeFrame() == 1, "safe frame defaults to the start frame");
        expectRejected(() -> SkyImpostor.of("ns:nave.json"), "impostor id with .json");
        expectRejected(() -> SkyImpostor.of("nave"), "impostor id without namespace");
        expectRejected(() -> SkyImpostor.of("ns:nave").orientation(0, 91, 0), "pitch above 90");
        expectRejected(() -> SkyImpostor.of("ns:nave").play(SkyImage.Play.TRACK).build(), "impostor TRACK without FRAME");
        SkyImpostor nave = SkyImpostor.of("ns:naves/nave").build();
        require(nave.descriptorNamespace().equals("ns") && nave.descriptorPath().equals("calamidade/impostors/naves/nave.json")
            && nave.viewBlend() == SkyImpostor.ViewBlend.AUTO && nave.play() == null && Float.isNaN(nave.fps()), "impostor defaults");
    }

    private static void sampling() {
        var timeline = Timeline.builder(100).phase("voo", 0, 100, 0, 0)
            .track("az", Timeline.keyframe(0, 10), Timeline.keyframe(100, 50))
            .track("tam", Timeline.keyframe(0, 200))
            .track("q", Timeline.keyframe(0, 2.5f)).build();
        SkyImage image = SkyImage.sheet("ns:x.png", 4, 1).direction(100, 80).size(20).play(SkyImage.Play.TRACK)
            .track(SkyImage.Param.AZIMUTH, "az").track(SkyImage.Param.ELEVATION, "az").track(SkyImage.Param.SIZE, "tam")
            .track(SkyImage.Param.FRAME, "q").weightPhase("voo").build();
        image.validate(timeline);
        var s = image.sample(timeline.at(50), .5f);
        require(close(s.azimuth(), 130) && close(s.elevation(), 90) && close(s.width(), SkyImage.MAX_SIZE) && close(s.frame(), 2.5)
            && close(s.opacity(), .5), "image sampling: added angles, clamped elevation and size, frame track, weight");
        expectRejected(() -> image.validate(null), "animated image without a timeline");
        expectRejected(() -> SkyImage.of("ns:x.png").track(SkyImage.Param.ROLL, "nada").build().validate(timeline), "unknown track");
        require(Float.isNaN(SkyImage.of("ns:x.png").build().sample(Timeline.empty(1).at(0), 1).frame()), "no frame track: NaN");
        SkyImpostor nave = SkyImpostor.of("ns:nave").orientation(10, 5, 0).track(SkyImpostor.Param.YAW, "az").build();
        var n = nave.sample(timeline.at(100), 1);
        require(close(n.yaw(), 50) && close(n.pitch(), 5), "impostor sampling");
    }

    private static void registration() {
        var id = ResourceLocation.fromNamespaceAndPath("checksprites", "ceu");
        Effects.register(id, new Effects.Definition(40, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        expectRejected(() -> Effects.images(id, SkyImage.of("ns:x.png").weightPhase("nada").build()), "phase without timeline");
        expectRejected(() -> Effects.images(id), "no images");
        var img = SkyImage.of("ns:x.png").build();
        expectRejected(() -> Effects.images(id, img, img, img, img, img, img, img, img, img), "nine images");
        Effects.images(id, img);
        expectRejected(() -> Effects.images(id, img), "duplicate images");
        var imp = SkyImpostor.of("ns:nave").build();
        expectRejected(() -> Effects.impostors(id, imp, imp, imp, imp, imp), "five impostors");
        Effects.impostors(id, imp);
        var kind = VisualKind.byId(VisualKind.localId(id.toString()));
        require(kind.images().equals(List.of(img)) && kind.impostors().equals(List.of(imp)) && kind.hasSkyObjects(), "registered");
        expectRejected(() -> Effects.images(ResourceLocation.fromNamespaceAndPath("checksprites", "nada"), img), "unregistered effect");
        var state = SkyProfile.State.builder().images(img).impostors(imp).build();
        require(state.images().equals(List.of(img)) && state.impostors().equals(List.of(imp)), "profile state");
        expectRejected(() -> SkyProfile.State.builder().images(SkyImage.of("ns:x.png").weightPhase("a").build()), "animated profile image");
        expectRejected(() -> SkyProfile.State.builder().impostors(imp, imp, imp, imp, imp), "five profile impostors");
        require(SkyProfile.State.builder().build().images().isEmpty(), "profile state without images");
    }

    private static void caps() {
        var low = SkySpriteMath.caps(QualityProfile.LOW.limits());
        var bal = SkySpriteMath.caps(QualityProfile.BALANCED.limits());
        var high = SkySpriteMath.caps(QualityProfile.HIGH.limits());
        var ultra = SkySpriteMath.caps(QualityProfile.ULTRA.limits());
        require(low.images() == 8 && bal.images() == 24 && high.images() == 48 && ultra.images() == 96, "image caps");
        require(low.impostors() == 2 && bal.impostors() == 4 && high.impostors() == 8 && ultra.impostors() == 16, "impostor caps");
        require(low.vramBytes() == 64L << 20 && ultra.vramBytes() == 512L << 20 && !low.blendFrames() && bal.blendFrames(), "budgets");
        require(low.viewBlend() == SkyImpostor.ViewBlend.NEAREST && bal.viewBlend() == SkyImpostor.ViewBlend.PAIR
            && high.viewBlend() == SkyImpostor.ViewBlend.QUAD, "AUTO view blend per profile");
        require(SkySpriteMath.textureBytes(256, 256, false) == 262144, "texture bytes without mipmaps");
        long mip = SkySpriteMath.textureBytes(256, 256, true);
        require(mip > 262144 && mip < 262144 * 4 / 3 + 16, "texture bytes with mipmaps about 4/3");
        require(SkySpriteMath.textureBytes(4, 1, true) == 16 + 8 + 4, "non-square mip chain");
        require(SkySpriteMath.uploadFits(low, 0, 0, 100L << 20), "first upload of a frame always goes");
        require(!SkySpriteMath.uploadFits(low, 1, 1, 1), "LOW: one upload per frame");
        require(SkySpriteMath.uploadFits(high, 1, 4L << 20, 8L << 20) && !SkySpriteMath.uploadFits(high, 1, 10L << 20, 8L << 20),
            "HIGH: second upload only within the byte limit");
        require(!SkySpriteMath.uploadFits(ultra, 2, 0, 1), "ULTRA: at most two uploads");
        require(SkySpriteMath.halvings(4096, 2048, 4096) == 0 && SkySpriteMath.halvings(4096, 2048, 2048) == 1
            && SkySpriteMath.halvings(5000, 10, 1024) == 3, "halvings to fit the GPU limit");
        require(SkySpriteMath.mipLevels(256, 64) == 8, "mip levels");
        require(SkySpriteMath.gridLevels(256, 64, 1, 1) == 8, "single image: full chain");
        require(SkySpriteMath.gridLevels(128, 64, 2, 1) == 6, "64-pixel frames: 6 levels (cells stay whole)");
        require(SkySpriteMath.gridLevels(72, 36, 2, 1) == 2, "36-pixel frames: 2 levels");
        require(SkySpriteMath.gridLevels(100, 30, 3, 1) == 0, "grid that does not divide the image: no mipmaps");
        require(SkySpriteMath.textureBytes(4, 4, 1) == 64 + 16 && SkySpriteMath.textureBytes(4, 4, 0) == 64, "bytes with a level cap");
        var test = SkySpriteMath.caps(FrameLimits.unlimited());
        require(test.images() == 96, "test profile lifts the counts");
    }

    private static void frames() {
        var L = SkyImage.Play.LOOP;
        require(SkySpriteMath.frame(L, 1, 12, 0, 99, Float.NaN, true, false, 0).equals(SkySpriteMath.Pick.of(0)), "one frame");
        require(SkySpriteMath.frame(L, 4, 2, 0, 0, Float.NaN, false, false, 0).a() == 0, "loop t=0");
        require(SkySpriteMath.frame(L, 4, 2, 0, 1.9, Float.NaN, false, false, 0).a() == 3, "loop 3.8 → 3");
        require(SkySpriteMath.frame(L, 4, 2, 0, 2.0, Float.NaN, false, false, 0).a() == 0, "loop wraps");
        require(SkySpriteMath.frame(L, 4, 2, 1, 0, Float.NaN, false, false, 0).a() == 1, "start frame");
        require(SkySpriteMath.frame(L, 4, 2, 0, -5, Float.NaN, false, false, 0).a() == 0, "negative time counts as 0");
        var blend = SkySpriteMath.frame(L, 4, 2, 0, 1.75, Float.NaN, true, false, 0);
        require(blend.a() == 3 && blend.b() == 0 && close(blend.mix(), .5), "loop blend wraps to frame 0");
        require(SkySpriteMath.frame(L, 4, 2, 0, 1.75, Float.NaN, false, false, 0).mix() == 0, "no blend: mix 0");
        var O = SkySpriteMath.frame(SkyImage.Play.ONCE, 4, 2, 0, 10, Float.NaN, true, false, 0);
        require(O.a() == 3 && O.b() == 3 && O.mix() == 0, "once stays on the last frame");
        require(SkySpriteMath.frame(SkyImage.Play.ONCE, 4, 2, 0, 0.75, Float.NaN, false, false, 0).a() == 1, "once in the middle");
        int[] expected = {0, 1, 2, 3, 2, 1, 0, 1, 2};
        for (int i = 0; i < expected.length; i++)
            require(SkySpriteMath.frame(SkyImage.Play.PING_PONG, 4, 1, 0, i + .01, Float.NaN, false, false, 0).a() == expected[i],
                "ping-pong frame at " + i + " s");
        var down = SkySpriteMath.frame(SkyImage.Play.PING_PONG, 4, 1, 0, 4.5, Float.NaN, true, false, 0);
        require(down.a() == 2 && down.b() == 1 && close(down.mix(), .5), "ping-pong blend going back");
        var T = SkySpriteMath.frame(SkyImage.Play.TRACK, 4, 1, 0, 123, 2.25f, true, false, 0);
        require(T.a() == 2 && T.b() == 3 && close(T.mix(), .25), "track frame ignores the clock");
        require(SkySpriteMath.frame(SkyImage.Play.TRACK, 4, 1, 0, 0, 9f, true, false, 0).a() == 3, "track clamped");
        require(SkySpriteMath.frame(L, 8, 30, 0, 5.3, Float.NaN, true, true, 6).equals(SkySpriteMath.Pick.of(6)), "frozen shows the safe frame");
        // Determinism: the same instant gives the same frame no matter how it is reached.
        require(SkySpriteMath.frame(L, 7, 13, 2, 1234.567, Float.NaN, true, false, 0)
            .equals(SkySpriteMath.frame(L, 7, 13, 2, 1234.567, Float.NaN, true, false, 0)), "deterministic");
        float[] r = SkySpriteMath.cell(5, 4, 2, 0, 0, 64, 64);
        require(close(r[0], .25) && close(r[1], .5) && close(r[2], .5) && close(r[3], 1), "cell rectangle (row by row)");
        float[] g = SkySpriteMath.cell(0, 2, 1, 2, 2, 68, 68);
        require(close(g[0], 2 / 68.0 / 2) && close(g[2], (1 - 2 / 68.0) / 2), "cell with a gutter");
        require(SkySpriteMath.frozen(true, false, true, 1) && SkySpriteMath.frozen(true, false, false, 0)
            && !SkySpriteMath.frozen(true, false, false, 1) && !SkySpriteMath.frozen(false, false, true, 1)
            && SkySpriteMath.frozen(false, true, true, 1), "freezing rules: only marked or detected sheets");
    }

    private static void flashes() {
        float[] strobe = {0, 1, 0, 1};
        require(SkySpriteMath.flashRate(strobe, 12, SkyImage.Play.LOOP) > SkySpriteMath.FLASH_RATE, "strobe at 12 fps flashes");
        require(SkySpriteMath.flashRate(strobe, 2, SkyImage.Play.LOOP) <= SkySpriteMath.FLASH_RATE, "strobe at 2 fps: 1 flash/s");
        float[] gentle = {.40f, .43f, .46f, .43f};
        require(SkySpriteMath.flashRate(gentle, 60, SkyImage.Play.LOOP) == 0, "small changes are not flashes");
        float[] ramp = {0, .2f, .4f, .6f, .8f, 1};
        require(SkySpriteMath.flashRate(ramp, 6, SkyImage.Play.ONCE) == 0, "one-way ramp is not a flash");
        require(SkySpriteMath.flashRate(strobe, 30, SkyImage.Play.TRACK) == 0, "track has no period");
        require(close(SkySpriteMath.luminance(1, 1, 1), 1) && close(SkySpriteMath.luminance(0, 0, 0), 0), "relative luminance");
    }

    private static void geometry() {
        double[] south = SkyObjectsMath.direction(0, 0);
        double[][] p = SkySpriteMath.plane(south, SkyImage.Up.HORIZON, null, 0);
        require(close(p[1][1], 1) && close(p[0][0], -1), "upright toward south: up = +y, right = west (-x, the right hand when facing south)");
        double[][] rolled = SkySpriteMath.plane(south, SkyImage.Up.HORIZON, null, 90);
        require(close(rolled[0][1], 1), "roll 90: right turns to up");
        double[] zenith = {0, 1, 0};
        double[] up = SkySpriteMath.upright(zenith);
        require(close(up[2], -1), "at the zenith, up is north");
        double[][] path = SkySpriteMath.plane(south, SkyImage.Up.PATH, new double[] {0, 1, 0}, 0);
        require(close(path[0][1], 1), "path: right follows the motion");
        double[][] still = SkySpriteMath.plane(south, SkyImage.Up.PATH, new double[] {0, 0, 1}, 0);
        require(close(still[1][1], 1), "path without tangent motion falls back to upright");
        double[] c = SkySpriteMath.corners(south, p[0], p[1], 20, 10, .5, .5);
        double halfW = Math.toDegrees(Math.atan2(Math.abs(c[0]), c[2])), halfH = Math.toDegrees(Math.atan2(c[1], c[2]));
        require(close(halfW, 10, 1e-6) && close(halfH, 5, 1e-6), "corners: angular size");
        require(c[0] > 0 && c[1] > 0, "texture (0,0) is the top-left corner (east and up when facing south)");
        double[] e = SkySpriteMath.corners(south, p[0], p[1], 20, 20, 0, 0);
        require(close(e[0], 0, 1e-9) && close(e[1], 0, 1e-9), "pivot at the top-left corner");
        require(SkySpriteMath.motion(south, south) == null && SkySpriteMath.motion(south, zenith) != null, "motion");
        // Coplanar corners (gnomonic quad is flat).
        double[] n = cross(sub(c, 1, 0), sub(c, 3, 0));
        require(Math.abs(dot(n, sub(c, 2, 0))) < 1e-9, "the quad is flat");
    }

    private static void impostors() {
        double[] south = SkyObjectsMath.direction(0, 0);
        double[] fixed = SkySpriteMath.orientation(0, 0, 0, SkyImpostor.Heading.FIXED, south, null);
        require(close(fixed[8], 1) && close(fixed[4], 1) && close(fixed[0], 1), "identity orientation");
        // Object to the south facing south: the camera (north of it) sees its back: yaw 180.
        double[] a = SkySpriteMath.viewAngles(fixed, south);
        require(close(a[0], 180) && close(a[1], 0), "seen from behind");
        double[] facing = SkySpriteMath.orientation(180, 0, 0, SkyImpostor.Heading.FIXED, south, null);
        require(close(SkySpriteMath.viewAngles(facing, south)[0], 0, 1e-6) || close(SkySpriteMath.viewAngles(facing, south)[0], 360, 1e-6),
            "facing the camera: yaw 0");
        double[] side = SkySpriteMath.orientation(90, 0, 0, SkyImpostor.Heading.FIXED, south, null);
        require(close(side[6], -1), "yaw 90 points the front west");
        double[] fromAbove = SkySpriteMath.viewAngles(fixed, new double[] {0, -1, 0});
        require(close(fromAbove[1], 90), "object below the camera: seen from above (pitch 90)");
        double[] moving = SkySpriteMath.orientation(0, 0, 0, SkyImpostor.Heading.PATH, south, new double[] {-1, 0, 0});
        require(close(moving[6], -1) && close(moving[4], 1), "path heading: front along the motion, top up");
        double[] pathView = SkySpriteMath.viewAngles(moving, south);
        require(close(pathView[0], 90) || close(pathView[0], 270), "flying across: seen from the side");
        var near = SkySpriteMath.views(10, 0, 8, new float[] {0}, false, SkyImpostor.ViewBlend.NEAREST);
        require(near.size() == 1 && near.get(0).cell() == 0, "nearest view");
        var pair = SkySpriteMath.views(22.5, 0, 8, new float[] {0}, false, SkyImpostor.ViewBlend.PAIR);
        require(pair.size() == 2 && close(pair.get(0).weight(), .5) && pair.get(1).cell() == 1, "pair blend halfway");
        var wrap = SkySpriteMath.views(350, 0, 8, new float[] {0}, false, SkyImpostor.ViewBlend.PAIR);
        require(wrap.size() == 2 && wrap.get(0).cell() == 7 && wrap.get(1).cell() == 0, "pair wraps around 360");
        var quad = SkySpriteMath.views(22.5, 15, 8, new float[] {0, 30}, false, SkyImpostor.ViewBlend.QUAD);
        float sum = 0;
        for (var v : quad) sum += v.weight();
        require(quad.size() == 4 && close(sum, 1) && quad.get(3).cell() == 8 + 1, "quad bilinear");
        var clampPitch = SkySpriteMath.views(0, 80, 8, new float[] {0, 30}, false, SkyImpostor.ViewBlend.QUAD);
        require(clampPitch.size() == 1 && clampPitch.get(0).cell() == 8, "pitch clamped to the top row");
        var mirrored = SkySpriteMath.views(270, 0, 8, new float[] {0}, true, SkyImpostor.ViewBlend.NEAREST);
        require(mirrored.get(0).mirrored() && mirrored.get(0).cell() == 2, "mirror: 270° is 90° flipped");
        require(SkySpriteMath.storedYaws(8, true) == 5 && SkySpriteMath.storedYaws(8, false) == 8, "stored yaws");
        for (double yaw = 0; yaw < 360; yaw += 7.3) {
            float s = 0;
            for (var v : SkySpriteMath.views(yaw, 12, 16, new float[] {-30, 0, 30}, true, SkyImpostor.ViewBlend.QUAD)) s += v.weight();
            require(close(s, 1, 1e-4), "weights sum to 1 at yaw " + yaw);
        }
    }

    private static void lod() {
        int[] below = {0, 96, 40};
        var s = SkySpriteMath.Lod.START;
        for (int i = 0; i < 9; i++) s = s.step(70, below, null);
        require(s.level() == 0, "not before 10 frames");
        s = s.step(70, below, null);
        require(s.level() == 1, "down after 10 frames below 0.85 × 96");
        var t = s;
        for (int i = 0; i < 30; i++) t = t.step(100, below, null);
        require(t.level() == 1, "100 px: inside the hysteresis band, stays");
        for (int i = 0; i < 30; i++) t = t.step(i % 2 == 0 ? 70 : 120, below, null);
        require(t.level() == 1, "flickering size never switches");
        for (int i = 0; i < 10; i++) t = t.step(120, below, null);
        require(t.level() == 0, "up above 1.15 × 96");
        var u = SkySpriteMath.Lod.START;
        for (int i = 0; i < 20; i++) u = u.step(70, below, new boolean[] {true, false, false});
        require(u.level() == 0, "waits for the smaller level's texture");
        var w = SkySpriteMath.Lod.START;
        for (int i = 0; i < 40; i++) w = w.step(10, below, null);
        require(w.level() == 2, "one level at a time down to the smallest");
    }

    private static void batching() {
        var runs = SkySpriteMath.runs(new int[] {3, 3, 5, 3, 3, 3});
        require(runs.size() == 3 && runs.get(0)[1] == 2 && runs.get(1)[0] == 2 && runs.get(2)[1] == 3, "runs keep the drawing order");
        require(SkySpriteMath.runs(new int[0]).isEmpty(), "no runs");
        require(SkySpriteMath.culled(new float[] {2, 0, 0, 1, 3, 0, 0, 1, 3, 1, 0, 1, 2, 1, 0, 1}), "all right of the view: culled");
        require(!SkySpriteMath.culled(new float[] {-2, 0, 0, 1, 3, 0, 0, 1, 3, 1, 0, 1, -2, 1, 0, 1}), "straddling: drawn");
        require(SkySpriteMath.culled(new float[] {0, 0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1}), "behind the camera: culled");
    }

    private static void descriptor() {
        String ok = """
            {"format": 1, "textures": ["ns:textures/sky/nave.png"], "frame": [64, 32], "grid": [8, 4], "gutter": 2,
             "views": {"yaw": 8, "pitch": [-30, 0, 30], "mirror": false}, "frames": 1, "fps": 8, "play": "loop",
             "pivot": [0.5, 0.75],
             "lod": [{"textures": ["ns:textures/sky/nave_32.png"], "frame": [32, 16], "below": 24}]}""";
        var d = SkySpriteMath.parse(ok);
        require(d.viewsPerFrame() == 24 && d.framesPerPage() == 1 && d.levels().size() == 2 && close(d.aspect(), 2)
            && close(d.pivotY(), .75), "valid descriptor");
        require(d.locate(0, 9)[1] == 9, "cell of a view");
        require(SkySpriteMath.checkPage(d, d.levels().get(0), "x", 8 * 68, 4 * 36) == null, "page size with gutter");
        require(SkySpriteMath.checkPage(d, d.levels().get(0), "x", 512, 128) != null, "wrong page size reported");
        var anim = SkySpriteMath.parse("""
            {"format":1,"textures":["a:p0.png","a:p1.png"],"frame":[16,16],"grid":[4,4],"views":{"yaw":8},"frames":3}""");
        require(anim.framesPerPage() == 2 && anim.locate(2, 3)[0] == 1 && anim.locate(2, 3)[1] == 3 && anim.locate(1, 3)[1] == 11,
            "whole frames per page");
        var mirror = SkySpriteMath.parse("""
            {"format":1,"textures":["a:p.png"],"frame":[16,16],"grid":[5,1],"views":{"yaw":8,"mirror":true}}""");
        require(mirror.viewsPerFrame() == 5, "mirror stores half the circle");
        String[][] bad = {
            {"not json {", "invalid JSON"},
            {"[1,2]", "JSON object"},
            {"{\"format\":2}", "unknown format"},
            {"{\"format\":1}", "missing \"textures\""},
            {"{\"format\":1,\"textures\":[\"x\"],\"frame\":[1,1],\"grid\":[1,1],\"views\":{\"yaw\":1}}", "not a PNG"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[16,16],\"grid\":[2,2],\"views\":{\"yaw\":8}}", "cells"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[16,16],\"grid\":[4,4],\"views\":{\"yaw\":8},\"frames\":3}", "page(s)"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[16,16],\"grid\":[8,1],\"views\":{\"yaw\":7,\"mirror\":true}}", "even"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[16,16],\"grid\":[8,2],\"views\":{\"yaw\":8,\"pitch\":[30,0]}}", "increasing"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[16,16],\"grid\":[8,1],\"views\":{\"yaw\":8},\"play\":\"bounce\"}", "play must"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[16,16],\"grid\":[8,1],\"views\":{\"yaw\":8},\"pivot\":[2,0]}", "pivot values"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[64,64],\"grid\":[8,1],\"views\":{\"yaw\":8},\"lod\":[{\"textures\":[\"a:q.png\"],\"frame\":[32,16],\"below\":10}]}", "proportion"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[64,64],\"grid\":[8,1],\"views\":{\"yaw\":8},\"lod\":[{\"textures\":[\"a:q.png\"],\"frame\":[32,32],\"below\":48}]}", "at most the level's frame width"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[64,64],\"grid\":[8,1],\"views\":{\"yaw\":8},\"lod\":[{\"textures\":[\"a:q.png\",\"a:r.png\"],\"frame\":[32,32],\"below\":16}]}", "page(s), like"},
            {"{\"format\":1,\"textures\":[\"a:p.png\"],\"frame\":[16.5,16],\"grid\":[8,1],\"views\":{\"yaw\":8}}", "whole numbers"},
        };
        for (String[] b : bad) {
            try {
                SkySpriteMath.parse(b[0]);
                throw new AssertionError("SkySpriteCheck: invalid descriptor accepted: " + b[0]);
            } catch (SkySpriteMath.DescriptorException expected) {
                require(expected.getMessage().contains(b[1]), "descriptor message mentions \"" + b[1] + "\": " + expected.getMessage());
            }
        }
    }

    private static double[] sub(double[] c, int i, int j) {
        return new double[] {c[i * 3] - c[j * 3], c[i * 3 + 1] - c[j * 3 + 1], c[i * 3 + 2] - c[j * 3 + 2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static boolean close(double a, double b) { return close(a, b, 1e-4); }

    private static boolean close(double a, double b, double eps) { return Math.abs(a - b) <= eps; }

    private static void require(boolean ok, String what) {
        if (!ok) throw new AssertionError("SkySpriteCheck: " + what);
    }

    private static void expectRejected(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return;
        }
        throw new AssertionError("SkySpriteCheck: expected rejection: " + what);
    }
}
