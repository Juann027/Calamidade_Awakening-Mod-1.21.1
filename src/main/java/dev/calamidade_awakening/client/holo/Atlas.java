package dev.calamidade_awakening.client.holo;

import cyou.rimuru.rimulib.mc1211.client.api.NetClientApi;
import dev.calamidade_awakening.api.QualityMode;
import dev.calamidade_awakening.api.QualityProfile;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.holo.HoloPlacement;
import dev.calamidade_awakening.client.ClientHooks;
import dev.calamidade_awakening.client.PerfOverlay;
import dev.calamidade_awakening.config.ClientConfig;
import dev.calamidade_awakening.config.ServerConfig;
import dev.calamidade_awakening.net.CastRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector3f;

/**
 * Content of the Star Atlas (0.11.2): a fixed board of {@link #W} × {@link #H} layout units with one tab strip and the
 * panels of the current tab. Tabs: skills (engine cards and one page per addon namespace, cast through the server as
 * before), quality (AUTO and the four profiles with their caps, fine tuning with sliders and safety maxima),
 * accessibility (the client config entries), terrain (read only: the server config, synced by NeoForge) and diagnostics
 * (the F7 lines live, a frame-time graph, bench and an 8 s recording). Values change at once and are stored in the
 * client config; server commands still need permission on the server.
 *
 * <p>Celestial identity: stars at the corners of every panel, constellation lines between neighbouring panels, star dust
 * at several depths (real parallax: the board is in the world), border glow. Opening: the stars light up, the lines
 * draw themselves and the panels form out of the stars; closing runs it backwards. Text is always measured and cut to
 * fit inside its panel. This is the neutral skeleton of the visual: colours and arrangement live in {@link #style} and
 * the {@code layout*} methods so the chosen direction can replace them without touching the engineering.
 */
final class Atlas {
    static final float W = 640F, H = 360F;
    /** Surface pixels per layout unit (at 1080p the board is about 3 px per unit). */
    static final float DENSITY = 3F;

    enum Tab {
        SKILLS("skills", "Skills"), QUALITY("quality", "Quality"), ACCESS("access", "Accessibility"),
        TERRAIN("terrain", "Terrain"), DIAG("diag", "Diagnostics");

        final String key, fallback;

        Tab(String key, String fallback) { this.key = key; this.fallback = fallback; }

        Component title() { return t("tab." + key, fallback); }
    }

    /** Palette of the neutral skeleton (ARGB). */
    record Style(int glassTop, int glassBottom, int edge, int accent, int text, int sub, int hotFill, int star, int line) {}

    static final Style style = new Style(0xD80A0E26, 0xD8181E4A, 0xFFBFE4FF, 0xFFFFD98A, 0xFFEEF6FF, 0xFFA9BCDC,
        0x3CFFD98A, 0xFFF2C2, 0xFFE8B4);

    private final AtlasWorld world;
    private final Minecraft mc = Minecraft.getInstance();
    private final Map<String, HoloPanel> panels = new HashMap<>();
    private final List<HoloPanel> active = new ArrayList<>();
    private Tab tab = Tab.SKILLS;
    private long openedAt, tabAt, closeAt = -1;
    private final List<Page> pages = new ArrayList<>();
    private int page;
    /** Profile whose caps the quality tab edits. */
    private QualityProfile editing;

    /** A page of cards: its name and up to five effects. */
    record Page(String name, List<VisualKind> kinds) {}

    Atlas(AtlasWorld world) {
        this.world = world;
        buildPages();
        layoutTab();
    }

    static Component t(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(AtlasWorld.PREFIX + key, fallback, args);
    }

    // ================================================================ pages and layout

    private void buildPages() {
        pages.add(new Page("Calamidade", List.of(VisualKind.PRISM, VisualKind.LATTICE, VisualKind.VEIL, VisualKind.AURORA,
            VisualKind.NEXUS)));
        var groups = new java.util.TreeMap<String, List<VisualKind>>();
        for (var entry : dev.calamidade_awakening.api.v1.Addons.entries()) {
            int id = VisualKind.localId(entry.effect().toString());
            if (id == -1) continue; // addon effects have negative ids; -1 means unknown
            groups.computeIfAbsent(entry.effect().getNamespace(), k -> new ArrayList<>()).add(VisualKind.byId(id));
        }
        groups.forEach((namespace, kinds) -> {
            for (int i = 0; i < kinds.size(); i += 5)
                pages.add(new Page(namespace, List.copyOf(kinds.subList(i, Math.min(kinds.size(), i + 5)))));
        });
    }

    private HoloPanel panel(String id) { return panels.computeIfAbsent(id, HoloPanel::new); }

    /** Panels of the current tab, in drawing order (tabs first). */
    private void layoutTab() {
        for (HoloPanel p : active) p.visible = false;
        active.clear();
        active.add(panel("tabs").rect(110, 12, 420, 30));
        switch (tab) {
            case SKILLS -> {
                // Five cards around the protagonist one: in front, two above at the sides, two below.
                float[][] slots = {{245, 112, 14, 0}, {62, 66, 0, 8}, {428, 66, 0, -8}, {88, 206, -6, 6}, {402, 206, -6, -6}};
                List<VisualKind> kinds = pages.get(page).kinds();
                for (int i = 0; i < kinds.size(); i++) {
                    HoloPanel p = panel("card" + i).rect(slots[i][0], slots[i][1], 150, 104);
                    p.dz = slots[i][2];
                    p.yaw = slots[i][3];
                    active.add(p);
                }
                active.add(panel("pages").rect(170, 322, 300, 30));
            }
            case QUALITY -> {
                active.add(panel("modes").rect(60, 52, 520, 78));
                active.add(panel("tune").rect(40, 138, 350, 198));
                active.add(panel("table").rect(402, 138, 198, 198));
            }
            case ACCESS -> active.add(panel("access").rect(130, 56, 380, 268));
            case TERRAIN -> active.add(panel("terrain").rect(130, 56, 380, 268));
            case DIAG -> {
                active.add(panel("diag").rect(20, 52, 340, 286));
                active.add(panel("graph").rect(372, 52, 248, 140));
                active.add(panel("tests").rect(372, 200, 248, 138));
            }
        }
        for (HoloPanel p : active) {
            p.visible = true;
            if (!p.id.startsWith("card")) p.dz = p.yaw = 0;
        }
    }

    List<HoloPanel> panels() { return active; }

    /** Placement samples (corners and centre of every panel) in units of the front distance. */
    List<HoloPlacement.Sample> samples(float eyeZ) {
        List<HoloPlacement.Sample> out = new ArrayList<>();
        for (HoloPanel p : active) {
            int kind = p.id.equals("tabs") || p.id.equals("card0") || active.size() <= 3 ? HoloPlacement.ESSENTIAL
                : HoloPlacement.IMPORTANT;
            for (float fx : new float[] {.04F, .5F, .96F})
                for (float fy : new float[] {.08F, .92F})
                    out.add(new HoloPlacement.Sample((p.x + p.w * fx - W / 2F) / eyeZ, (H / 2F - (p.y + p.h * fy)) / eyeZ,
                        1F - p.dz / eyeZ, kind));
        }
        return out;
    }

    // ================================================================ lifecycle and animation

    void open() {
        openedAt = tabAt = System.currentTimeMillis();
        closeAt = -1;
    }

    void close() { if (closeAt < 0) closeAt = System.currentTimeMillis(); }

    boolean closing() { return closeAt >= 0; }

    boolean closeFinished() { return closeAt >= 0 && System.currentTimeMillis() - closeAt > 400; }

    void tick() {}

    void release() { panels.values().forEach(HoloPanel::close); }

    static float smooth(float x) {
        float t = Math.clamp(x, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    private float seconds() { return (System.currentTimeMillis() - openedAt) / 1000F; }

    /** 1 while open, going to 0 over the closing animation. */
    private float closingFactor() { return closeAt < 0 ? 1F : 1F - smooth((System.currentTimeMillis() - closeAt) / 360F); }

    float starsAlpha() { return smooth(seconds() / .25F) * closingFactor(); }

    float linesProgress() { return smooth((seconds() - .15F) / .35F) * closingFactor(); }

    /** Panel growth: out of its corner stars after the lines, staggered; again for new content after a tab change. */
    void animate() {
        float t = seconds(), tt = (System.currentTimeMillis() - tabAt) / 1000F, c = closingFactor();
        for (int i = 0; i < active.size(); i++) {
            HoloPanel p = active.get(i);
            float born = smooth((t - .30F - i * .05F) / .40F);
            if (i > 0) born = Math.min(born, smooth((tt - (i - 1) * .05F) / .35F));
            born *= c;
            p.scale = .18F + .82F * born;
            p.alpha = born;
            p.contentAlpha = smooth((born - .5F) / .5F);
            p.accent = style.edge();
        }
    }

    private void setTab(Tab next) {
        if (next == tab) return;
        tab = next;
        tabAt = System.currentTimeMillis();
        layoutTab();
        click();
    }

    void selectTab(int index) { setTab(Tab.values()[Math.clamp(index, 0, Tab.values().length - 1)]); }

    int tabIndex() { return tab.ordinal(); }

    int pageIndex() { return page; }

    int pageCount() { return pages.size(); }

    private void setPage(int next) {
        if (pages.isEmpty()) return;
        int n = Math.floorMod(next, pages.size());
        if (n == page) return;
        page = n;
        tabAt = System.currentTimeMillis();
        layoutTab();
        click();
    }

    private void click() {
        if (mc.player != null) mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .6F, 1.6F);
    }

    // ================================================================ input

    /** Click on a panel: a drag region is captured (returned); a click region runs now. {@code use} = right button. */
    HoloZone press(HoloPanel p, float x, float y, boolean use) {
        if (use && p.id.startsWith("card")) {
            setPage(page + 1);
            return null;
        }
        HoloZone z = p.zoneAt(x, y);
        if (z == null) return null;
        if (z.drag != null) return z;
        z.click.run();
        return null;
    }

    /** Wheel: a region's own wheel; over the tab strip the tab; over the skills panels the page; elsewhere nothing. */
    void scroll(HoloPanel p, float x, float y, double amount) {
        if (p != null) {
            HoloZone z = p.scrollZoneAt(x, y);
            if (z != null) {
                z.scroll.accept(amount);
                return;
            }
        }
        if (p == null) return;
        int step = amount > 0 ? -1 : 1;
        if (p.id.equals("tabs")) setTab(Tab.values()[Math.floorMod(tab.ordinal() + step, Tab.values().length)]);
        else if (tab == Tab.SKILLS) setPage(page + step);
    }

    // ================================================================ 3D pass

    void render(HoloGl gl) {
        float unit = world.unit();
        Vector3f a = new Vector3f(), b = new Vector3f();
        // Star dust at several depths: real parallax, since the board stands in the world.
        gl.beginStars();
        float stars = starsAlpha(), time = seconds();
        long seed = 0x5DEECE66DL;
        for (int i = 0; i < 110; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float px = ((seed >>> 16) & 1023) / 1023F * (W + 80) - 40;
            float py = ((seed >>> 26) & 1023) / 1023F * (H + 40) - 20;
            float pz = ((seed >>> 36) & 1023) / 1023F * 120F - 90F;
            float twinkle = .45F + .55F * (float) Math.abs(Math.sin(time * (.6 + (i % 7) * .21) + i));
            world.viewLayout.transformPosition(a.set(px, py, pz));
            gl.star(a, unit * (i % 5 == 0 ? 1.1F : .6F), i % 6 == 0 ? style.accent() : style.star(), stars * twinkle * .45F);
        }
        gl.endStars();
        gl.beginLight();
        // Constellation lines between neighbouring panels (nearest corners), drawn from the first to the second.
        float lines = linesProgress();
        for (int i = 1; i < active.size(); i++) {
            HoloPanel q = active.get(i);
            // Cards hang from the first card (the protagonist one); the rest chain to the previous panel.
            HoloPanel p = i >= 2 && tab == Tab.SKILLS && q.id.startsWith("card") ? active.get(1) : active.get(i - 1);
            nearestCorners(p, q, a, b);
            b.set(a.x + (b.x - a.x) * lines, a.y + (b.y - a.y) * lines, a.z + (b.z - a.z) * lines);
            gl.line(a, b, unit * .7F, GLine(lines * .8F), GLine(lines * .8F));
        }
        gl.endLight();
        // Panels back to front.
        List<HoloPanel> order = new ArrayList<>(active);
        order.sort(Comparator.comparingDouble(p -> p.modelView.m32()));
        for (HoloPanel p : order) {
            if (!p.visible || !p.surface.ready()) continue;
            gl.texturedPanel(p.modelView, p.w, p.h, p.surface.textureId(), p.surface.u(p.w), p.surface.vBottom(p.h), p.alpha);
        }
        // Corner stars on top: they light first and the panels grow out of them.
        gl.beginStars();
        for (HoloPanel p : active) {
            float grow = 1F + 1.6F * (1F - p.alpha);
            for (int c = 0; c < 4; c++) {
                p.modelView.transformPosition(a.set((c & 1) == 0 ? 0 : p.w, (c & 2) == 0 ? 0 : p.h, 0F));
                gl.star(a, unit * (c == 0 || c == 3 ? 1.5F : 1.0F) * grow, p.hot ? style.accent() : style.star(), stars);
            }
        }
        gl.endStars();
    }

    private static int GLine(float alpha) { return HoloGl.withAlpha(style.line(), alpha); }

    private static void nearestCorners(HoloPanel p, HoloPanel q, Vector3f outA, Vector3f outB) {
        Vector3f a = new Vector3f(), b = new Vector3f();
        float best = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            p.modelView.transformPosition(a.set((i & 1) == 0 ? 0 : p.w, (i & 2) == 0 ? 0 : p.h, 0F));
            for (int j = 0; j < 4; j++) {
                q.modelView.transformPosition(b.set((j & 1) == 0 ? 0 : q.w, (j & 2) == 0 ? 0 : q.h, 0F));
                float d = a.distanceSquared(b);
                if (d < best) {
                    best = d;
                    outA.set(a);
                    outB.set(b);
                }
            }
        }
    }

    // ================================================================ surfaces

    void drawSurfaces() {
        for (HoloPanel p : active) {
            if (!p.visible || p.culled || p.alpha <= .01F) continue;
            p.surface.ensure(p.w, p.h, DENSITY);
            GuiGraphics g = p.surface.begin();
            try {
                g.pose().scale(DENSITY, DENSITY, 1F);
                p.resetZones();
                glass(g, p);
                switch (p.id) {
                    case "tabs" -> tabs(g, p);
                    case "pages" -> pages(g, p);
                    case "modes" -> modes(g, p);
                    case "tune" -> tune(g, p);
                    case "table" -> table(g, p);
                    case "access" -> access(g, p);
                    case "terrain" -> terrain(g, p);
                    case "diag" -> diag(g, p);
                    case "graph" -> graph(g, p);
                    case "tests" -> tests(g, p);
                    default -> card(g, p, Integer.parseInt(p.id.substring(4)));
                }
            } finally {
                p.surface.end();
            }
        }
    }

    // ------------------------------------------------------------------ drawing helpers (panel local units)

    private Font font() { return mc.font; }

    private static void glass(GuiGraphics g, HoloPanel p) {
        int w = (int) p.w, h = (int) p.h;
        g.fillGradient(1, 1, w - 1, h - 1, style.glassTop(), style.glassBottom());
        int edge = p.hot ? style.accent() : style.edge();
        g.fill(0, 0, w, 1, edge);
        g.fill(0, h - 1, w, h, edge);
        g.fill(0, 1, 1, h - 1, edge);
        g.fill(w - 1, 1, w, h - 1, edge);
        // Soft inner glow along the border.
        int glow = HoloGl.withAlpha(edge, .18F);
        g.fill(1, 1, w - 1, 3, glow);
        g.fill(1, h - 3, w - 1, h - 1, glow);
    }

    /** Text cut with an ellipsis to {@code maxW} units, at {@code scale}. */
    private void text(GuiGraphics g, Component text, float x, float y, float maxW, float scale, int color) {
        String s = text.getString();
        int room = (int) (maxW / scale);
        if (font().width(s) > room) s = font().plainSubstrByWidth(s, Math.max(0, room - font().width("…"))).stripTrailing() + "…";
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font(), s, 0, 0, color, false);
        g.pose().popPose();
    }

    private void centered(GuiGraphics g, Component text, float cx, float y, float maxW, float scale, int color) {
        float width = Math.min(maxW, font().width(text) * scale);
        text(g, text, cx - width / 2F, y, maxW, scale, color);
    }

    private void right(GuiGraphics g, Component text, float rx, float y, float maxW, float scale, int color) {
        float width = Math.min(maxW, font().width(text) * scale);
        text(g, text, rx - width, y, maxW, scale, color);
    }

    /** Wrapped text, at most {@code lines} lines; the last one cut with an ellipsis. Returns the height used. */
    private float wrapped(GuiGraphics g, Component text, float x, float y, float maxW, float scale, int lines, int color) {
        List<FormattedCharSequence> split = font().split(text, (int) (maxW / scale));
        int n = Math.min(lines, split.size());
        for (int i = 0; i < n; i++) {
            g.pose().pushPose();
            g.pose().translate(x, y + i * 10 * scale, 0);
            g.pose().scale(scale, scale, 1F);
            if (i == n - 1 && split.size() > n) {
                // Rebuild the last visible line as a string to cut it with an ellipsis inside the width.
                StringBuilder sb = new StringBuilder();
                split.get(i).accept((index, st, cp) -> { sb.appendCodePoint(cp); return true; });
                String s = font().plainSubstrByWidth(sb.toString(), (int) (maxW / scale) - font().width("…")).stripTrailing() + "…";
                g.drawString(font(), s, 0, 0, color, false);
            } else {
                g.drawString(font(), split.get(i), 0, 0, color, false);
            }
            g.pose().popPose();
        }
        return n * 10 * scale;
    }

    private static boolean hot(HoloPanel p, float x, float y, float w, float h) {
        return p.hot && p.aimX >= x && p.aimY >= y && p.aimX < x + w && p.aimY < y + h;
    }

    private void button(GuiGraphics g, HoloPanel p, float x, float y, float w, float h, Component label, boolean on,
                        boolean enabled, Runnable action) {
        boolean hover = enabled && hot(p, x, y, w, h);
        int fill = on ? HoloGl.withAlpha(style.accent(), .85F) : hover ? style.hotFill() : 0x22FFFFFF;
        g.fill((int) x, (int) y, (int) (x + w), (int) (y + h), fill);
        int edge = on || hover ? style.accent() : 0x55FFFFFF;
        g.fill((int) x, (int) y, (int) (x + w), (int) y + 1, edge);
        g.fill((int) x, (int) (y + h) - 1, (int) (x + w), (int) (y + h), edge);
        centered(g, label, x + w / 2F, y + (h - 7F * .8F) / 2F, w - 4, .8F, on ? 0xFF14101E : enabled ? style.text() : 0xFF6A7690);
        if (enabled) p.zone(x, y, w, h).click = () -> {
            action.run();
            click();
        };
    }

    /** Horizontal slider: drag or wheel; shows {@code value / max}. */
    private void slider(GuiGraphics g, HoloPanel p, float x, float y, float w, Component label, int value, int min, int max,
                        int step, IntConsumer set, String shown) {
        text(g, label, x, y + 1, 110, .72F, style.text());
        float tx = x + 112, tw = w - 112 - 46;
        float k = max == min ? 0 : (value - min) / (float) (max - min);
        boolean hover = hot(p, tx - 4, y - 2, tw + 8, 12);
        g.fill((int) tx, (int) y + 4, (int) (tx + tw), (int) y + 6, 0x40FFFFFF);
        g.fill((int) tx, (int) y + 4, (int) (tx + tw * k), (int) y + 6, style.accent());
        int knob = (int) (tx + tw * k);
        g.fill(knob - 2, (int) y + 1, knob + 2, (int) y + 9, hover ? 0xFFFFFFFF : style.accent());
        right(g, Component.literal(shown), x + w, y + 1, 44, .72F, style.text());
        HoloZone z = p.zone(tx - 4, y - 2, tw + 8, 12);
        z.drag = new HoloZone.Drag() {
            @Override public void move(float mx, float my) { set.accept(snap(min + (mx - tx) / tw * (max - min), min, max, step)); }
            @Override public void release(float mx, float my, boolean clickOnly) { move(mx, my); click(); }
        };
        z.scroll = amount -> set.accept(Math.clamp(value + (amount > 0 ? step : -step), min, max));
    }

    private static int snap(float v, int min, int max, int step) {
        int s = Math.round((v - min) / step) * step + min;
        return Math.clamp(s, min, max);
    }

    private void toggle(GuiGraphics g, HoloPanel p, float x, float y, float w, Component label, Component help, boolean on,
                        Consumer<Boolean> set) {
        boolean hover = hot(p, x, y, w, 26);
        if (hover) g.fill((int) x - 2, (int) y - 2, (int) (x + w) + 2, (int) y + 25, style.hotFill());
        text(g, label, x, y, w - 40, .78F, style.text());
        wrapped(g, help, x, y + 10, w - 40, .6F, 2, style.sub());
        int bx = (int) (x + w - 30);
        g.fill(bx, (int) y + 2, bx + 28, (int) y + 12, on ? HoloGl.withAlpha(style.accent(), .9F) : 0x40FFFFFF);
        g.fill(on ? bx + 18 : bx + 1, (int) y + 3, on ? bx + 27 : bx + 10, (int) y + 11, 0xFFFFFFFF);
        p.zone(x, y, w, 26).click = () -> {
            set.accept(!on);
            click();
        };
    }

    // ------------------------------------------------------------------ tabs and skills

    private void tabs(GuiGraphics g, HoloPanel p) {
        float cell = p.w / Tab.values().length;
        for (Tab each : Tab.values()) {
            float x = each.ordinal() * cell;
            boolean on = each == tab, hover = hot(p, x, 0, cell, p.h);
            if (on) g.fill((int) x + 2, 3, (int) (x + cell) - 2, (int) p.h - 3, 0x30FFD98A);
            if (hover && !on) g.fill((int) x + 2, 3, (int) (x + cell) - 2, (int) p.h - 3, style.hotFill());
            centered(g, each.title(), x + cell / 2F, 11, cell - 8, .85F, on ? style.accent() : style.text());
            if (on) g.fill((int) x + 10, (int) p.h - 5, (int) (x + cell) - 10, (int) p.h - 4, style.accent());
            p.zone(x, 0, cell, p.h).click = () -> setTab(each);
        }
    }

    private void pages(GuiGraphics g, HoloPanel p) {
        Page current = pages.get(page);
        if (pages.size() > 1) {
            button(g, p, 6, 5, 22, 20, Component.literal("◀"), false, true, () -> setPage(page - 1));
            button(g, p, p.w - 28, 5, 22, 20, Component.literal("▶"), false, true, () -> setPage(page + 1));
        }
        centered(g, t("page", "%s · page %s/%s", current.name(), page + 1, pages.size()), p.w / 2F, 6, p.w - 70, .78F,
            style.text());
        centered(g, t("hint", "Aim + attack: cast · use: next page · %s: close",
            AtlasWorld.OPEN.getTranslatedKeyMessage()), p.w / 2F, 18, p.w - 70, .6F, style.sub());
    }

    private void card(GuiGraphics g, HoloPanel p, int index) {
        List<VisualKind> kinds = pages.get(page).kinds();
        if (index >= kinds.size()) return;
        VisualKind kind = kinds.get(index);
        int color = kind.color | 0xFF000000;
        g.fill(8, 8, (int) p.w - 8, 52, 0x66000000);
        g.fill(8, 8, (int) p.w - 8, 9, color);
        ResourceLocation art = kind.addon() != null ? artOf(kind) : ResourceLocation.fromNamespaceAndPath(
            dev.calamidade_awakening.CalamidadeAwakening.ID, "textures/gui/sprites/skill/focus/" + kind.key + ".png");
        g.blit(art, (int) (p.w / 2 - 40), 10, 80, 40, 0, 0, 64, 32, 64, 32);
        text(g, Component.literal(kind.localizedTitle()), 8, 56, p.w - 16, 1F, style.text());
        Component desc = kind.addon() != null
            ? Component.literal(kind.addon().card().tier())
            : Component.translatable("skill.calamidade_awakening.focus." + kind.key + ".desc");
        wrapped(g, desc, 8, 68, p.w - 16, .66F, 2, style.sub());
        boolean hover = p.hot;
        if (kind.addon() != null && kind.addon().card().cooldown() > 0)
            text(g, t("cooldown", "Cooldown %s s", String.format(Locale.ROOT, "%.1f", kind.addon().card().cooldown() / 20F)),
                8, p.h - 12, p.w / 2, .62F, style.sub());
        right(g, hover ? t("cast.hot", "▶ Cast") : t("cast", "Cast"), p.w - 8, p.h - 12, p.w / 2 - 12, .7F,
            hover ? style.accent() : style.sub());
        p.zone(0, 0, p.w, p.h).click = () -> {
            if (!NetClientApi.canSend(CastRequest.PACKET)) return;
            NetClientApi.send(ClientHooks.castRequest(kind));
            world.cast();
        };
    }

    private static ResourceLocation artOf(VisualKind kind) {
        for (var entry : dev.calamidade_awakening.api.v1.Addons.entries())
            if (entry.effect().toString().equals(kind.networkKey())) return entry.art();
        return ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png");
    }

    // ------------------------------------------------------------------ quality

    private static final String[] MODE_HELP = {"adjusts itself", "integrated graphics", "default", "dedicated graphics",
        "strong machines"};

    private void modes(GuiGraphics g, HoloPanel p) {
        var coordinator = ClientHooks.coordinator();
        text(g, t("quality.title", "Effect quality"), 10, 7, 200, .9F, style.text());
        right(g, ClientHooks.qualityLine(), p.w - 10, 8, p.w - 200, .6F, style.sub());
        QualityMode mode = coordinator.mode();
        float bw = (p.w - 20 - 4 * 6) / 5F;
        for (QualityMode each : QualityMode.values()) {
            float x = 10 + each.ordinal() * (bw + 6);
            boolean on = each == mode;
            button(g, p, x, 24, bw, 22, Component.literal(each.name()), on, true, () -> {
                coordinator.select(each);
                editing = null;
            });
            centered(g, t("mode." + each.name().toLowerCase(Locale.ROOT), MODE_HELP[each.ordinal()]), x + bw / 2F, 50, bw - 4,
                .6F, on ? style.accent() : style.sub());
        }
        if (coordinator.testing()) centered(g, t("quality.test", "Test profile on (debug command): F8 or the buttons leave it"),
            p.w / 2F, 63, p.w - 20, .6F, style.accent());
    }

    private QualityProfile editing() {
        if (editing != null) return editing;
        return ClientHooks.coordinator().profile();
    }

    private static final String[] CAP_LABELS = {"Effects drawn", "Addon passes", "HERO passes", "HERO drawn", "Beams", "Lights"};
    private static final int[] CAP_MAX = {QualityProfile.Caps.MAX_EFFECTS, QualityProfile.Caps.MAX_PASSES,
        QualityProfile.Caps.MAX_HERO_PASSES, QualityProfile.Caps.MAX_HEROES, dev.calamidade_awakening.api.FrameLimits.MAX_BEAMS,
        dev.calamidade_awakening.api.FrameLimits.MAX_LIGHTS};
    private static final int[] CAP_MIN = {1, 0, 0, 0, 0, 0};

    private void tune(GuiGraphics g, HoloPanel p) {
        QualityProfile profile = editing();
        text(g, t("tune.title", "Fine tuning of %s (safety maxima)", profile.name()), 10, 7, p.w - 70, .8F, style.text());
        // Which profile to edit: cycles through the four.
        button(g, p, p.w - 58, 4, 50, 14, t("tune.next", "next ▶"), false, true, () -> editing = profile.higher() == profile
            ? QualityProfile.LOW : profile.higher());
        var caps = ClientConfig.caps(profile);
        int[] values = {caps.effects(), caps.passes(), caps.heroPasses(), caps.heroes(), caps.beams(), caps.lights()};
        for (int i = 0; i < 6; i++) {
            int index = i;
            int step = i >= 4 ? 4 : 1;
            slider(g, p, 10, 26 + i * 22, p.w - 20, t("cap." + ClientConfig.CAP_NAMES[i], CAP_LABELS[i]), values[i], CAP_MIN[i],
                CAP_MAX[i], step, v -> {
                    ClientConfig.cap(profile, index, v);
                    ClientHooks.coordinator().invalidate();
                }, values[i] + " / " + CAP_MAX[i]);
        }
        int fps = ClientConfig.autoTargetFps();
        slider(g, p, 10, 26 + 6 * 22, p.w - 20, t("tune.fps", "AUTO target FPS"), fps, 30, 240, 5, ClientConfig::autoTargetFps,
            fps + " FPS");
        button(g, p, 10, p.h - 24, 120, 16, t("tune.reset", "Restore defaults"), false, true, () -> {
            var d = profile.defaults;
            int[] def = {d.effects(), d.passes(), d.heroPasses(), d.heroes(), d.beams(), d.lights()};
            for (int i = 0; i < 6; i++) ClientConfig.cap(profile, i, def[i]);
            ClientHooks.coordinator().invalidate();
        });
        right(g, t("tune.saved", "Saved in the client config"), p.w - 10, p.h - 20, p.w - 140, .6F, style.sub());
    }

    private void table(GuiGraphics g, HoloPanel p) {
        text(g, t("table.title", "Caps per profile"), 10, 7, p.w - 20, .8F, style.text());
        String[] short_ = {"drawn", "passes", "HERO p.", "HERO", "beams", "lights"};
        float col = (p.w - 64) / 4F;
        QualityProfile current = ClientHooks.coordinator().profile();
        for (QualityProfile each : QualityProfile.values()) {
            float x = 60 + each.ordinal() * col;
            centered(g, Component.literal(each == QualityProfile.BALANCED ? "BAL" : each.name()), x + col / 2F, 24, col, .62F,
                each == current ? style.accent() : style.sub());
            var c = ClientConfig.caps(each);
            int[] v = {c.effects(), c.passes(), c.heroPasses(), c.heroes(), c.beams(), c.lights()};
            for (int i = 0; i < 6; i++)
                centered(g, Component.literal(String.valueOf(v[i])), x + col / 2F, 40 + i * 18, col, .7F,
                    each == current ? style.accent() : style.text());
        }
        for (int i = 0; i < 6; i++) text(g, t("table." + i, short_[i]), 8, 40 + i * 18, 50, .62F, style.sub());
        wrapped(g, t("table.note", "The highlighted column is the profile in use. AUTO moves between them."), 8, p.h - 26,
            p.w - 16, .58F, 2, style.sub());
    }

    // ------------------------------------------------------------------ accessibility and terrain

    private void access(GuiGraphics g, HoloPanel p) {
        text(g, t("access.title", "Accessibility"), 12, 8, p.w - 24, .9F, style.text());
        int intensity = Math.round(ClientConfig.screenEffects() * 100);
        slider(g, p, 12, 28, p.w - 24, t("access.screen", "Screen effects"), intensity, 0, 100, 5,
            v -> ClientConfig.screenEffects(v / 100F), intensity + "%");
        wrapped(g, t("access.screen.help", "Bloom, flashes, contrast, vignette, aberration, zoom and atmosphere exposure of the engine and addons. 0 turns them off."),
            12, 40, p.w - 24, .6F, 2, style.sub());
        float y = 62;
        toggle(g, p, 12, y, p.w - 24, t("access.shake", "Screen shake"), t("access.shake.help", "Camera shake of the engine and addons."),
            ClientConfig.cameraShake(), ClientConfig::cameraShake);
        toggle(g, p, 12, y + 34, p.w - 24, t("access.motion", "Motion effects"),
            t("access.motion.help", "Zoom blur and afterimage trails. Turn off if they cause motion sickness."),
            ClientConfig.motionEffects(), ClientConfig::motionEffects);
        toggle(g, p, 12, y + 68, p.w - 24, t("access.fog", "Atmosphere fog through the game fog"),
            t("access.fog.help", "Recommended. Turn off only if a shader pack ignores the game fog."),
            ClientConfig.vanillaFog(), ClientConfig::vanillaFog);
        boolean flashes = mc.options.hideLightningFlash().get();
        toggle(g, p, 12, y + 102, p.w - 24, t("access.flashes", "Hide flashes (game option)"),
            t("access.flashes.help", "The game's own option: the engine and addons skip flashes and pulses."), flashes, v -> {
                mc.options.hideLightningFlash().set(v);
                mc.options.save();
            });
        wrapped(g, t("access.saved", "Changes apply at once and are saved in calamidade_awakening-client.toml."), 12, p.h - 22,
            p.w - 24, .6F, 2, style.sub());
    }

    private void terrain(GuiGraphics g, HoloPanel p) {
        var rules = ServerConfig.terrain();
        text(g, t("terrain.title", "Terrain (server, read only)"), 12, 8, p.w - 24, .9F, style.text());
        boolean here = mc.level != null && rules.allows(mc.level.dimension().location().toString());
        Component yes = t("yes", "yes"), no = t("no", "no");
        Object[][] rows = {
            {t("terrain.enabled", "Destruction"), rules.enabled() ? t("on", "on") : t("off", "off")},
            {t("terrain.here", "In this dimension"), here && rules.enabled() ? yes : no},
            {t("terrain.volume", "Most blocks per edit"), Component.literal(String.format(Locale.ROOT, "%,d", rules.maxVolume()))},
            {t("terrain.tick", "Blocks per tick"), Component.literal(String.format(Locale.ROOT, "%,d", rules.blocksPerTick()))},
            {t("terrain.dimensions", "Dimensions"), Component.literal(String.join(", ", rules.dimensions()))},
            {t("terrain.entities", "Blocks with data (chests...)"), rules.blockEntities() ? yes : no},
            {t("terrain.griefing", "Mobs follow mobGriefing"), rules.mobGriefing() ? yes : no},
            {t("terrain.undo", "Undo"), t("terrain.undo.value", "%s min · %s edits", rules.undoMinutes(), rules.undoEdits())},
            {t("terrain.structure", "Largest structure"), t("terrain.structure.value", "%s blocks per axis", rules.maxStructure())},
            {t("terrain.explosion", "Protection mods (explosion event)"), rules.explosionEvent() ? yes : no},
            {t("terrain.spawn", "Spawn protection"), rules.spawnProtection() ? yes : no}};
        for (int i = 0; i < rows.length; i++) {
            float y = 28 + i * 18;
            if (i % 2 == 0) g.fill(8, (int) y - 3, (int) p.w - 8, (int) y + 12, 0x14FFFFFF);
            text(g, (Component) rows[i][0], 12, y, p.w * .55F, .7F, style.sub());
            right(g, (Component) rows[i][1], p.w - 12, y, p.w * .42F, .7F, style.text());
        }
        wrapped(g, t("terrain.note", "Set in the server config (serverconfig/calamidade_awakening-server.toml) and synced when you join. Changing it needs the server."),
            12, p.h - 24, p.w - 24, .58F, 2, style.sub());
    }

    // ------------------------------------------------------------------ diagnostics

    private void diag(GuiGraphics g, HoloPanel p) {
        List<Component> lines = PerfOverlay.lines();
        float y = 8;
        for (Component line : lines) {
            if (y > p.h - 12) break;
            text(g, line, 8, y, p.w - 16, .62F, y == 8 ? style.accent() : style.text());
            y += 7.6F;
        }
    }

    private void graph(GuiGraphics g, HoloPanel p) {
        double[] frames = PerfOverlay.recentFrames();
        text(g, t("graph.title", "Frame time (last %s frames)", frames.length), 8, 6, p.w - 16, .7F, style.text());
        float gx = 30, gy = 20, gw = p.w - 38, gh = p.h - 44;
        double top = 50;
        for (double f : frames) top = Math.max(top, Math.min(200, f * 1.1));
        g.fill((int) gx, (int) gy, (int) (gx + gw), (int) (gy + gh), 0x30000000);
        for (double guide : new double[] {16.7, 33.3}) {
            if (guide > top) continue;
            int yy = (int) (gy + gh - guide / top * gh);
            g.fill((int) gx, yy, (int) (gx + gw), yy + 1, 0x50FFFFFF);
            right(g, Component.literal(guide == 16.7 ? "60" : "30"), gx - 3, yy - 3, 24, .55F, style.sub());
        }
        int n = frames.length;
        for (int i = 0; i < n; i++) {
            float x = gx + i * gw / Math.max(1, n);
            float hgt = (float) (Math.min(top, frames[i]) / top * gh);
            int color = frames[i] > 33.3 ? 0xFFFF7A5A : frames[i] > 16.7 ? 0xFFFFD98A : 0xFF8AE6C8;
            g.fill((int) x, (int) (gy + gh - hgt), (int) Math.max(x + 1, x + gw / Math.max(1, n)), (int) (gy + gh), color);
        }
        double[] s = dev.calamidade_awakening.api.FrameStats.summary(frames);
        text(g, t("graph.stats", "median %s ms · p95 %s ms", fmt(s[0]), fmt(s[1])), 8, p.h - 16, p.w - 16, .66F, style.sub());
    }

    private void tests(GuiGraphics g, HoloPanel p) {
        text(g, t("tests.title", "Quick tests"), 8, 6, p.w - 16, .8F, style.text());
        boolean op = mc.player != null && mc.player.hasPermissions(2);
        float bw = (p.w - 16 - 3 * 4) / 4F;
        int[] loads = {6, 24, 60};
        for (int i = 0; i < 3; i++) {
            int n = loads[i];
            button(g, p, 8 + i * (bw + 4), 22, bw, 18, t("tests.bench", "Bench %s", n), false, op,
                () -> command("calamidade bench " + n + " mix"));
        }
        button(g, p, 8 + 3 * (bw + 4), 22, bw, 18, t("tests.stop", "Stop"), false, op, () -> command("calamidade bench stop"));
        if (!op) wrapped(g, t("tests.permission", "Bench needs permission level 2 on the server."), 8, 44, p.w - 16, .6F, 2,
            style.sub());
        else text(g, t("tests.mix", "Ring, cube, web and sky of the vfx-sample, in front of you."), 8, 44, p.w - 16, .6F,
            style.sub());
        boolean running = PerfOverlay.recording();
        button(g, p, 8, 62, 110, 18, running ? t("tests.recording", "Recording…") : t("tests.record", "Record 8 s"), running,
            !running, () -> PerfOverlay.startRecording(8));
        double[] last = PerfOverlay.lastRecording();
        if (last != null) {
            text(g, t("tests.result", "median %s ms", fmt(last[1])), 8, 86, p.w - 16, .9F, style.accent());
            text(g, t("tests.result2", "p95 %s ms · %s frames", fmt(last[2]), (int) last[0]), 8, 100, p.w - 16, .7F, style.text());
        } else {
            wrapped(g, t("tests.none", "The result shows here; the CSV goes to calamidade_bench/."), 8, 88, p.w - 16, .62F, 2,
                style.sub());
        }
    }

    private static String fmt(double ms) { return String.format(Locale.ROOT, "%.2f", ms); }

    private void command(String command) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
    }
}
