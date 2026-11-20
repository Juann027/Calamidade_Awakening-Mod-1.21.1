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
    private static final RimuStageBackend BACKEND = new RimuStageBackend(COORDINATOR, FILMS);
    private static final MeshRenderer GEOMETRY = new MeshRenderer();
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

    @EventBusSubscriber(modid = CalamidadeAwakening.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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
                    FILMS.end(packet.order());
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
        if (SCENE.updateLevel(mc.level)) FILMS.cancel();
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
        if (SCENE.updateLevel(mc.level)) FILMS.cancel();
        SCENE.tick(mc.level);
        COORDINATOR.invalidate();
        while (CANCEL.consumeClick()) FILMS.cancel();
        PerfOverlay.tick();
        while (QUALITY.consumeClick()) {
            var quality = COORDINATOR.cycleQuality();
            if (mc.player != null) mc.player.displayClientMessage(
                Component.literal("Calamidade_Awakening: " + quality.name()), true);
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
        PerfOverlay.frameStart(SCENE.size(), COORDINATOR.quality());
        COORDINATOR.invalidate();
    }

    /** /calamidade_client record <seconds>: grava tempos de quadro em calamidade_bench/*.csv. */
    @SubscribeEvent
    public static void clientCommands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("calamidade_client")
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
                var time = new dev.calamidade_awakening.api.v1.client.AddonLayers.Time(
                    sample.progress(),sample.opacity(),sample.seed(),sample.kind().radius);
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
