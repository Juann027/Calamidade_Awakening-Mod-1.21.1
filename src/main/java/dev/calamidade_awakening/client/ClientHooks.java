package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FilmApi;
import cyou.rimuru.rimulib.mc1211.client.api.NetClientApi;
import cyou.rimuru.rimulib.mc1211.client.api.MenuApi;
import cyou.rimuru.rimulib.mc1211.api.SkillApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.item.AwakeningSkill;
import dev.calamidade_awakening.item.AwakeningSkills;
import dev.calamidade_awakening.net.CastRequest;
import dev.calamidade_awakening.net.StageEvent;
import dev.calamidade_awakening.client.geometry.MeshRenderer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = CalamidadeAwakening.ID, value = Dist.CLIENT)
public final class ClientHooks {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final StageScene SCENE = new StageScene();
    private static final FrameCoordinator COORDINATOR = new FrameCoordinator(SCENE);
    private static final StageFilms FILMS = new StageFilms();
    private static final AtmosphereFrame ATMOSPHERE = new AtmosphereFrame(SCENE);
    private static final BeamFrame BEAMS = new BeamFrame(SCENE);
    private static final LightFrame LIGHTS = new LightFrame(SCENE);
    /** Terrain edit states from the server and the terrain pass (0.11.0). */
    private static final TerrainClient TERRAIN = new TerrainClient();
    private static final TerrainFrame TERRAIN_FRAME = new TerrainFrame(SCENE, TERRAIN);
    private static final TerrainMeshes TERRAIN_MESHES = new TerrainMeshes(TERRAIN);
    private static final RimuStageBackend BACKEND = new RimuStageBackend(COORDINATOR, FILMS, ATMOSPHERE, BEAMS, LIGHTS,
        TERRAIN_FRAME);
    private static final MeshRenderer GEOMETRY = new MeshRenderer();
    static {
        CostProbe.install(COORDINATOR);
        PerfOverlay.qualityLine = ClientHooks::qualityLine;
    }
    private static final Map<VisualKind, KeyMapping> DEMOS = new LinkedHashMap<>();
    private static final KeyMapping QUALITY = new KeyMapping("key.calamidade_awakening.quality",
        GLFW.GLFW_KEY_F8, "key.categories.calamidade_awakening");
    private static final KeyMapping CANCEL = new KeyMapping("key.calamidade_awakening.cancel",
        GLFW.GLFW_KEY_O, "key.categories.calamidade_awakening");

    private static int defaultKey(VisualKind kind) {
        if (kind == VisualKind.LATTICE) return GLFW.GLFW_KEY_U;
        if (kind == VisualKind.VEIL) return GLFW.GLFW_KEY_I;
        if (kind == VisualKind.PRISM) return GLFW.GLFW_KEY_K;
        if (kind == VisualKind.AURORA) return GLFW.GLFW_KEY_V;
        if (kind == VisualKind.NEXUS) return GLFW.GLFW_KEY_Y;
        return GLFW.GLFW_KEY_UNKNOWN;
    }

    // No bus attribute: FML routes each listener by event type (IModBusEvent -> mod bus).
    @EventBusSubscriber(modid = CalamidadeAwakening.ID, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent
        public static void client(FMLClientSetupEvent event) {
            // Accessibility settings editable from the mod list (0.6.2).
            net.neoforged.fml.ModList.get().getModContainerById(dev.calamidade_awakening.CalamidadeAwakening.ID)
                .ifPresent(container -> container.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    net.neoforged.neoforge.client.gui.ConfigurationScreen::new));
            NetClientApi.receive(dev.calamidade_awakening.net.EndEffect.PACKET, (packet, context) ->
                Minecraft.getInstance().execute(() -> {
                    SCENE.remove(packet.order());
                    TERRAIN.forget(packet.order());
                    FILMS.end(packet.order());
                    COORDINATOR.invalidate();
                }));
            NetClientApi.receive(dev.calamidade_awakening.net.TerrainSync.PACKET, (packet, context) ->
                Minecraft.getInstance().execute(() -> TERRAIN.receive(packet)));
            NetClientApi.receive(dev.calamidade_awakening.net.ReleaseEffect.PACKET, (packet, context) ->
                Minecraft.getInstance().execute(() -> {
                    SCENE.release(packet.order(), packet.age());
                    COORDINATOR.invalidate();
                }));
            NetClientApi.receive(StageEvent.PACKET, (packet, context) ->
                Minecraft.getInstance().execute(() -> receive(packet)));
            event.enqueueWork(() -> {
                BACKEND.install();
                cyou.rimuru.rimulib.mc1211.client.api.UiApi.onMenuDraw((graphics,partial) -> {
                    var set = cyou.rimuru.rimulib.mc1211.client.magic.WhiteSpace.openSet();
                    if (set != AwakeningSkills.SET) return;
                    var chosen = cyou.rimuru.rimulib.mc1211.client.magic.ClientSkills.selected(set);
                    if (!(chosen instanceof AwakeningSkill skill)) return;
                    var panel = dev.calamidade_awakening.api.v1.client.AddonLayers.panel(skill.visual.networkKey());
                    if (panel == null) return;
                    graphics.pose().pushPose();
                    try { panel.accept(new dev.calamidade_awakening.api.v1.client.AddonLayers.Panel(graphics,partial)); }
                    finally { graphics.pose().popPose(); }
                });
                FilmApi.enableKey();
                MenuApi.source(CalamidadeAwakening.ID).order(5);
            });
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            for (VisualKind kind : VisualKind.values()) {
                KeyMapping key = new KeyMapping("key.calamidade_awakening." + kind.key, defaultKey(kind), "key.categories.calamidade_awakening");
                DEMOS.put(kind, key);
                event.register(key);
            }
            event.register(StarAtlas.OPEN);
            event.register(PerfOverlay.TOGGLE);
            event.register(QUALITY);
            event.register(CANCEL);
        }

        @SubscribeEvent
        public static void shaders(RegisterShadersEvent event) throws IOException {
            GEOMETRY.shader(null);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "energy"),
                DefaultVertexFormat.POSITION_COLOR), GEOMETRY::shader);
        }
    }

    private static void receive(StageEvent packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (SCENE.updateLevel(mc.level)) {
            FILMS.cancel();
            TERRAIN.clear();
        }
        if (!SCENE.accept(packet, mc.level)) return;
        COORDINATOR.invalidate();
        VisualKind kind = VisualKind.byId(packet.visual());
        boolean caster = packet.caster().equals(mc.player.getUUID());
        long age = mc.level.getGameTime() - packet.tick();
        var started = dev.calamidade_awakening.api.v1.client.AddonLayers.start(kind.networkKey());
        if (started != null) {
            try {
                started.accept(new dev.calamidade_awakening.api.v1.client.AddonLayers.Start(mc.level, packet.center(),
                    age, (packet.seed() & 0xFFFFL) / 65535f, caster, packet.order()));
            } catch (RuntimeException failure) {
                LOGGER.error("Start listener for {} failed", kind.networkKey(), failure);
            }
        }
        boolean customFilm = dev.calamidade_awakening.api.v1.client.AddonLayers.film(kind.networkKey()) != null;
        // Late recipients receive effects at their actual age, but never a delayed film.
        if ((kind.film || customFilm || kind.addon() != null && !kind.addon().card().camera().isEmpty()) && caster
            && Math.abs(age) <= 8) FILMS.start(packet.center(), kind, packet.order());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (SCENE.updateLevel(mc.level)) {
            FILMS.cancel();
            TERRAIN.clear();
            TERRAIN_MESHES.clear();
        }
        SCENE.tick(mc.level);
        // Terrain states of instances that expired on this client (the server only sends end_effect on stops).
        TERRAIN.orders().removeIf(order -> !SCENE.has(order));
        if (mc.level != null) TERRAIN.dust(mc.level, SCENE.events());
        COORDINATOR.invalidate();
        while (CANCEL.consumeClick()) FILMS.cancel();
        PerfOverlay.tick();
        CostProbe.tick();
        while (QUALITY.consumeClick()) {
            var mode = COORDINATOR.cycleQuality();
            if (mc.player != null) mc.player.displayClientMessage(mode == dev.calamidade_awakening.api.QualityMode.AUTO
                ? Component.translatableWithFallback("perf.calamidade_awakening.mode.auto", "Calamidade_Awakening: AUTO (now %s)",
                    COORDINATOR.profile().name())
                : Component.literal("Calamidade_Awakening: " + mode.name()), true);
        }
        DEMOS.forEach((kind, key) -> poll(mc, key, kind));
        if (mc.player != null && SkillApi.holds(mc.player, AwakeningSkills.SET)) {
            for (AwakeningSkill skill : AwakeningSkills.values()) {
                KeyMapping key = MenuApi.key(skill);
                if (key != null && !DEMOS.containsValue(key)) poll(mc, key, skill.visual);
            }
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        PerfOverlay.frameStart(SCENE.size(), SCENE.sizeBySource(), COORDINATOR.limits());
        if (PerfOverlay.lastFrameMs() > 0)
            COORDINATOR.frame(System.currentTimeMillis(), PerfOverlay.lastFrameMs(), PerfOverlay.lastPassCut());
        COORDINATOR.invalidate();
        ATMOSPHERE.invalidate();
    }

    // 0.8.0: atmosphere fog through the game's own fog. Lowest priority, so it applies after other mods' changes.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void fogColor(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        ATMOSPHERE.fogColor(event);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void fogDistances(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
        ATMOSPHERE.fogDistances(event);
        // 0.9.0: the lights pass fades with the terrain fog the game really uses this frame (after every listener).
        if (event.getMode() == net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN)
            LIGHTS.fog(event.getNearPlaneDistance(), event.getFarPlaneDistance(),
                event.getFogShape() == com.mojang.blaze3d.shaders.FogShape.CYLINDER);
    }

    /**
     * /calamidade_client record <seconds>: grava tempos de quadro em calamidade_bench/*.csv. 0.11.2: debug unlimited
     * [effects passes] e debug off (perfil interno de teste UNLIMITED) e debug measure effects|passes|all (CostProbe).
     */
    @SubscribeEvent
    public static void clientCommands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("calamidade_client")
            .then(net.minecraft.commands.Commands.literal("debug")
                .then(net.minecraft.commands.Commands.literal("unlimited")
                    .executes(ctx -> unlimited(dev.calamidade_awakening.api.FrameLimits.unlimited()))
                    .then(net.minecraft.commands.Commands.argument("effects",
                            com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, dev.calamidade_awakening.api.FrameLimits.MAX_EFFECTS))
                        .then(net.minecraft.commands.Commands.argument("passes",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, dev.calamidade_awakening.api.FrameLimits.MAX_PASSES))
                            .executes(ctx -> unlimited(dev.calamidade_awakening.api.FrameLimits.unlimited(
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "effects"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "passes")))))))
                .then(net.minecraft.commands.Commands.literal("off").executes(ctx -> unlimited(null)))
                .then(net.minecraft.commands.Commands.literal("measure")
                    .then(net.minecraft.commands.Commands.literal("effects").executes(ctx -> measure("effects")))
                    .then(net.minecraft.commands.Commands.literal("passes").executes(ctx -> measure("passes")))
                    .then(net.minecraft.commands.Commands.literal("all").executes(ctx -> measure("all")))))
            .then(net.minecraft.commands.Commands.literal("record")
                .then(net.minecraft.commands.Commands.argument("seconds",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 300))
                    .executes(ctx -> {
                        int seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds");
                        boolean started = PerfOverlay.record(seconds);
                        var player = Minecraft.getInstance().player;
                        if (player != null) player.displayClientMessage(started
                            ? Component.translatableWithFallback("perf.calamidade_awakening.started",
                                "Recording %s s of frame times…", seconds)
                            : Component.translatableWithFallback("perf.calamidade_awakening.busy",
                                "A recording is already running"), false);
                        return started ? 1 : 0;
                    }))));
    }

    /** True while a HERO effect is resident on this client. */
    public static boolean heroActive() { return SCENE.heroActive(); }

    /** Selection and quality mode of this client (0.11.2: tests and the Atlas). */
    public static FrameCoordinator coordinator() { return COORDINATOR; }

    /**
     * F7 line of the quality mode (0.11.2): the mode, the profile in use and, under AUTO, the last change with its p95
     * and threshold and how long ago.
     */
    public static Component qualityLine() {
        String p = "perf.calamidade_awakening.quality.";
        if (COORDINATOR.testing())
            return Component.translatableWithFallback(p + "test", "Quality: %s (test profile)", COORDINATOR.limits().label());
        var mode = COORDINATOR.mode();
        if (mode != dev.calamidade_awakening.api.QualityMode.AUTO)
            return Component.translatableWithFallback(p + "forced", "Quality: %s (forced with F8)", mode.name());
        var auto = COORDINATOR.auto();
        var r = auto.reason();
        long ago = Math.max(0, (System.currentTimeMillis() - r.atMs()) / 1000);
        String target = String.format(java.util.Locale.ROOT, "%.1f", auto.target());
        return switch (r.change()) {
            case START -> Component.translatableWithFallback(p + "start", "Quality: AUTO → %s · started %s s ago · target %s ms",
                auto.current().name(), ago, target);
            case DOWN -> Component.translatableWithFallback(p + "down",
                "Quality: AUTO → %s · down from %s %s s ago (p95 %s ms > %s ms)", auto.current().name(), r.from().name(),
                ago, String.format(java.util.Locale.ROOT, "%.1f", r.p95()), String.format(java.util.Locale.ROOT, "%.1f", r.thresholdMs()));
            case UP -> Component.translatableWithFallback(p + "up",
                "Quality: AUTO → %s · up from %s %s s ago (p95 %s ms < %s ms, effects cut)", auto.current().name(),
                r.from().name(), ago, String.format(java.util.Locale.ROOT, "%.1f", r.p95()),
                String.format(java.util.Locale.ROOT, "%.1f", r.thresholdMs()));
        };
    }

    private static int unlimited(dev.calamidade_awakening.api.FrameLimits limits) {
        COORDINATOR.test(limits);
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatableWithFallback(
            "perf.calamidade_awakening.profile", "Calamidade_Awakening: %s", COORDINATOR.limits().label()), false);
        return 1;
    }

    private static int measure(String which) {
        boolean started = CostProbe.start(which);
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(started
            ? Component.translatableWithFallback("perf.calamidade_awakening.measuring",
                "Measuring %s: %s steps of %s s; keep still and look ahead (F7 shows progress)", which,
                CostProbe.plan(which).size(), (CostProbe.WARMUP_MS + CostProbe.RECORD_MS) / 1000)
            : Component.translatableWithFallback("perf.calamidade_awakening.busy", "A recording is already running"), false);
        if (started) PerfOverlay.show();
        return started ? 1 : 0;
    }

    /** 0.11.0: structure stand-ins, after the translucent blocks (they fade over the real blocks after the commit). */
    @SubscribeEvent
    public static void terrainMeshes(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || Minecraft.getInstance().level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var look = event.getCamera().getLookVector();
        var plan = COORDINATOR.plan(event.getCamera().getPosition(), new Vec3(look.x(), look.y(), look.z()), partial);
        TERRAIN_MESHES.render(event, plan, partial);
    }

    @SubscribeEvent
    public static void geometry(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || Minecraft.getInstance().level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var look = event.getCamera().getLookVector();
        var plan = COORDINATOR.plan(event.getCamera().getPosition(), new Vec3(look.x(), look.y(), look.z()), partial);
        GEOMETRY.render(event, plan, partial);
        PerfOverlay.plan(plan);
        PerfOverlay.vertices(GEOMETRY.lastVertices());
    }

    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        PerfOverlay.draw(event.getGuiGraphics());
        var camera = mc.gameRenderer.getMainCamera();
        var plan = COORDINATOR.plan(camera.getPosition(), new Vec3(camera.getLookVector()),
            event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (plan.effects().isEmpty()) return;
        var drawn = new java.util.HashSet<String>();
        for (var sample : plan.effects()) {
            String id = sample.kind().networkKey();
            var layer = dev.calamidade_awakening.api.v1.client.AddonLayers.hud(id);
            if (layer != null && drawn.add(id)) {
                var time = sample.time();
                var graphics = event.getGuiGraphics();
                graphics.pose().pushPose();
                try { layer.accept(new dev.calamidade_awakening.api.v1.client.AddonLayers.Hud(graphics,time)); }
                finally { graphics.pose().popPose(); }
            }
        }
    }

    @SubscribeEvent public static void useFocus(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.isCanceled() || StarAtlas.isOpen() || mc.player == null || mc.screen != null
            || FilmApi.active() || MenuApi.capturesInput() || !mc.player.getItemInHand(event.getHand()).is(AwakeningSkills.FOCUS.get())) return;
        var selected = cyou.rimuru.rimulib.mc1211.client.magic.ClientSkills.selected(AwakeningSkills.SET);
        if (selected instanceof AwakeningSkill skill && NetClientApi.canSend(CastRequest.PACKET)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            NetClientApi.send(request(skill.visual));
        }
    }

    static CastRequest request(VisualKind kind) {
        if (kind.addon() != null && kind.addon().power() != null) {
            for (var skill : AwakeningSkills.values()) if (skill.visual == kind)
                return new CastRequest(kind.id,cyou.rimuru.rimulib.mc1211.client.magic.SkillTuning.power(skill));
        }
        return new CastRequest(kind.id,1);
    }

    private static void poll(Minecraft mc, KeyMapping key, VisualKind kind) {
        while (key.consumeClick()) {
            if (mc.player != null && mc.level != null && mc.screen == null
                && !StarAtlas.isOpen() && !FilmApi.active() && !MenuApi.capturesInput() && NetClientApi.canSend(CastRequest.PACKET)) {
                NetClientApi.send(request(kind));
            }
        }
    }
}
