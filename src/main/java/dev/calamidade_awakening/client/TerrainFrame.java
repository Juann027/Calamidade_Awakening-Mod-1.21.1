package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.EnginePasses;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.TerrainNoise;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Terrain visuals of the current frame (0.11.0): the cooling heat over the area of every committed edit and the
 * falling stand-ins, from the drawn instances (HERO first), drawn in the single engine pass "terrain" right after the
 * lights pass. The heated area is computed in the shader from the edit's analytic shape and the instance seed (the same
 * formulas the server planned with). Each hot zone and each stand-in also adds a visual light to the lights pass (an
 * orange disc that cools with the heat, a fire point light on the stand-in). The pass costs one slot of the HERO budget
 * when a drawn HERO effect needs it, otherwise one of the common budget; it is paid before the lights pass.
 */
final class TerrainFrame {
    static final String PROGRAM = "calamidade_awakening:terrain";
    static final int TEXELS = 23, ROWS = 16;
    /** Most hot zones and stand-ins drawn per frame, by quality (LOW, BALANCED, HIGH). */
    static final int[] ZONES = {2, 4, 8}, STAND_INS = {4, 8, 8};
    private static final Light DISC = Light.disc(Point.center(), 1).build();
    private static final Light POINT = Light.point(Point.center()).build();

    /** A hot zone: origin relative to the camera, frame, heat and the edit. */
    record Zone(TerrainEdit edit, Vec3 origin, Basis basis, float heat, int seed, float seconds) {}

    /** A stand-in: center relative to the camera, fall direction, opacity, and whether the shader draws the ball. */
    record StandIn(TerrainEdit edit, Vec3 center, Vec3 direction, float opacity, float radius, boolean ball, float trail) {}

    private final StageScene scene;
    private final TerrainClient state;
    private final DataTexture texture = new DataTexture(TEXELS, ROWS);
    private final float[] data = new float[TEXELS * 4 * ROWS];
    private List<Zone> zones = List.of();
    private List<StandIn> standIns = List.of();
    private final Map<Long, List<LightFrame.Resolved>> lights = new HashMap<>();
    private boolean heroNeeds, normalNeeds;

    TerrainFrame(StageScene scene, TerrainClient state) {
        this.scene = scene;
        this.state = state;
    }

    /** Resolves the hot zones and stand-ins of the plan (and their lights), in priority order, capped by the quality. */
    void collect(StageScene.Plan plan, Vec3 camera, float partial) {
        heroNeeds = normalNeeds = false;
        lights.clear();
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            zones = List.of();
            standIns = List.of();
            return;
        }
        double now = mc.level.getGameTime() + partial;
        int q = plan.quality().ordinal();
        List<Zone> zoneList = new ArrayList<>();
        List<StandIn> standInList = new ArrayList<>();
        for (StageScene.Sample sample : plan.effects()) {
            var edits = sample.kind().terrain();
            if (edits.isEmpty()) continue;
            var timeline = sample.kind().timeline();
            Vec3 center = camera.add(sample.relative());
            boolean used = false;
            List<LightFrame.Resolved> sampleLights = new ArrayList<>();
            for (int i = 0; i < edits.size(); i++) {
                TerrainEdit edit = edits.get(i);
                TerrainClient.Commit commit = state.commit(sample.event(), sample.kind(), edit, i, now, center, sample.basis());
                if (commit != null) {
                    float heat = edit.heatAt(timeline, sample.localAge(), commit.since());
                    if (heat > 2e-3f && zoneList.size() < ZONES[q]) {
                        Vec3 origin = commit.origin().subtract(camera);
                        zoneList.add(new Zone(edit, origin, commit.basis(), heat, TerrainNoise.seed32(sample.event().seed()),
                            (float) (commit.since() / 20.0)));
                        used = true;
                        float reach = (float) edit.shape().extent() + edit.thickness();
                        int color = ember(edit.glow(), heat);
                        Vec3 at = origin.add(StageScene.turn(commit.basis(), 0, .5, 0));
                        sampleLights.add(new LightFrame.Resolved(DISC, at, at,
                            new Light.State(1, 0, 0, 0, color, 1.8f * heat, Math.min(Light.MAX_RADIUS, reach), 1.5f, reach * .5f,
                                Math.max(4, reach * .8f)), 1));
                    }
                }
                TerrainClient.Entry entry = state.get(sample.event().order(), i);
                Vec3 origin = commit != null ? commit.origin() : center;
                Basis basis = commit != null ? commit.basis() : sample.basis();
                TerrainClient.Pose pose = TerrainClient.standIn(edit, timeline, sample.localAge(), origin, basis,
                    entry == null ? null : entry.structure);
                if (pose != null && standInList.size() < STAND_INS[q]) {
                    TerrainEdit.StandIn s = edit.standIn();
                    boolean ball = s.kind() == TerrainEdit.StandIn.Kind.SPHERE;
                    Vec3 c = pose.position();
                    float radius = s.radius() + .4f;
                    if (!ball && entry != null && entry.structure != null) {
                        var st = entry.structure;
                        c = c.add(st.sizeX() * .5, st.sizeY() * .5, st.sizeZ() * .5);
                        radius = Math.max(st.sizeX(), Math.max(st.sizeY(), st.sizeZ())) * .45f;
                    }
                    // The trail only while falling.
                    float trail = pose.progress() < 1 ? s.trail() * Math.min(1, pose.progress() * 4 + .2f) : 0;
                    standInList.add(new StandIn(edit, c.subtract(camera), pose.direction(), pose.opacity(), radius, ball, trail));
                    used = true;
                    Vec3 at = c.subtract(camera);
                    sampleLights.add(new LightFrame.Resolved(POINT, at, at,
                        new Light.State(1, 0, 0, 0, s.color(), 2f * pose.opacity(), Math.min(Light.MAX_RADIUS, 8 + radius * 3), 1.6f, 0, 0), 1));
                }
            }
            if (!used) continue;
            if (sample.kind().hero()) heroNeeds = true; else normalNeeds = true;
            if (!sampleLights.isEmpty()) lights.put(sample.event().order(), sampleLights);
        }
        zones = zoneList;
        standIns = standInList;
    }

    /** Engine lights of the zones and stand-ins of an instance this frame (added to the lights pass), or null. */
    List<LightFrame.Resolved> lights(long order) { return lights.get(order); }

    /** Hot zones kept this frame (F7). */
    int zones() { return zones.size(); }

    /** Stand-ins kept this frame (F7). */
    int standIns() { return standIns.size(); }

    /** Pays for this frame's pass from the right pool. */
    EnginePasses.Pool pay(dev.calamidade_awakening.api.PassBudget budget) {
        return zones.isEmpty() && standIns.isEmpty() ? EnginePasses.Pool.NONE : EnginePasses.pay(budget, heroNeeds, normalNeeds);
    }

    /** Uploads the zones and stand-ins and queues the pass. Call only when the pass was paid for, after the lights pass. */
    void queue(FxApi.Frame frame, Quality quality, float seconds, boolean flashAllowed) {
        int k = 0;
        int zoneCount = Math.min(zones.size(), 8), standCount = Math.min(standIns.size(), ROWS - zoneCount);
        for (int i = 0; i < zoneCount; i++) {
            Zone z = zones.get(i);
            TerrainEdit edit = z.edit();
            TerrainShape shape = edit.shape();
            Basis b = z.basis();
            int row = i * TEXELS * 4;
            k = row;
            k = put(k, (float) z.origin().x, (float) z.origin().y, (float) z.origin().z, z.heat());
            k = put(k, (float) b.lx, (float) b.ly, (float) b.lz, (float) shape.extent());
            k = put(k, (float) b.ux, (float) b.uy, (float) b.uz, Math.max(1, edit.thickness()));
            k = put(k, (float) b.fx, (float) b.fy, (float) b.fz, shape.roughness());
            k = put(k, shape.roughScale(), z.seed() & 0xFFFF, (z.seed() >>> 16) & 0xFFFF, shape.primitives().size());
            k = put(k, BeamFrame.red(edit.glow()), BeamFrame.green(edit.glow()), BeamFrame.blue(edit.glow()), edit.smoke());
            k = put(k, edit.cracks(), z.seconds(), 0, 0);
            for (TerrainShape.Primitive p : shape.primitives()) {
                k = put(k, p.kind().ordinal() + (p.subtract() ? 4 : 0), p.a(), p.b(), p.c());
                k = put(k, p.x(), p.y(), p.z(), p.d());
            }
        }
        for (int j = 0; j < standCount; j++) {
            StandIn s = standIns.get(j);
            k = (zoneCount + j) * TEXELS * 4;
            k = put(k, (float) s.center().x, (float) s.center().y, (float) s.center().z, s.radius());
            k = put(k, (float) s.direction().x, (float) s.direction().y, (float) s.direction().z, s.opacity());
            int color = s.edit().standIn().color();
            k = put(k, BeamFrame.red(color), BeamFrame.green(color), BeamFrame.blue(color), s.trail());
            k = put(k, s.ball() ? 0 : 1, 0, 0, 0);
        }
        int id = texture.upload(data, zoneCount + standCount);
        float zonesF = zoneCount, standsF = standCount, q = quality.ordinal();
        float intensity = ClientConfig.screenEffects(), flash = flashAllowed ? 1 : 0, motion = ClientConfig.motionEffects() ? 1 : 0;
        frame.pass(PROGRAM, u -> {
            u.glTexture("CaTerrainData", id);
            u.f4("CaTerrainInfo", zonesF, standsF, seconds, q);
            u.f4("CaTerrainLook", intensity, flash, motion, 0);
        });
    }

    private int put(int k, float x, float y, float z, float w) {
        data[k] = x; data[k + 1] = y; data[k + 2] = z; data[k + 3] = w;
        return k + 4;
    }

    /** Light color of a zone: deep red when cold, the glow color when hot (like caTerrainEmber in the shader). */
    static int ember(int glow, float heat) {
        float cold = 1 - Math.min(1, heat / .6f);
        float r = mix(((glow >> 16) & 255) / 255f, .35f, cold);
        float g = mix(((glow >> 8) & 255) / 255f, .03f, cold);
        float b = mix((glow & 255) / 255f, .01f, cold);
        return 0xff000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
