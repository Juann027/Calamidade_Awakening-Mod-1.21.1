package dev.calamidade_awakening.client.boss;

import cyou.rimuru.rimulib.mc1211.client.api.NetClientApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.BossBarMath;
import dev.calamidade_awakening.api.v1.Boss;
import dev.calamidade_awakening.api.v1.client.BossBars;
import dev.calamidade_awakening.config.ClientConfig;
import dev.calamidade_awakening.net.BossSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Custom boss bars (0.13.0, client): keeps the {@code boss_v1} state of each vanilla boss bar and draws the bars whose
 * style is registered ({@link BossBars}), unless the player forces vanilla bars. Everything visual (smoothing, damage
 * ghost, phase flash) is computed here from the vanilla progress and the phase start tick: no packets per tick.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID, value = Dist.CLIENT)
public final class BossBarClient {
    /** Vanilla bar width, GUI pixels. */
    static final int WIDTH = 182;

    /** State of one bar. */
    static final class State {
        BossSync sync;
        float ghost = -1, last = -1;
        double lastHit = -1e9, lastFrame = -1;
        int phase = -1;
        Component label;
    }

    private static final Map<UUID, State> BARS = new HashMap<>();
    /** Bars drawn with a custom style in the last frame (F7, tests). */
    static int drawn;
    private static int drawing;

    private BossBarClient() {}

    @EventBusSubscriber(modid = CalamidadeAwakening.ID, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent
        public static void client(FMLClientSetupEvent event) {
            NetClientApi.receive(BossSync.PACKET, (packet, context) -> Minecraft.getInstance().execute(() -> receive(packet)));
            BossBars.style(Boss.Bar.SEGMENTED, BossBarClient::segmented);
            dev.calamidade_awakening.client.PerfOverlay.bossLine = () -> BARS.isEmpty() && AnchorCache.idle() ? null
                : Component.translatableWithFallback("calamidade_awakening.perf.boss", "Bosses: %s bars (%s custom) · %s",
                    BARS.size(), drawn, AnchorCache.line());
        }
    }

    static void receive(BossSync packet) {
        if (packet.removed()) {
            BARS.remove(packet.bar());
            return;
        }
        State state = BARS.computeIfAbsent(packet.bar(), id -> new State());
        state.sync = packet;
        state.label = packet.label().isEmpty() ? null : Component.translatable(packet.label());
    }

    /** Number of bars with boss_v1 state (tests). */
    public static int known() { return BARS.size(); }

    /** Bars drawn with a custom style in the last frame (tests, F7). */
    public static int drawn() { return drawn; }

    /** Phase index of the first known bar, or -1 (tests). */
    public static int firstPhase() {
        for (State state : BARS.values()) if (state.sync != null) return state.sync.phase();
        return -1;
    }

    /** Phase index of a known bar, or -1 (tests). */
    public static int phase(UUID bar) {
        State state = BARS.get(bar);
        return state == null || state.sync == null ? -1 : state.sync.phase();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { BARS.clear(); }

    @SubscribeEvent
    public static void bar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        State state = BARS.get(boss.getId());
        if (state == null || state.sync == null || ClientConfig.bossBarVanilla()) return;
        String styleId = state.sync.style();
        BossBars.Renderer renderer = styleId.isEmpty() ? null : BossBars.style(ResourceLocation.tryParse(styleId));
        if (renderer == null) return;  // unknown style: vanilla fallback
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        BossBars.View view = view(state, boss, now, mc);
        float scale = view.scale();
        GuiGraphics graphics = event.getGuiGraphics();
        int x = event.getX(), y = event.getY();
        int lift = Math.round(9 * (scale - 1));  // keeps a scaled title below the top of the screen
        graphics.pose().pushPose();
        int height;
        try {
            graphics.pose().translate(x + WIDTH / 2f, y + lift, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.pose().translate(-WIDTH / 2f, 0, 0);
            height = renderer.draw(graphics, 0, 0, view);
        } catch (RuntimeException failure) {
            graphics.pose().popPose();
            BossBars.style(ResourceLocation.tryParse(styleId), BossBarClient::segmented);
            com.mojang.logging.LogUtils.getLogger().error("Boss bar style {} failed; using the engine style", styleId, failure);
            return;
        }
        graphics.pose().popPose();
        event.setCanceled(true);
        event.setIncrement(Math.max(10, Math.round(Math.max(height, 10) * scale) + lift));
        drawing++;
    }

    @SubscribeEvent
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        drawn = drawing;
        drawing = 0;
    }

    static BossBars.View view(State state, LerpingBossEvent boss, double now, Minecraft mc) {
        BossSync sync = state.sync;
        float progress = boss.getProgress();
        if (state.last >= 0 && progress < state.last - 1e-4) state.lastHit = now;
        float dt = state.lastFrame < 0 ? 0 : (float) Math.max(0, now - state.lastFrame);
        state.ghost = state.ghost < 0 ? progress : BossBarMath.ghost(state.ghost, progress, (float) (now - state.lastHit), dt);
        state.last = progress;
        state.lastFrame = now;
        Boss.BarColor[] colors = Boss.BarColor.values();
        Boss.BarOverlay[] overlays = Boss.BarOverlay.values();
        Boss.BarColor color = colors[Math.clamp(sync.color(), 0, colors.length - 1)];
        boolean contrast = ClientConfig.bossBarHighContrast();
        boolean flashAllowed = !mc.options.hideLightningFlash().get();
        float flash = BossBarMath.flash((float) (now - sync.phaseTick()), flashAllowed, ClientConfig.motionEffects())
            * ClientConfig.screenEffects();
        return new BossBars.View(boss.getName(), progress, state.ghost, color, contrast ? 0xffffffff : color.argb(),
            overlays[Math.clamp(sync.overlay(), 0, overlays.length - 1)], ClientConfig.bossBarLabels() ? state.label : null,
            sync.marks().clone(), flash, sync.vulnerable(), BossBarMath.scale(ClientConfig.bossBarScale()), contrast, sync.phase());
    }

    /** The engine style: vanilla proportions, phase marks, damage ghost, phase flash, label, invulnerability frame. */
    static int segmented(GuiGraphics g, int x, int y, BossBars.View bar) {
        Font font = Minecraft.getInstance().font;
        int w = WIDTH, h = 5;
        g.drawCenteredString(font, bar.name(), x + w / 2, y - 9, 0xffffffff);
        int back = bar.highContrast() ? 0xff000000 : 0xc0202020;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, bar.vulnerable() ? back : 0xff8a8a8a);
        g.fill(x, y, x + w, y + h, bar.highContrast() ? 0xff000000 : 0xff3a3a3a);
        int fill = Math.round(w * Math.clamp(bar.progress(), 0, 1));
        int ghost = Math.round(w * Math.clamp(bar.ghost(), 0, 1));
        if (ghost > fill) g.fill(x + fill, y, x + ghost, y + h, bar.highContrast() ? 0xffbfbfbf : 0xc0f0e6d2);
        g.fill(x, y, x + fill, y + h, bar.argb());
        if (!bar.highContrast()) g.fill(x, y, x + fill, y + 1, 0x50ffffff);
        int notches = switch (bar.overlay()) {
            case NOTCHED_6 -> 6; case NOTCHED_10 -> 10; case NOTCHED_12 -> 12; case NOTCHED_20 -> 20; default -> 0;
        };
        for (int i = 1; i < notches; i++) {
            int nx = x + Math.round(w * i / (float) notches);
            g.fill(nx, y, nx + 1, y + h, 0x80000000);
        }
        for (float mark : bar.marks()) {
            int mx = x + Math.round(w * mark);
            g.fill(mx, y - 2, mx + 1, y + h + 2, bar.highContrast() ? 0xffffff00 : 0xe0ffffff);
        }
        if (bar.flash() > 0) {
            int a = Math.round(Math.clamp(bar.flash(), 0, 1) * 160);
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, (a << 24) | 0xffffff);
        }
        if (bar.label() != null) {
            g.drawCenteredString(font, bar.label(), x + w / 2, y + h + 3, bar.highContrast() ? 0xffffffff : 0xffd8d0c0);
            return 19 + 10;
        }
        return 19;
    }
}
