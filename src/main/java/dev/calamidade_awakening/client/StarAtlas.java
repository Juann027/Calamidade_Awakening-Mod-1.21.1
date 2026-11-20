package dev.calamidade_awakening.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import cyou.rimuru.rimulib.mc1211.client.api.FilmApi;
import cyou.rimuru.rimulib.mc1211.client.api.MenuApi;
import cyou.rimuru.rimulib.mc1211.client.api.NetClientApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.net.CastRequest;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import java.util.List;

@EventBusSubscriber(modid = CalamidadeAwakening.ID, value = Dist.CLIENT)
public final class StarAtlas {
    private static final String PREFIX = "atlas.calamidade_awakening.";
    static final KeyMapping OPEN = new KeyMapping("key.calamidade_awakening.atlas", GLFW.GLFW_KEY_N,
        "key.categories.calamidade_awakening");
    private static final float SCALE = .013f;
    private static final int HALF_WIDTH = 47, HALF_HEIGHT = 21;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private record Node(VisualKind kind, int x, int y, int depth, ResourceLocation image) {
        Node(VisualKind kind, int x, int y, int depth) {
            this(kind, x, y, depth, ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID,
                "textures/gui/sprites/skill/focus/" + kind.key + ".png"));
        }
    }
    private static final List<Node> NODES = List.of(
        new Node(VisualKind.PRISM, 0, 0, 12),
        new Node(VisualKind.LATTICE, -101, -28, 0), new Node(VisualKind.VEIL, 101, -28, 0),
        new Node(VisualKind.AURORA, -101, 28, 3), new Node(VisualKind.NEXUS, 101, 28, 3));
    private static StarAtlas current;
    private final List<List<Node>> pages = new java.util.ArrayList<>();
    private int page;

    private void buildPages() {
        pages.add(NODES);
        var groups = new java.util.TreeMap<String,List<dev.calamidade_awakening.api.v1.Addons.Entry>>();
        for (var entry : dev.calamidade_awakening.api.v1.Addons.entries())
            groups.computeIfAbsent(entry.effect().getNamespace(), key -> new java.util.ArrayList<>()).add(entry);
        for (var entries : groups.values()) appendPages(entries);
    }

    private void appendPages(List<dev.calamidade_awakening.api.v1.Addons.Entry> entries) {
        for (int offset = 0; offset < entries.size(); offset += NODES.size()) {
            var nodes = new java.util.ArrayList<Node>();
            for (int i = 0; i < NODES.size() && offset+i < entries.size(); i++) {
                var entry = entries.get(offset+i);
                var slot = NODES.get(i);
                nodes.add(new Node(VisualKind.byId(VisualKind.localId(entry.effect().toString())),
                    slot.x, slot.y, slot.depth, entry.art()));
            }
            pages.add(List.copyOf(nodes));
        }
    }
    private final ClientLevel level;
    private final long opened = net.minecraft.Util.getMillis();
    private Node selected;
    private long selectedAt;
    private Node focus;
    private long focusSince;

    // Both the cursor ray and the renderer sample this orbit in local card coordinates.
    private Vec3 placement(Node node, long now) {
        double t = Math.clamp((now - opened - Math.abs(node.y) * 1.5) / 440.0, 0, 1);
        double rest = (1 - t) * (1 - t);
        double angle = .35 * rest;
        double x = node.x * Math.cos(angle) - node.y * Math.sin(angle);
        double y = node.x * Math.sin(angle) + node.y * Math.cos(angle);
        return new Vec3(x * (1 - .33 * rest), y * (1 - .33 * rest), node.depth - 16 * rest);
    }
    private final Vec3 origin, right, up, forward;
    private final Quaternionf orientation;
    private final ByteBufferBuilder memory = new ByteBufferBuilder(32768);
    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(memory);

    private StarAtlas(Minecraft mc) {
        buildPages();
        level = mc.level;
        Camera camera = mc.gameRenderer.getMainCamera();
        orientation = new Quaternionf(camera.rotation());
        forward = new Vec3(camera.getLookVector());
        right = new Vec3(camera.getLeftVector()).scale(-1);
        up = new Vec3(camera.getUpVector());
        origin = camera.getPosition().add(forward.scale(4));
    }
    static boolean isOpen() { return current != null; }
    private static void close() {
        if (current != null) current.memory.close();
        current = null;
    }
    private static void open(Minecraft mc) {
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || FilmApi.active() || MenuApi.capturesInput()) return;
        close();
        current = new StarAtlas(mc);
    }
    @SubscribeEvent public static void pauseButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen) || !screen.showsPauseMenu()) return;
        Minecraft mc = Minecraft.getInstance();
        event.addListener(Button.builder(Component.translatable(PREFIX + "title"), button -> {
            mc.setScreen(null);
            open(mc);
        }).bounds(screen.width - 97, 4, 93, 20).build());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (current != null && (current.selected != null && net.minecraft.Util.getMillis() - current.selectedAt >= 180 || mc.level != current.level || mc.player == null || !mc.player.isAlive()
            || mc.screen != null || FilmApi.active() || MenuApi.capturesInput()
            || mc.player.position().distanceToSqr(current.origin) > 24 * 24)) close();
        while (OPEN.consumeClick()) {
            if (current != null) close();
            else if (mc.screen == null) open(mc);
        }
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (current == null || !(event.isUseItem() || event.isAttack())) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (current.selected != null) return;
        if (event.isUseItem()) {
            current.page = (current.page + 1) % current.pages.size();
            current.focus = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = net.minecraft.Util.getMillis();
        Node hit = current.pick(mc, mc.gameRenderer.getMainCamera(), now);
        if (hit != null && NetClientApi.canSend(CastRequest.PACKET)) {
            current.selected = hit;
            current.selectedAt = now;
            NetClientApi.send(ClientHooks.request(hit.kind));
        }
    }

    private Node pick(Minecraft mc, Camera camera, long now) {
        Vec3 eye = camera.getPosition(), ray = new Vec3(camera.getLookVector());
        double direction = ray.dot(forward);
        if (direction < .001 || mc.player == null) return null;
        double nearest = Double.POSITIVE_INFINITY;
        Node result = null;
        for (Node node : pages.get(page)) {
            Vec3 placement = placement(node, now);
            double t = (origin.subtract(eye).dot(forward) - placement.z * SCALE) / direction;
            if (t <= 0 || t >= nearest) continue;
            Vec3 relative = eye.add(ray.scale(t)).subtract(origin);
            if (Math.abs(relative.dot(right) / SCALE - placement.x) > HALF_WIDTH
                || Math.abs(-relative.dot(up) / SCALE - placement.y) > HALF_HEIGHT) continue;
            nearest = t;
            result = node;
        }
        if (result != null && level.clip(new ClipContext(eye, eye.add(ray.scale(nearest)),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) return null;
        return result;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (current == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != current.level || mc.player == null) return;
        current.draw(mc, event);
    }
    private void draw(Minecraft mc, RenderLevelStageEvent event) {
        PoseStack pose = event.getPoseStack();
        Vec3 offset = origin.subtract(event.getCamera().getPosition());
        long now = net.minecraft.Util.getMillis();
        Node hovered = pick(mc, event.getCamera(), now);
        if (hovered != focus) { focus = hovered; focusSince = now; }
        pose.pushPose();
        try {
            pose.translate(offset.x, offset.y, offset.z);
            pose.mulPose(orientation);
            pose.scale(SCALE, -SCALE, SCALE);
            Matrix4f matrix = pose.last().pose();
            // Angled orbital path at the original world position. Dashed and faint so it reads as decoration,
            // fading in with the opening animation instead of appearing as a hard line between cards.
            float reveal = (float)Math.clamp((now - opened) / 440.0, 0, 1);
            int orbit = ((int)(0x4c * reveal) << 24) | 0x7ddacc;
            if (reveal > 0) for (int i = 0; i < 96; i++) {
                if (i % 3 == 2) continue;
                double a = i * Math.TAU / 96, b = (i + 1) * Math.TAU / 96;
                float x = (float)Math.cos(a)*143, xx = (float)Math.cos(b)*143;
                float y = (float)Math.sin(a)*43 + x*.12f, yy = (float)Math.sin(b)*43 + xx*.12f;
                strip(matrix, x, y, xx, yy, orbit);
            }
            centered(mc.font, Component.translatable(PREFIX + "title"), matrix, 0, -68, 0xffbafff3);
            if (pages.size() > 1) centered(mc.font, Component.translatable(PREFIX + "page",page+1,pages.size(),
                mc.options.keyUse.getTranslatedKeyMessage()), matrix, 0, 76, 0xffbafff3);
            if (hovered != null && hovered.kind.addon() != null) centered(mc.font,
                Component.literal(hovered.kind.addon().card().tier()),matrix,0,-54,0xffd2e9f4);
            centered(mc.font, Component.translatable(PREFIX + "hint", OPEN.getTranslatedKeyMessage()), matrix, 0, 63, 0xffd2e9f4);
            for (Node node : pages.get(page)) {
                pose.pushPose();
                Vec3 placement = placement(node, now);
                pose.translate(placement.x, placement.y, placement.z);
                Matrix4f m = pose.last().pose();
                int tint = node.kind.color | 0xff000000;
                rect(m,-HALF_WIDTH,-HALF_HEIGHT,HALF_WIDTH,HALF_HEIGHT,0, node == hovered ? 0xff21485d : 0xf5162236);
                rect(m,-HALF_WIDTH,-HALF_HEIGHT,HALF_WIDTH,-HALF_HEIGHT+2,.1f,tint);
                rect(m,-HALF_WIDTH,HALF_HEIGHT-1,HALF_WIDTH,HALF_HEIGHT,.1f,node == hovered ? 0xffffffff : 0xff517284);
                if (node == hovered || node == selected) {
                    float dwell = Math.clamp((now - focusSince) / 160f, 0, 1);
                    int line = node == selected ? 0xffffffff : tint;
                    float span = node == selected ? HALF_WIDTH : HALF_WIDTH * dwell;
                    rect(m,-span,HALF_HEIGHT-3,span,HALF_HEIGHT-1,.2f,line);
                    if (node.kind == VisualKind.NEXUS) {
                        float gap = 2 + 4 * (float)(.5 + .5 * Math.cos((now-focusSince)*.008));
                        rect(m,-40,-6,-26-gap,-5,.2f,line);
                        rect(m,26+gap,-6,40,-5,.2f,line);
                    }
                }
                VertexConsumer icon = buffers.getBuffer(RenderType.text(node.image));
                float x = -24, y = -18, w = 48, h = 21;
                icon.addVertex(m,x,y,.3f).setColor(-1).setUv(0,0).setLight(LIGHT);
                icon.addVertex(m,x,y+h,.3f).setColor(-1).setUv(0,1).setLight(LIGHT);
                icon.addVertex(m,x+w,y+h,.3f).setColor(-1).setUv(1,1).setLight(LIGHT);
                icon.addVertex(m,x+w,y,.3f).setColor(-1).setUv(1,0).setLight(LIGHT);
                pose.translate(0,0,.5f);
                label(mc.font, node.kind.localizedTitle(), pose);
                pose.popPose();
            }
            buffers.endBatch();
        } finally { pose.popPose(); }
    }
    /**
     * Card title inside the card: shrinks long names (down to MIN_LABEL) and cuts with an ellipsis only beyond that.
     * The vertical center stays where the original .77 scale placed it.
     */
    private static final float LABEL = .77f, MIN_LABEL = .58f, LABEL_CENTER = 9.6f;
    private static final int LABEL_WIDTH = HALF_WIDTH * 2 - 8;
    private void label(Font font, String title, PoseStack pose) {
        int width = Math.max(1, font.width(title));
        float scale = Math.min(LABEL, LABEL_WIDTH / (float) width);
        if (scale < MIN_LABEL) {
            scale = MIN_LABEL;
            int room = (int)(LABEL_WIDTH / MIN_LABEL) - font.width("…");
            title = font.plainSubstrByWidth(title, room).stripTrailing() + "…";
        }
        pose.pushPose();
        pose.scale(scale, scale, 1);
        float y = (LABEL_CENTER - 4.5f * scale) / scale;
        Component text = Component.literal(title);
        font.drawInBatch(text, -font.width(text) * .5f, y, 0xffffffff, false, pose.last().pose(), buffers,
            Font.DisplayMode.NORMAL, 0, LIGHT);
        pose.popPose();
    }
    private void centered(Font font,Component text,Matrix4f m,int x,int y,int color) {
        font.drawInBatch(text,x-font.width(text)*.5f,y,color,false,m,buffers,Font.DisplayMode.NORMAL,0,LIGHT);
    }
    private void strip(Matrix4f m,float x,float y,float xx,float yy,int col) {
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        double length=Math.hypot(xx-x,yy-y);
        float dx=(float)(-(yy-y)/length*.35),dy=(float)((xx-x)/length*.35);
        v.addVertex(m,x-dx,y-dy,-2).setColor(col); v.addVertex(m,x+dx,y+dy,-2).setColor(col);
        v.addVertex(m,xx+dx,yy+dy,-2).setColor(col); v.addVertex(m,xx-dx,yy-dy,-2).setColor(col);
    }
    private void rect(Matrix4f m,float x,float y,float xx,float yy,float z,int color) {
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        v.addVertex(m,x,y,z).setColor(color); v.addVertex(m,x,yy,z).setColor(color);
        v.addVertex(m,xx,yy,z).setColor(color); v.addVertex(m,xx,y,z).setColor(color);
    }
}
