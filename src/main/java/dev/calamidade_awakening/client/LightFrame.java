package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.CookieAtlas;
import dev.calamidade_awakening.api.EnginePasses;
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
 */
final class LightFrame {
    static final String PROGRAM = "calamidade_awakening:lights";
    static final int TEXELS = 12, ROWS = 64;

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

    private final StageScene scene;
    private final DataTexture texture = new DataTexture(TEXELS, ROWS);
    private final float[] data = new float[TEXELS * 4 * ROWS];
    private final float[] rect = new float[4];
    private final LightCookies cookies = new LightCookies();
    private List<Resolved> lights = List.of();
    private boolean heroNeeds, normalNeeds;
    private int requested, culled, textured, fallbacks, cookieCap;
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

    /** Resolves the visible lights of the plan, in priority order, capped by the quality. */
    void collect(StageScene.Plan plan, Vec3 camera, float partial) { collect(plan, camera, partial, null); }

    /**
     * Same, plus the engine lights of the terrain visuals (0.11.0): the cooling disc of every hot zone and the fire of
     * every stand-in, counted with the lights of their instance (caps and pass payment).
     */
    void collect(StageScene.Plan plan, Vec3 camera, float partial, TerrainFrame terrain) {
        heroNeeds = normalNeeds = false;
        requested = culled = 0;
        cookies.pump();
        var mc = Minecraft.getInstance();
        boolean calm = mc.options.hideLightningFlash().get();
        float flickerScale = ClientConfig.screenEffects() * (calm ? .25f : 1f);
        var frustum = mc.levelRenderer.getFrustum();
        List<List<Resolved>> perEffect = new ArrayList<>();
        for (StageScene.Sample sample : plan.effects()) {
            var list = sample.kind().lights();
            var extra = terrain == null ? null : terrain.lights(sample.event().order());
            if (list.isEmpty() && extra == null) continue;
            Vec3 center = camera.add(sample.relative());
            var sampler = sample.time().timeline();
            double seconds = sample.age() / 20.0;
            List<Resolved> resolved = new ArrayList<>(list.size());
            for (Light light : list) {
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
                } else {
                    r = new Resolved(light, a.subtract(camera), b.subtract(camera), state, weight);
                }
                if (!visible(r, camera, frustum)) {
                    culled++;
                    continue;
                }
                resolved.add(r);
            }
            if (extra != null) {
                requested += extra.size();
                for (Resolved r : extra) if (visible(r, camera, frustum)) resolved.add(r); else culled++;
            }
            if (resolved.isEmpty()) continue;
            perEffect.add(resolved);
            if (sample.kind().hero()) heroNeeds = true; else normalNeeds = true;
        }
        lights = EnginePasses.cap(perEffect, plan.limits().lights());
        cookieCap = CookieAtlas.textured(plan.limits().lights());
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
    private boolean visible(Resolved r, Vec3 camera, net.minecraft.client.renderer.culling.Frustum frustum) {
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
        double distance = c.length() - radius;
        // Cylindrical fog distance is at least the euclidean one over sqrt(2).
        if (distance > 0 && (fogCylinder ? distance / Math.sqrt(2) : distance) > fogEnd) return false;
        if (frustum == null || distance <= 0) return true;
        Vec3 w = c.add(camera);
        return frustum.isVisible(new AABB(w.x - radius, w.y - radius, w.z - radius, w.x + radius, w.y + radius, w.z + radius));
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
        PerfOverlay.lightDetail(requested, culled, Math.max(0, requested - culled - kept), drawn ? textured : 0,
            drawn ? fallbacks : 0, cookieCap, cookies.counts(), cookies.bytes());
    }

    /** Uploads the lights and queues the pass. Call only when the pass was paid for, before the effect passes. */
    void queue(FxApi.Frame frame) {
        int count = Math.min(lights.size(), ROWS);
        textured = fallbacks = 0;
        int k = 0;
        for (int i = 0; i < count; i++) {
            Resolved r = lights.get(i);
            Light light = r.light();
            Light.State s = r.state();
            float gain = s.intensity() * r.weight();
            int shape = light.shape().ordinal();
            int flags = 0;
            float cr = BeamFrame.red(s.color()), cg = BeamFrame.green(s.color()), cb = BeamFrame.blue(s.color());
            Light.Cookie cookie = light.cookie();
            LightCookies.Entry entry = null;
            if (cookie != null) {
                entry = cookies.get(cookie.texture());
                boolean budget = textured < cookieCap;
                if (entry.ready() && budget) {
                    flags |= 1;
                    textured++;
                } else if (entry.status == LightCookies.Status.PENDING && budget) {
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
                if (cookie.isPixelated()) flags |= 8;
                flags |= 16;   // has a cookie: square aperture for a spot, even on the fallback
            }
            int stops = light.gradientStops();
            int code = shape | light.attenuation().ordinal() << 3 | light.blend().ordinal() << 5 | flags << 7 | stops << 12;
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
            if (count > 0 && p == null && stops == 0) {
                // Conventional light: the shader reads only t0..t3; the rest of the row stays as it was.
                k += 8 * 4;
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
                int f1 = cookie.play() == Light.Cookie.Play.LOOP ? (f0 + 1) % cookie.frames() : Math.min(f0 + 1, cookie.frames() - 1);
                CookieAtlas.frameRect(entry.cell, entry.width, entry.height, cookie.columns(), cookie.rows(), f0, rect);
                k = put(k, rect[0], rect[1], rect[2], rect[3]);
                CookieAtlas.frameRect(entry.cell, entry.width, entry.height, cookie.columns(), cookie.rows(), f1, rect);
                k = put(k, rect[0], rect[1], rect[2], rect[3]);
                maxLod = CookieAtlas.maxLod((float) entry.width / cookie.columns(), (float) entry.height / cookie.rows());
            } else {
                k = put(k, 0, 0, 0, 0);
                k = put(k, 0, 0, 0, 0);
            }
            for (int j = 1; j < 4; j++) {
                float w = j == 1 ? mix : j == 2 ? maxLod : 0;
                if (g != null && j < g.length) k = put(k, cr * gain * BeamFrame.red(g[j]), cg * gain * BeamFrame.green(g[j]),
                    cb * gain * BeamFrame.blue(g[j]), w);
                else k = put(k, 0, 0, 0, w);
            }
        }
        int id = texture.upload(data, count);
        int atlas = cookies.texture();
        float start = fogStart, end = fogEnd, cylinder = fogCylinder ? 1 : 0;
        frame.pass(PROGRAM, u -> {
            u.glTexture("CaLightData", id);
            // Without the atlas the cookie sampler gets the data texture: it is never read then (no cookie flag set).
            u.glTexture("CaCookieAtlas", atlas != 0 ? atlas : id);
            u.f4("CaLightInfo", count, start, end, cylinder);
            u.f4("CaCookieInfo", CookieAtlas.SIDE, atlas != 0 ? 1 : 0, 0, 0);
        });
    }

    private int put(int k, float x, float y, float z, float w) {
        data[k] = x; data[k + 1] = y; data[k + 2] = z; data[k + 3] = w;
        return k + 4;
    }
}
