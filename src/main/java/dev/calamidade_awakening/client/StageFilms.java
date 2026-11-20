package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FilmApi;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Subject;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.client.geometry.Timeline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Camera adapter. RimuLib owns playback, skip, input and restoration. */
public final class StageFilms {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private Subject subject;
    private VisualKind kind;

    private long order;
    public void end(long instance) { if (subject != null && instance == order) cancel(); }
    public void start(Vec3 center, VisualKind selected, long instance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || FilmApi.active() || !FilmApi.automatic()) return;
        order = instance;
        subject = FilmApi.follow(mc.player, center);
        kind = selected;
        var addon = selected.addon();
        // 0.6.0: an addon film builder replaces the card's linear shots.
        var builder = dev.calamidade_awakening.api.v1.client.AddonLayers.film(selected.networkKey());
        if (builder != null) {
            List<cyou.rimuru.rimulib.mc1211.client.cinematic.Shot> custom;
            try {
                custom = builder.apply(new dev.calamidade_awakening.api.v1.client.AddonLayers.Film(
                    subject, center, selected.duration, instance, selected.clock()));
            } catch (RuntimeException failure) {
                LOGGER.error("Film builder for {} failed", selected.networkKey(), failure);
                custom = null;
            }
            if (custom == null || custom.isEmpty()) {
                cancel();
                return;
            }
            String tier = addon != null ? addon.card().tier() : "AWAKENING";
            FilmApi.play(FilmApi.film(subject, tier, kind.localizedTitle(), kind.color, List.copyOf(custom)));
            return;
        }
        if (addon != null && !addon.card().camera().isEmpty()) {
            var shots = new java.util.ArrayList<cyou.rimuru.rimulib.mc1211.client.cinematic.Shot>();
            var last = addon.card().camera().getFirst();
            int from = 0;
            for (var next : addon.card().camera()) {
                var previous = last;
                shots.add(FilmApi.cut(from,next.endTick(),(s,partial,k) -> {
                    double t = Timeline.smooth(k);
                    Vec3 p = previous.camera().lerp(next.camera(),t);
                    return FilmApi.pose(s.aroundTarget(partial,p.x,p.y,p.z),s.target(),
                        (float)(previous.fov()+(next.fov()-previous.fov())*t));
                }));
                from = next.endTick();
                last = next;
            }
            FilmApi.play(FilmApi.film(subject,addon.card().tier(),kind.localizedTitle(),kind.color,shots));
            return;
        }
        if (kind == VisualKind.NEXUS) {
            var shots = List.of(
                FilmApi.cut(0, 80, (s, partial, k) -> FilmApi.pose(
                    s.aroundTarget(partial, 12 - 5*k, 5 + k, 17 - 3*k), s.target(), 64f)),
                FilmApi.sweep(80, 150, 14, (s, partial, k) -> FilmApi.pose(
                    s.aroundTarget(partial, 7, 6, 14), s.target(), 58f)),
                FilmApi.sweep(150, 198, 12, (s, partial, k) -> FilmApi.pose(
                    s.aroundTarget(partial, 7 + 4*k, 6 + 3*k, 14 + 5*k), s.target(), 67f)));
            FilmApi.play(FilmApi.film(subject, "AWAKENING", kind.localizedTitle(), kind.color, shots));
            return;
        }
        int first = (int) (kind.duration * .22);
        int second = (int) (kind.duration * .65);
        int end = Math.max(second + 1, kind.duration - 6);
        double radius = Math.max(8, kind.radius * 1.4);
        var shots = List.of(
            FilmApi.cut(0, first, (s, partial, k) ->
                FilmApi.pose(s.aroundTarget(partial, 2 + k * 4, 3 + k * 3, radius), s.target(), 65f)),
            FilmApi.sweep(first, second, 16, (s, partial, k) ->
                FilmApi.pose(s.aroundTarget(partial, radius * (.8 - 1.6 * k), 6 + k * 3, radius), s.target(), 59f)),
            FilmApi.sweep(second, end, 18, (s, partial, k) ->
                FilmApi.pose(s.aroundTarget(partial, -8 + k * 6, 8 - k * 4, radius - k * 3), s.target(), 70f))
        );
        FilmApi.play(FilmApi.film(subject, "AWAKENING", kind.localizedTitle(), kind.color, shots));
    }

    public float orbitalMix(float partial) {
        if (subject == null || kind == null || !kind.orbital || FilmApi.weight(subject) <= .001f) return 0;
        double p = FilmApi.time(subject, partial) / kind.duration;
        return (float) (Timeline.envelope(p, .22, .32, .57, .69) * FilmApi.weight(subject));
    }

    public float seconds(float partial) {
        return subject == null ? 0 : FilmApi.time(subject, partial) / 20f;
    }

    public void cancel() {
        if (subject != null) {
            FilmApi.stop(subject);
            subject = null;
            kind = null;
        }
    }
}
