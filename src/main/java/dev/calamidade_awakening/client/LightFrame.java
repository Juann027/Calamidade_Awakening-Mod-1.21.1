package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.CookieAtlas;
import dev.calamidade_awakening.api.EnginePasses;
import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.LightBudget;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual lights of the current frame (0.9.0). Resolves the lights of every drawn instance (HERO first, then by
 * weight), keeps at most {@code Quality.lights} (8/24/64) and draws them in the single engine pass "lights", queued
 * before the effect passes so the atmosphere and the effects stay on top. The pass costs one slot of the HERO budget
 * when a drawn HERO effect has lights, otherwise one of the common budget. Visual only: no light level changes.
 *
 * <p>0.15.0: spots and projectors (oriented by the instance frame, a direction or a target point), light cookies from
 * one shared atlas ({@link LightCookies}), attenuation curves, blend modes, color ramps and distance gradients. Before
 * the cap, each light is culled conservatively: its bounding sphere against the camera frustum and against the
 * distance where the game fog covers everything. Only the first {@link CookieAtlas#textured} cookie lights of the frame
 * sample their image; the others use their fallback. Conventional lights (0.9 shapes with the default curve and
 * blend) keep the 0.14 shader path, with no texture reads.
 *
 * <p>Fase 3 (adaptive light budget): up to {@value #ROWS} lights per frame (24/64/128/192 by profile) and a separate
 * cookie budget ({@link FrameLimits#cookies}: 16/48/96/128). Lights that light nothing visible (contribution under
 * {@link LightBudget#INVISIBLE}) are culled with the out-of-view ones. Over a cap, lights are kept by priority
 * ({@link Light.Priority}, HERO effects count as IMPORTANT) and visible contribution, with hysteresis
 * ({@link LightBudget#select}); cookie images go to the most important kept lights. Each cookie light tells the atlas
 * the side it needs on the screen ({@link LightBudget#demand}). With {@value LightBudget#TILED_FROM} lights or more,
 * the bounding sphere of every light is projected onto a 16 × 9 screen grid and each tile gets a bit mask of the
 * lights that reach it: the shader skips, per pixel, the lights whose rectangle does not cover its tile (still one
 * pass, the 0.14 path for conventional lights). Where one tile has more than {@value LightBudget#PIXEL_PRESSURE}
 * cookies, NORMAL and DECORATIVE cookies drop the interpolation between sheet frames (one sample instead of two).
 */
final class LightFrame {
    static final String PROGRAM = "calamidade_awakening:lights";
    static final int TEXELS = 13, ROWS = FrameLimits.MAX_LIGHTS;

    /**
     * One resolved light: positions relative to the camera and its state (weight already includes a linked beam's).
     * Oriented lights (0.15.0) also carry their projection and world axes: forward {@code dir}, image right and up.
     */
    record Resolved(Light light, Vec3 a, Vec3 b, Light.State state, float weight, Light.Projection projection,
                    Vec3 dir, Vec3 right, Vec3 up) {
        Resolved(Light light, Vec3 a, Vec3 b, Light.State state, float weight) {
            this(light, a, b, state, weight, null, null, null, null);
        }
    }

    /** Fase 3: selection data of one resolved light (identity across frames, tier, score, bounding sphere). */
    private record Pick(Resolved r, long key, int tier, float score, double cx, double cy, double cz, double radius) {}

    private final StageScene scene;
    private final DataTexture texture = new DataTexture(TEXELS, ROWS);
    private final float[] data = new float[TEXELS * 4 * ROWS];
    private final float[] rect = new float[4];
    private final LightCookies cookies = new LightCookies();
    // Fase 3: screen tiles (bit masks of the lights that reach each tile), 2 texels per tile.
    private final DataTexture tileTexture = new DataTexture(2, LightBudget.TILES);
    private final float[] masks = new float[LightBudget.TILES * LightBudget.MASK_WORDS];
    private final int[] tileLights = new int[LightBudget.TILES], tileCookies = new int[LightBudget.TILES];
    private final int[] tileRect = new int[4];
    private static final float[] VIEW_PROJ = new float[16];
    private static boolean viewProjValid, tilesAllowed = true;

    /** Fase 3: debug A/B of the screen tiles ({@code /calamidade_client lighttiles on|off}); on by default. */
    static void tilesAllowed(boolean on) { tilesAllowed = on; }
    private List<Resolved> lights = List.of();
    private List<Pick> picks = List.of();
    private java.util.Set<Long> keptBefore = new java.util.HashSet<>(), texturedBefore = new java.util.HashSet<>();
    private boolean heroNeeds, normalNeeds;
    private int requested, culled, textured, fallbacks, cookieCap, large;
    // Fase 3 details for F7: lights culled for no visible contribution, cookie lights requested (kept), cookies over the
    // budget, cookies not ready, frame blends dropped, tiles on/off, average and max lights per tile, max cookies per tile.
    private int lightsCap;
    private static float projY = 1.4281f;
    private int criticalCookies, criticalSampled;
    private int dark, cookieRequests, overBudget, notReady, blendsDropped, tiled, tileAvg10, tileMax, tileCookieMax;
    // Game terrain fog of the frame (RenderFog, recorded after every listener): the light fades with it.
    private float fogStart = 1e6f, fogEnd = 2e6f;
    private boolean fogCylinder;

    LightFrame(StageScene scene) { this.scene = scene; }

    /** The cookie atlas (cleared on reload and when leaving the world). */
    LightCookies cookies() { return cookies; }

    /** Terrain fog the game uses this frame (blocks), recorded from ViewportEvent.RenderFog. */
    void fog(float start, float end, boolean cylinder) {
        fogStart = start; fogEnd = end; fogCylinder = cylinder;
    }

    /**
     * Fase 3: view-projection of the camera-relative world for this frame (RenderLevelStageEvent), used to project the
     * lights onto the screen tiles; without it (first frame, another renderer) the pass keeps the plain loop.
     */
    static void viewProjection(org.joml.Matrix4f modelView, org.joml.Matrix4f projection) {
        new org.joml.Matrix4f(projection).mul(modelView).get(VIEW_PROJ);
        projY = Math.abs(projection.m11()) > 1e-3f ? Math.abs(projection.m11()) : projY;
        viewProjValid = true;
    }

    /** Resolves the visible lights of the plan, in priority order, capped by the quality. */
    void collect(StageScene.Plan plan, Vec3 camera, float partial) { collect(plan, camera, partial, null); }

    /**
     * Same, plus the engine lights of the terrain visuals (0.11.0): the cooling disc of every hot zone and the fire of
     * every stand-in, counted with the lights of their instance (caps and pass payment).
     */
    void collect(StageScene.Plan plan, Vec3 camera, float partial, TerrainFrame terrain) {
        heroNeeds = normalNeeds = false;
        requested = culled = dark = 0;
        cookies.pump(CookieAtlas.largeSide(plan.limits().tier()));
        var mc = Minecraft.getInstance();
        boolean calm = mc.options.hideLightningFlash().get();
        float flickerScale = ClientConfig.screenEffects() * (calm ? .25f : 1f);
        var frustum = mc.levelRenderer.getFrustum();
        List<Pick> all = new ArrayList<>();
        for (StageScene.Sample sample : plan.effects()) {
            var list = sample.kind().lights();
            var extra = terrain == null ? null : terrain.lights(sample.event().order());
            if (list.isEmpty() && extra == null) continue;
            Vec3 center = camera.add(sample.relative());
            var sampler = sample.time().timeline();
            double seconds = sample.age() / 20.0;
            boolean hero = sample.kind().hero();
            long base = sample.event().order() << 20;
            int before = all.size();
            for (int index = 0; index < list.size(); index++) {
                Light light = list.get(index);
                Light.State state = light.sample(sampler, sample.opacity(), seconds, sample.seed(), flickerScale);
                if (!state.visible()) continue;
                // 0.10.0: track offsets are in the instance frame (world axes without orientation).
                Vec3 offset = sample.turn(state.x(), state.y(), state.z());
                Vec3 a = scene.point(sample.event(), center, sample.basis(), light.from(), partial);
                if (a == null) continue;
                Vec3 b = a;
                float weight = state.weight();
                if (light.shape() == Light.Shape.SEGMENT) {
                    b = scene.point(sample.event(), center, sample.basis(), light.to(), partial);
                    if (b == null) continue;
                    Beam beam = light.beam();
                    if (beam != null) {
                        // Follows the beam: its track offsets, reveal and weight.
                        Beam.State bs = beam.sample(sampler, sample.opacity());
                        if (!bs.visible()) continue;
                        a = a.add(sample.turn(bs.fromX(), bs.fromY(), bs.fromZ()));
                        b = b.add(sample.turn(bs.toX(), bs.toY(), bs.toZ()));
                        b = a.add(b.subtract(a).scale(bs.reveal()));
                        weight *= bs.weight();
                    }
                }
                a = a.add(offset);
                b = b.add(offset);
                if (weight <= Light.ACTIVE_WEIGHT) continue;
                requested++;
                Resolved r;
                if (light.oriented()) {
                    Light.Projection projection = light.projection(sampler, seconds);
                    Basis frame;
                    if (light.shape() == Light.Shape.DISC) {
                        // A disc stays horizontal; the top of its cookie follows the instance's forward.
                        frame = Basis.of(sample.basis().yaw, 90);
                        r = axes(light, a, b, state, weight, projection, frame, null, camera);
                    } else if (light.target() != null) {
                        Vec3 t = scene.point(sample.event(), center, sample.basis(), light.target(), partial);
                        if (t == null) continue;
                        Vec3 d = t.add(offset).subtract(a);
                        if (d.lengthSqr() < 1e-8) continue;
                        frame = Basis.of(Basis.yawTowards(d.x, d.z) + projection.yaw(),
                            Basis.pitchTowards(d.x, d.y, d.z) + projection.pitch());
                        r = axes(light, a, b, state, weight, projection, frame, null, camera);
                    } else {
                        frame = Basis.of(projection.yaw(), projection.pitch());
                        r = axes(light, a, b, state, weight, projection, frame, sample, camera);
                    }
                } else if (light.projected()) {
                    // 0.15 fase 2: EMISSIVE or OVERRIDE on a point, segment, disc or ring: strength, detail.
                    r = new Resolved(light, a.subtract(camera), b.subtract(camera), state, weight,
                        light.projection(sampler, seconds), null, null, null);
                } else {
                    r = new Resolved(light, a.subtract(camera), b.subtract(camera), state, weight);
                }
                Pick pick = pick(r, base | index, LightBudget.tier(light.priority(), hero), camera, frustum);
                if (pick != null) all.add(pick);
            }
            if (extra != null) {
                requested += extra.size();
                for (int j = 0; j < extra.size(); j++) {
                    Pick pick = pick(extra.get(j), base | (0x80000 + j), hero ? 2 : 1, camera, frustum);
                    if (pick != null) all.add(pick);
                }
            }
            if (all.size() == before) continue;
            if (hero) heroNeeds = true; else normalNeeds = true;
        }
        int n = all.size(), max = plan.limits().lights();
        int[] tiers = new int[n];
        float[] scores = new float[n];
        boolean[] was = new boolean[n];
        for (int i = 0; i < n; i++) {
            Pick p = all.get(i);
            tiers[i] = p.tier(); scores[i] = p.score(); was[i] = keptBefore.contains(p.key());
        }
        boolean[] keep = LightBudget.select(n, tiers, scores, was, max);
        List<Pick> kept = new ArrayList<>(Math.min(n, max));
        List<Resolved> out = new ArrayList<>(Math.min(n, max));
        java.util.Set<Long> keys = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) if (keep[i]) {
            kept.add(all.get(i));
            out.add(all.get(i).r());
            keys.add(all.get(i).key());
        }
        keptBefore = keys;
        picks = kept;
        lights = out;
        cookieCap = plan.limits().cookies();
        lightsCap = max;
    }

    /**
     * Fase 3: culling and selection data of one light. Null (and counted) when out of view or under the fog, or when it
     * lights nothing visible.
     */
    private Pick pick(Resolved r, long key, int tier, Vec3 camera, net.minecraft.client.renderer.culling.Frustum frustum) {
        double[] sphere = bounds(r);
        if (!visible(sphere, camera, frustum)) {
            culled++;
            return null;
        }
        Light.State s = r.state();
        int c = s.color();
        float channel = Math.max(BeamFrame.red(c), Math.max(BeamFrame.green(c), BeamFrame.blue(c)));
        Light light = r.light();
        float gain;
        if (light.blend() == Light.Blend.OVERRIDE) gain = (r.projection() == null ? 1 : r.projection().strength()) * r.weight();
        else gain = s.intensity() * r.weight() * channel;
        if (light.gradientStops() > 0) gain = Math.max(gain, s.intensity() * r.weight());
        if (gain < LightBudget.INVISIBLE) {
            dark++;
            return null;
        }
        double distance = Math.sqrt(sphere[0] * sphere[0] + sphere[1] * sphere[1] + sphere[2] * sphere[2]);
        return new Pick(r, key, tier, LightBudget.score(gain, sphere[3], distance), sphere[0], sphere[1], sphere[2], sphere[3]);
    }

    /** World axes of an oriented light; {@code sample} non-null turns a local frame by the instance frame. */
    private static Resolved axes(Light light, Vec3 a, Vec3 b, Light.State state, float weight, Light.Projection p,
                                 Basis frame, StageScene.Sample sample, Vec3 camera) {
        Vec3 f = new Vec3(frame.fx, frame.fy, frame.fz), u = new Vec3(frame.ux, frame.uy, frame.uz);
        Vec3 right = new Vec3(-frame.lx, -frame.ly, -frame.lz);
        if (sample != null) {
            f = sample.turn(f.x, f.y, f.z);
            u = sample.turn(u.x, u.y, u.z);
            right = sample.turn(right.x, right.y, right.z);
        }
        double roll = Math.toRadians(p.roll());
        if (roll != 0) {
            double c = Math.cos(roll), s = Math.sin(roll);
            Vec3 r2 = right.scale(c).add(u.scale(s));
            u = u.scale(c).subtract(right.scale(s));
            right = r2;
        }
        return new Resolved(light, a.subtract(camera), b.subtract(camera), state, weight, p, f, right, u);
    }

    /**
     * Conservative culling of one light (0.15.0): its bounding sphere must touch the camera frustum and come closer
     * than the distance where the game fog hides everything. A light whose sphere is out of view lights no visible
     * surface.
     */
    private boolean visible(double[] sphere, Vec3 camera, net.minecraft.client.renderer.culling.Frustum frustum) {
        Vec3 c = new Vec3(sphere[0], sphere[1], sphere[2]);
        double radius = sphere[3];
        double distance = c.length() - radius;
        // Cylindrical fog distance is at least the euclidean one over sqrt(2).
        if (distance > 0 && (fogCylinder ? distance / Math.sqrt(2) : distance) > fogEnd) return false;
        if (frustum == null || distance <= 0) return true;
        Vec3 w = c.add(camera);
        return frustum.isVisible(new AABB(w.x - radius, w.y - radius, w.z - radius, w.x + radius, w.y + radius, w.z + radius));
    }

    /**
     * Bounding sphere of a light relative to the camera (x, y, z, radius): everything it can light is inside, so the
     * culling, the screen tiles and the shader agree.
     */
    private static double[] bounds(Resolved r) {
        Light.State s = r.state();
        Vec3 c;
        double radius;
        switch (r.light().shape()) {
            case SEGMENT -> {
                c = r.a().add(r.b()).scale(.5);
                radius = s.radius() + r.a().distanceTo(r.b()) * .5;
            }
            case DISC, RING -> {
                c = r.a();
                radius = s.radius() + s.width() + s.height();
            }
            case PROJECTOR -> {
                Light.Projection p = r.projection();
                c = r.a().add(r.dir().scale(s.radius() * .5));
                radius = Math.sqrt(s.radius() * s.radius() * .25 + p.sizeX() * p.sizeX() * .25 + p.sizeY() * p.sizeY() * .25);
            }
            default -> {
                c = r.a();
                radius = s.radius();
            }
        }
        return new double[] {c.x, c.y, c.z, radius};
    }

    /** Lights kept this frame (F7). */
    List<Resolved> lights() { return lights; }

    /** Pays for this frame's pass from the right pool. */
    EnginePasses.Pool pay(dev.calamidade_awakening.api.PassBudget budget) {
        return lights.isEmpty() ? EnginePasses.Pool.NONE : EnginePasses.pay(budget, heroNeeds, normalNeeds);
    }

    /** F7 details of the frame (also when the pass was not paid for). */
    void report(boolean drawn) {
        int kept = Math.min(lights.size(), ROWS);
        PerfOverlay.lightDetail(requested, culled, Math.max(0, requested - culled - dark - kept), drawn ? textured : 0,
            drawn ? fallbacks : 0, cookieCap, cookies.counts(), cookies.bytes(), drawn ? large : 0);
        int[] a = cookies.adaptive();
        PerfOverlay.lightBudget(new int[] {dark, drawn ? cookieRequests : 0, drawn ? overBudget : 0, drawn ? notReady : 0,
            drawn ? blendsDropped : 0, drawn ? tiled : 0, drawn ? tileAvg10 : 0, drawn ? tileMax : 0, drawn ? tileCookieMax : 0,
            a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], lightsCap, cookieCap,
            drawn ? criticalCookies : 0, drawn ? criticalSampled : 0});
    }

    /** Uploads the lights and queues the pass. Call only when the pass was paid for, before the effect passes. */
    void queue(FxApi.Frame frame) {
        int count = Math.min(lights.size(), ROWS);
        textured = fallbacks = large = 0;
        cookieRequests = overBudget = notReady = blendsDropped = criticalCookies = criticalSampled = 0;
        // Fase 3: cookie budget by priority and contribution, with hysteresis (cookies sampled in the last frame stay).
        boolean[] sampleOk = new boolean[count];
        {
            int m = 0;
            int[] idx = new int[count];
            for (int i = 0; i < count; i++) if (lights.get(i).light().cookie() != null) idx[m++] = i;
            int[] tiers = new int[m];
            float[] scores = new float[m];
            boolean[] was = new boolean[m];
            for (int j = 0; j < m; j++) {
                Pick p = picks.get(idx[j]);
                tiers[j] = p.tier(); scores[j] = p.score(); was[j] = texturedBefore.contains(p.key());
            }
            boolean[] ok = LightBudget.select(m, tiers, scores, was, cookieCap);
            java.util.Set<Long> keys = new java.util.HashSet<>();
            for (int j = 0; j < m; j++) if (ok[j]) {
                sampleOk[idx[j]] = true;
                keys.add(picks.get(idx[j]).key());
            }
            texturedBefore = keys;
            cookieRequests = m;
        }
        boolean tiles = count >= LightBudget.TILED_FROM && viewProjValid && tilesAllowed;
        tileStats(count, sampleOk, tiles);
        boolean dropBlend = tileCookieMax > LightBudget.PIXEL_PRESSURE;
        var window = Minecraft.getInstance().getWindow();
        int screenH = Math.max(1, window.getHeight());
        double fov = Math.toDegrees(2 * Math.atan(1 / projY));
        int k = 0;
        for (int i = 0; i < count; i++) {
            Resolved r = lights.get(i);
            Pick pick = picks.get(i);
            Light light = r.light();
            Light.State s = r.state();
            boolean override = light.blend() == Light.Blend.OVERRIDE;
            // OVERRIDE: the color carries the intensity only; the instance weight goes into the strength (coverage).
            float gain = override ? s.intensity() : s.intensity() * r.weight();
            int shape = light.shape().ordinal();
            int flags = 0;
            float cr = BeamFrame.red(s.color()), cg = BeamFrame.green(s.color()), cb = BeamFrame.blue(s.color());
            Light.Cookie cookie = light.cookie();
            LightCookies.Entry entry = null;
            if (cookie != null) {
                // Fase 3: the side this light needs on the screen (sheet frames share the image side).
                double dist = Math.sqrt(pick.cx() * pick.cx() + pick.cy() * pick.cy() + pick.cz() * pick.cz());
                int demand = LightBudget.demand(LightBudget.footprint(pick.radius(), dist, screenH, fov), cookie.columns(),
                    cookie.rows(), pick.tier());
                entry = cookies.get(cookie.texture(), cookie.resolution().side, demand, pick.tier());
                boolean budget = sampleOk[i];
                if (!budget) overBudget++;
                else if (!entry.ready()) notReady++;
                if (pick.tier() == 3) criticalCookies++;
                if (entry.ready() && budget) {
                    flags |= 1;
                    textured++;
                    if (pick.tier() == 3) criticalSampled++;
                } else if (entry.status() == LightCookies.Status.PENDING && budget) {
                    // First load: nothing yet (it shows within a few frames), instead of a fallback that pops.
                    gain = 0;
                } else if (cookie.fallback() == Light.Cookie.Fallback.NONE) {
                    gain = 0;
                    fallbacks++;
                } else {
                    // AVERAGE: the light without its image, with the image's average coverage (0.5 if it never loaded).
                    if (cookie.mode() == Light.Cookie.Mode.COLOR) { cr *= entry.avgR; cg *= entry.avgG; cb *= entry.avgB; }
                    else gain *= entry.avgLuma;
                    fallbacks++;
                }
                if (cookie.mode() == Light.Cookie.Mode.COLOR) flags |= 2;
                if (cookie.wrap() == Light.Cookie.Wrap.REPEAT) flags |= 4;
                flags |= 8;    // has a cookie: square aperture for a spot, even on the fallback
                // Só a luz que pediu alta resolução usa a versão grande (uma luz STANDARD do mesmo PNG fica no 512).
                if ((flags & 1) != 0 && cookie.resolution().side > 512 && entry.isLarge()) flags |= 16;
                flags |= cookie.filter().ordinal() << 5;
            }
            int stops = light.gradientStops();
            // Bits: 0-2 shape, 3-4 curve, 5-7 blend, 8-14 cookie flags, 15-17 gradient stops (all exact in a float).
            int code = shape | light.attenuation().ordinal() << 3 | light.blend().ordinal() << 5 | flags << 8 | stops << 15;
            // t0, t1, t2: same layout as 0.9 for the conventional shapes.
            k = put(k, (float) r.a().x, (float) r.a().y, (float) r.a().z, s.radius());
            Vec3 second = r.dir() != null && light.shape() != Light.Shape.DISC ? r.dir() : r.b();
            k = put(k, (float) second.x, (float) second.y, (float) second.z, s.falloff());
            float sr = cr * gain, sg = cg * gain, sb = cb * gain;
            int[] g = stops > 0 ? light.distanceGradient() : null;
            if (g != null) { sr *= BeamFrame.red(g[0]); sg *= BeamFrame.green(g[0]); sb *= BeamFrame.blue(g[0]); }
            k = put(k, sr, sg, sb, code);
            Light.Projection p = r.projection();
            float tanHalf = p == null ? 0 : (float) Math.tan(Math.toRadians(Math.clamp(p.angle(), 1, 170) * .5));
            k = put(k, s.width(), s.height(), tanHalf, p == null ? 0 : p.softness());
            if (p == null && stops == 0) {
                // Conventional light: the shader reads only t0..t3; the rest of the row stays as it was.
                k += (TEXELS - 4) * 4;
                continue;
            }
            Vec3 right = r.right() == null ? Vec3.ZERO : r.right(), up = r.up() == null ? Vec3.ZERO : r.up();
            k = put(k, (float) right.x, (float) right.y, (float) right.z, p == null ? 0 : p.sizeX() * .5f);
            k = put(k, (float) up.x, (float) up.y, (float) up.z, p == null ? 0 : p.sizeY() * .5f);
            float scale = p == null ? 1 : p.scale(), rot = p == null ? 0 : (float) Math.toRadians(p.rotation());
            k = put(k, (float) Math.cos(rot) * scale, (float) Math.sin(rot) * scale, p == null ? 0 : p.u(), p == null ? 0 : p.v());
            float mix = 0, maxLod = 0;
            if ((flags & 1) != 0) {
                float f = Math.max(0, p.frame());
                int f0 = (int) Math.floor(f);
                mix = f - f0;
                // Fase 3: many cookies on the same tile: NORMAL and DECORATIVE cookies read one frame (half the samples).
                if (dropBlend && pick.tier() < 2 && mix > 0) {
                    mix = Math.round(mix);
                    f0 += (int) mix;
                    mix = 0;
                    blendsDropped++;
                }
                int f1 = cookie.play() == Light.Cookie.Play.LOOP ? (f0 + 1) % cookie.frames() : Math.min(f0 + 1, cookie.frames() - 1);
                LightCookies.Version v = (flags & 16) != 0 ? entry.best() : entry.std;
                CookieAtlas.frameRect(v.cell, v.width, v.height, cookie.columns(), cookie.rows(), f0, rect);
                k = put(k, rect[0], rect[1], rect[2], rect[3]);
                CookieAtlas.frameRect(v.cell, v.width, v.height, cookie.columns(), cookie.rows(), f1, rect);
                k = put(k, rect[0], rect[1], rect[2], rect[3]);
                maxLod = CookieAtlas.maxLod((float) v.width / cookie.columns(), (float) v.height / cookie.rows());
                if (v != entry.std) large++;
            } else {
                k = put(k, 0, 0, 0, 0);
                k = put(k, 0, 0, 0, 0);
            }
            for (int j = 1; j < 4; j++) {
                float w = j == 1 ? mix : j == 2 ? maxLod : cookie == null ? 0 : cookie.sharpness();
                if (g != null && j < g.length) k = put(k, cr * gain * BeamFrame.red(g[j]), cg * gain * BeamFrame.green(g[j]),
                    cb * gain * BeamFrame.blue(g[j]), w);
                else k = put(k, 0, 0, 0, w);
            }
            // t12: blend strength (OVERRIDE: times the instance weight), surface detail, mask contrast, lod bias.
            float strength = p == null ? 1 : p.strength();
            k = put(k, override ? strength * r.weight() : strength, p == null ? 0 : p.detail(), p == null ? 1 : p.contrast(),
                cookie == null ? 0 : cookie.lodBias());
        }
        int id = texture.upload(data, count);
        int tileId = tileTexture.upload(masks, tiles ? LightBudget.TILES : 0);
        // The pixel finds its tile with the same matrix the spheres were projected with (copied: the pass runs later).
        float[] tileMatrix = VIEW_PROJ.clone();
        float tileOn = tiles ? 1 : 0;
        int atlas = cookies.texture(), largeAtlas = cookies.largeTexture();
        float start = fogStart, end = fogEnd, cylinder = fogCylinder ? 1 : 0;
        frame.pass(PROGRAM, u -> {
            u.glTexture("CaLightData", id);
            // Without the atlas the cookie sampler gets the data texture: it is never read then (no cookie flag set).
            u.glTexture("CaCookieAtlas", atlas != 0 ? atlas : id);
            u.f4("CaLightInfo", count, start, end, cylinder);
            // 0.15 fase 2: the large atlas only exists while a high-resolution cookie is resident.
            u.glTexture("CaCookieLarge", largeAtlas != 0 ? largeAtlas : atlas != 0 ? atlas : id);
            u.f4("CaCookieInfo", CookieAtlas.SIDE, atlas != 0 ? 1 : 0, largeAtlas != 0 ? 1 : 0, 0);
            // Fase 3: screen tiles (masks of the lights per tile); off below TILED_FROM lights or without the matrices.
            u.glTexture("CaLightTiles", tileId);
            u.f4("CaTileInfo", LightBudget.TILES_X, LightBudget.TILES_Y, tileOn, 0);
            float[] m = tileMatrix;
            u.f4("CaTileM0", m[0], m[1], m[2], m[3]);
            u.f4("CaTileM1", m[4], m[5], m[6], m[7]);
            u.f4("CaTileM2", m[8], m[9], m[10], m[11]);
            u.f4("CaTileM3", m[12], m[13], m[14], m[15]);
        });
    }

    /**
     * Fase 3: bit masks of the lights per screen tile and their statistics (F7: average and max lights per tile, max
     * sampled cookies per tile). A sphere the tiles find off screen is marked everywhere (the frustum kept it: never
     * lose a light to a matrix mismatch).
     */
    private void tileStats(int count, boolean[] sampled, boolean on) {
        java.util.Arrays.fill(tileLights, 0);
        java.util.Arrays.fill(tileCookies, 0);
        tiled = on ? 1 : 0;
        if (on) java.util.Arrays.fill(masks, 0);
        for (int i = 0; i < count; i++) {
            Pick p = picks.get(i);
            if (!viewProjValid || !LightBudget.tiles(VIEW_PROJ, p.cx(), p.cy(), p.cz(), p.radius(), tileRect)) {
                tileRect[0] = 0; tileRect[1] = 0; tileRect[2] = LightBudget.TILES_X - 1; tileRect[3] = LightBudget.TILES_Y - 1;
            }
            if (on) LightBudget.mark(masks, tileRect, i);
            for (int ty = tileRect[1]; ty <= tileRect[3]; ty++) for (int tx = tileRect[0]; tx <= tileRect[2]; tx++) {
                int t = ty * LightBudget.TILES_X + tx;
                tileLights[t]++;
                if (sampled[i]) tileCookies[t]++;
            }
        }
        long sum = 0;
        tileMax = tileCookieMax = 0;
        for (int t = 0; t < LightBudget.TILES; t++) {
            sum += tileLights[t];
            tileMax = Math.max(tileMax, tileLights[t]);
            tileCookieMax = Math.max(tileCookieMax, tileCookies[t]);
        }
        tileAvg10 = (int) (sum * 10 / LightBudget.TILES);
    }

    private int put(int k, float x, float y, float z, float w) {
        data[k] = x; data[k + 1] = y; data[k + 2] = z; data[k + 3] = w;
        return k + 4;
    }
}
