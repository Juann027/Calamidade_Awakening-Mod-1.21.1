package dev.calamidade_awakening.client.holo;

import cyou.rimuru.rimulib.mc1211.client.api.FilmApi;
import cyou.rimuru.rimulib.mc1211.client.api.MenuApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.holo.HoloGeometry;
import dev.calamidade_awakening.api.holo.HoloInputArbiter;
import dev.calamidade_awakening.api.holo.HoloPlacement;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * The Star Atlas in the world (0.11.2): N summons the board standing in front of where the player looked; the player
 * keeps walking, jumping and looking around (no {@code Screen}, no captured input). Since 0.11.2 the Atlas is the only
 * holographic panel of the mod: skills (engine cards and addon pages, cast through the server), quality, accessibility,
 * terrain (read only) and diagnostics.
 *
 * <p><b>Space.</b> The board ({@link Atlas}, a fixed 640 × 360 layout) becomes world through one matrix
 * ({@link HoloGeometry#layoutToWorld}): its eye is the player's eyes at summon time and its front faces the player's
 * yaw, stored in world coordinates and never recomputed from the camera. The camera is the real one (AFTER_LEVEL:
 * camera rotation and the projection with view bobbing), relative to the camera.
 *
 * <p><b>Frame.</b> {@code RenderFrameEvent.Pre}: clock, animation, hover by the aim and surface redraw (panels out of
 * view are not redrawn). AFTER_LEVEL: matrices, fading when the camera crosses a panel or sees it from behind, and the
 * 3D pass with depth test against the scenery (a wall in front hides a panel; the depth buffer is never cleared).
 *
 * <p><b>Input.</b> The aim is the vanilla ray (eyes and look of the camera entity at the frame's partial tick, the same
 * instant as {@code hitResult}) taken to the eye space of the drawn frame: hover, click, drag and wheel use the drawing
 * matrices. A panel owns the aim only by its front face, within {@link #REACH} and closer than the vanilla target;
 * {@link AtlasInput} decides the owner of each button at the press and keeps it until release.
 *
 * <p><b>Lifecycle.</b> Closes at once on death, respawn, dimension or level change, logout, spectator mode, an
 * incompatible screen or a RimuLib menu; dismissed with the closing animation by N, after a cast and beyond
 * {@link #DISMISS_DISTANCE} blocks. Stays visible (without input) under the inventory, chat and pause menu; hidden
 * without closing during a cinematic film and with F1. Recall (B) brings it in front of the aim. The pause menu has an
 * "Atlas Estelar" button.
 *
 * <p>Adapted from the RespectCraft holographic menu ({@code HoloWorld}, stages 4.8.4-A and 4.8.4-B, same author, who
 * authorized copying); no RespectCraft class is used. Visual state of this client only: no entity, block or packet.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID, value = Dist.CLIENT)
public final class AtlasWorld {
    static final String PREFIX = "atlas.calamidade_awakening.";
    public static final KeyMapping OPEN = new KeyMapping("key.calamidade_awakening.atlas", GLFW.GLFW_KEY_N,
        "key.categories.calamidade_awakening");
    /** Preferred and smallest distance of the board's front (blocks). */
    static final float PREFERRED = 2.4F, MIN_DISTANCE = .8F;
    /** Reach of the aim on the panels (blocks). */
    static final float REACH = 6F;
    /** Walking farther than this from the anchor dismisses the Atlas (blocks). */
    static final float DISMISS_DISTANCE = 24F;
    static final float FOV = 70F;

    private static AtlasWorld current;

    private final Minecraft mc = Minecraft.getInstance();
    final Atlas atlas;
    private final HoloGl gl = new HoloGl();
    private final Vector3f hit = new Vector3f(), local = new Vector3f();
    private final Vector3f rayO = new Vector3f(), rayD = new Vector3f(0F, 0F, -1F);
    private double worldDistance = Double.POSITIVE_INFINITY;
    final Matrix4f layout = new Matrix4f(), view = new Matrix4f(), projection = new Matrix4f(),
        viewLayout = new Matrix4f(), projViewLayout = new Matrix4f();
    private HoloPanel dragPanel;
    private HoloZone dragZone;
    private int dragSource = -1;
    private float dragStartX, dragStartY;
    private boolean dragMoved;
    private final ClientLevel level;
    private double anchorX, anchorY, anchorZ;
    private float yaw, scale;
    final float eyeZ = HoloGeometry.referenceDistance(Atlas.H, FOV);
    private boolean rendered;
    private long castAt = -1;

    private AtlasWorld(Minecraft mc) {
        level = mc.level;
        atlas = new Atlas(this);
    }

    // ================================================================ API

    public static boolean isOpen() { return current != null; }

    static AtlasWorld current() { return current; }

    /** N: summons in front of the aim; with the Atlas open, dismisses it. */
    public static void toggle() {
        if (current != null && !current.atlas.closing()) current.dismiss();
        else summon();
    }

    /** Summons (or, with the Atlas already open, recalls it to the current aim). */
    public static void summon() {
        Minecraft mc = Minecraft.getInstance();
        if (!canSummon(mc)) return;
        if (current != null) {
            if (current.atlas.closing()) current.closeNow();
            else {
                current.recallHere();
                return;
            }
        }
        AtlasWorld w = new AtlasWorld(mc);
        boolean fits = w.place(mc.player);
        current = w;
        w.atlas.open();
        w.hint(fits);
    }

    /** Recall: brings the open Atlas in front of the current aim, assembling again. */
    public static void recall() {
        if (current != null && !current.atlas.closing()) current.recallHere();
    }

    /** Closes at once and frees the framebuffers. */
    public static void closeAll() {
        if (current != null) current.closeNow();
    }

    void dismiss() {
        cancelDrag();
        atlas.close();
    }

    boolean closing() { return atlas.closing(); }

    /** Blocks per layout unit (constellation stars and lines are sized in layout units). */
    float unit() { return scale; }

    /** A card was cast: the Atlas dismisses itself shortly after, so the effect is seen (as before 0.11.2). */
    void cast() { castAt = System.currentTimeMillis(); }

    private static boolean canSummon(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && mc.level != null && player.isAlive() && !player.isSpectator() && !FilmApi.active()
            && !MenuApi.capturesInput();
    }

    // ================================================================ placement

    /** Chooses the anchor from the player's eyes and yaw (read-only: one {@code level.clip} per sample). */
    private boolean place(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        HoloPlacement.Result r = HoloPlacement.choose(eye.x, eye.y, eye.z, player.getYRot(), PREFERRED, MIN_DISTANCE,
            atlas.samples(eyeZ), (fx, fy, fz, tx, ty, tz) -> player.level().clip(new ClipContext(new Vec3(fx, fy, fz),
                new Vec3(tx, ty, tz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS);
        anchorX = eye.x;
        anchorY = eye.y;
        anchorZ = eye.z;
        yaw = r.yaw();
        scale = r.distance() / eyeZ;
        return r.fits();
    }

    private void recallHere() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level != level) return;
        cancelDrag();
        boolean fits = place(player);
        rendered = false;
        atlas.open();
        hint(fits);
    }

    private void hint(boolean fits) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(fits
            ? Component.translatable(PREFIX + "summoned", OPEN.getTranslatedKeyMessage(), AtlasInput.RECALL.getTranslatedKeyMessage(),
                AtlasInput.CURSOR.getTranslatedKeyMessage())
            : Component.translatable(PREFIX + "cramped", AtlasInput.RECALL.getTranslatedKeyMessage()), true);
    }

    private void closeNow() {
        cancelDrag();
        atlas.release();
        if (current == this) {
            current = null;
            AtlasInput.closed();
        }
    }

    /** Hidden without closing during a cinematic film and with F1. */
    private boolean hidden() { return FilmApi.active() || mc.options.hideGui; }

    // ================================================================ frame

    private void frame() {
        if (mc.level == null || mc.player == null) {
            closeNow();
            return;
        }
        if (atlas.closeFinished()) {
            closeNow();
            return;
        }
        if (hidden()) {
            cancelDrag();
            return;
        }
        AtlasInput.poll();
        if (current != this) return;
        atlas.animate();
        updateDrag();
        hover();
        for (HoloPanel p : atlas.panels()) p.culled = rendered && p.offscreen();
        // Same model-view as the GUI: the orthographic projection of the surfaces expects GUI depth.
        var mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.translation(0F, 0F, 10000F - net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane());
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            atlas.drawSurfaces();
        } finally {
            mv.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        }
    }

    private void hover() {
        for (HoloPanel p : atlas.panels()) {
            p.hot = false;
            p.aimX = p.aimY = -9999F;
        }
        if (dragZone != null) {
            dragPanel.hot = true;
            dragPanel.aimX = hit.x;
            dragPanel.aimY = hit.y;
            return;
        }
        HoloPanel p = target(hit);
        if (p != null) {
            p.hot = true;
            p.aimX = hit.x;
            p.aimY = hit.y;
        }
    }

    // ================================================================ input

    /** The panels may take input now (drawn, visible, no screen on top, player playing). */
    boolean acceptsInput() {
        LocalPlayer player = mc.player;
        return rendered && !hidden() && mc.screen == null && mc.getOverlay() == null && mc.level == level && player != null
            && player.isAlive() && !player.isSpectator() && !atlas.closing();
    }

    /** The aim belongs to the Atlas: front face of a visible panel, within reach and closer than the vanilla target. */
    boolean ownsAim() {
        if (!acceptsInput()) return false;
        HoloPanel p = pickRay(local);
        return HoloInputArbiter.hologramWins(p != null, p == null ? -1.0 : local.z, worldDistance, REACH);
    }

    /** Panel under the aim (or the free cursor) and the local point in {@code out}. */
    private HoloPanel target(Vector3f out) {
        if (!acceptsInput()) return null;
        if (AtlasInput.cursorMode()) return pickCursor(out);
        HoloPanel p = pickRay(out);
        if (p == null || !HoloInputArbiter.hologramWins(true, out.z, worldDistance, REACH)) return null;
        return p.interactive ? p : null;
    }

    /** Nearest panel hit by the aim ray. */
    private HoloPanel pickRay(Vector3f out) {
        HoloPanel best = null;
        float bestT = Float.MAX_VALUE;
        Vector3f tmp = new Vector3f();
        for (HoloPanel p : atlas.panels()) {
            if (!p.visible || !p.rayHit(rayO.x, rayO.y, rayO.z, rayD.x, rayD.y, rayD.z, tmp)) continue;
            float t = tmp.z; // eye space is in blocks and the direction is a unit vector: distance in blocks
            if (t < bestT) {
                bestT = t;
                best = p;
                out.set(tmp.x, tmp.y, t);
            }
        }
        return best;
    }

    private HoloPanel pickCursor(Vector3f out) {
        float nx = (float) (mc.mouseHandler.xpos() / Math.max(1, mc.getWindow().getScreenWidth()) * 2.0 - 1.0);
        float ny = (float) (1.0 - mc.mouseHandler.ypos() / Math.max(1, mc.getWindow().getScreenHeight()) * 2.0);
        HoloPanel best = null;
        float bestT = Float.MAX_VALUE;
        Vector3f tmp = new Vector3f();
        for (HoloPanel p : atlas.panels()) {
            if (!p.visible || !p.interactive || !p.hit(nx, ny, tmp)) continue;
            if (tmp.z < bestT) {
                bestT = tmp.z;
                best = p;
                out.set(tmp);
            }
        }
        return best;
    }

    /** Button (or keyboard action) down and owned by the Atlas: same click path for attack and use. */
    void press(int source, int action) {
        if (dragZone != null || atlas.closing()) return;
        HoloPanel p = target(hit);
        if (p == null) return;
        HoloZone z = atlas.press(p, hit.x, hit.y, action == HoloInputArbiter.USE);
        if (z != null && current == this) {
            dragPanel = p;
            dragZone = z;
            dragSource = source;
            dragStartX = hit.x;
            dragStartY = hit.y;
            dragMoved = false;
            z.drag.move(hit.x, hit.y);
        }
    }

    void release(int source) {
        if (dragZone == null || source != dragSource) return;
        float x = dragStartX, y = dragStartY;
        if (aimPlane(dragPanel, hit)) {
            x = hit.x;
            y = hit.y;
        }
        HoloZone z = dragZone;
        boolean click = !dragMoved;
        clearDrag();
        z.drag.release(x, y, click);
    }

    void cancelDrag() {
        if (dragZone == null) return;
        HoloZone z = dragZone;
        clearDrag();
        z.drag.release(dragStartX, dragStartY, false);
    }

    private void clearDrag() {
        dragZone = null;
        dragPanel = null;
        dragSource = -1;
    }

    private void updateDrag() {
        if (dragZone == null) return;
        if (!dragPanel.visible || !acceptsInput()) {
            cancelDrag();
            return;
        }
        if (!aimPlane(dragPanel, hit)) return;
        if (Math.abs(hit.x - dragStartX) + Math.abs(hit.y - dragStartY) > 2F) dragMoved = true;
        dragZone.drag.move(hit.x, hit.y);
    }

    private boolean aimPlane(HoloPanel p, Vector3f out) {
        if (AtlasInput.cursorMode()) {
            float nx = (float) (mc.mouseHandler.xpos() / Math.max(1, mc.getWindow().getScreenWidth()) * 2.0 - 1.0);
            float ny = (float) (1.0 - mc.mouseHandler.ypos() / Math.max(1, mc.getWindow().getScreenHeight()) * 2.0);
            return HoloGeometry.intersect(p.inverse, nx, ny, out);
        }
        return p.rayPlane(rayO.x, rayO.y, rayO.z, rayD.x, rayD.y, rayD.z, out);
    }

    void scroll(double amount) {
        if (atlas.closing() || amount == 0) return;
        HoloPanel p = target(hit);
        atlas.scroll(p, hit.x, hit.y, amount);
    }

    // ================================================================ world pass

    private void renderWorld(RenderLevelStageEvent event) {
        if (hidden() || mc.level != level) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        HoloGeometry.layoutToWorld(layout, (float) (anchorX - cam.x), (float) (anchorY - cam.y), (float) (anchorZ - cam.z),
            yaw, scale, Atlas.W / 2F, Atlas.H / 2F, eyeZ);
        view.set(event.getModelViewMatrix());
        projection.set(event.getProjectionMatrix());
        view.mul(layout, viewLayout);
        projection.mul(viewLayout, projViewLayout);
        for (HoloPanel p : atlas.panels()) p.freeze(viewLayout, projViewLayout);
        aimRay(camera, cam);
        fadeNearAndBehind();
        gl.begin(projection);
        try {
            atlas.render(gl);
        } finally {
            gl.end();
        }
        rendered = true;
    }

    /**
     * Aim ray of this frame in eye space: eyes and look of the camera entity at the instant vanilla chose
     * {@code hitResult}, and that target's distance. Hover and clicks until the next frame use what the crosshair showed.
     */
    private void aimRay(Camera camera, Vec3 cam) {
        Entity viewer = mc.getCameraEntity();
        if (viewer == null) return;
        float pt = camera.getPartialTickTime();
        Vec3 eye = viewer.getEyePosition(pt), look = viewer.getViewVector(pt);
        view.transformPosition(rayO.set((float) (eye.x - cam.x), (float) (eye.y - cam.y), (float) (eye.z - cam.z)));
        view.transformDirection(rayD.set((float) look.x, (float) look.y, (float) look.z)).normalize();
        HitResult target = mc.hitResult;
        worldDistance = target == null || target.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY
            : target.getLocation().distanceTo(eye);
    }

    /** A panel crossed by the camera fades instead of being cut by the near plane; seen from behind it is dimmer. */
    private void fadeNearAndBehind() {
        for (HoloPanel p : atlas.panels()) {
            if (!p.visible) continue;
            Vector3f e = p.eyeLocal(local);
            float blocks = HoloGeometry.distanceToRect(e.x, e.y, e.z, p.w, p.h) * scale;
            float k = Atlas.smooth((blocks - .08F) / .4F);
            if (e.z < 0F) k *= .45F;
            p.alpha *= k;
        }
    }

    // ================================================================ lifecycle

    private void tick() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.level != level || !player.isAlive() || player.isSpectator()
            || incompatible(mc.screen) || MenuApi.capturesInput()) {
            closeNow();
            return;
        }
        atlas.tick();
        if (castAt >= 0 && System.currentTimeMillis() - castAt >= 180 && !atlas.closing()) {
            castAt = -1;
            dismiss();
        }
        if (atlas.closing()) return;
        Vec3 eye = player.getEyePosition();
        double dx = eye.x - anchorX, dy = eye.y - anchorY, dz = eye.z - anchorZ;
        if (dx * dx + dy * dy + dz * dz > DISMISS_DISTANCE * DISMISS_DISTANCE) dismiss();
    }

    /** Screens that take the player out of the world close the Atlas. */
    private static boolean incompatible(Screen screen) {
        return screen instanceof DeathScreen || screen instanceof WinScreen || screen instanceof ReceivingLevelScreen
            || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen || screen instanceof GenericMessageScreen
            || screen instanceof DisconnectedScreen || screen instanceof TitleScreen;
    }

    // ================================================================ events

    @SubscribeEvent
    public static void renderFrame(RenderFrameEvent.Pre event) {
        if (current != null) current.frame();
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && current != null) current.renderWorld(event);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (current != null) current.tick();
        AtlasInput.poll();
        while (OPEN.consumeClick()) if (mc.screen == null) toggle();
        while (AtlasInput.RECALL.consumeClick()) if (mc.screen == null) recall();
    }

    @SubscribeEvent
    public static void mouseButton(InputEvent.MouseButton.Pre event) { AtlasInput.onMouseButton(event); }

    @SubscribeEvent
    public static void mouseScroll(InputEvent.MouseScrollingEvent event) { AtlasInput.onScroll(event); }

    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) { AtlasInput.onInteraction(event); }

    /** Any screen on top (chat, inventory, pause...): drops a drag and cursor mode without clicking anything. */
    @SubscribeEvent
    public static void screenOpening(ScreenEvent.Opening event) {
        if (event.isCanceled() || event.getNewScreen() == null) return;
        if (current != null) current.cancelDrag();
        AtlasInput.screenOpened();
    }

    /** The pause menu opens the Atlas (as before 0.11.2). */
    @SubscribeEvent
    public static void pauseButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen) || !screen.showsPauseMenu()) return;
        Minecraft mc = Minecraft.getInstance();
        event.addListener(Button.builder(Component.translatable(PREFIX + "title"), button -> {
            mc.setScreen(null);
            summon();
        }).bounds(screen.width - 97, 4, 93, 20).build());
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        closeAll();
        AtlasInput.onLoggingOut();
    }

    @SubscribeEvent
    public static void clone(ClientPlayerNetworkEvent.Clone event) { closeAll(); }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) closeAll();
    }

    /**
     * For the in-game test: world position of a point of a visible panel (local units), from the matrices of the last
     * frame; null when closed or the panel is not visible.
     */
    public static Vec3 worldPoint(String panelId, float lx, float ly) {
        AtlasWorld w = current;
        if (w == null) return null;
        for (HoloPanel p : w.atlas.panels()) {
            if (!p.visible || !p.id.equals(panelId)) continue;
            Matrix4f m = HoloGeometry.layoutToWorld(new Matrix4f(), 0F, 0F, 0F, w.yaw, w.scale, Atlas.W / 2F, Atlas.H / 2F, w.eyeZ)
                .mul(p.model);
            Vector3f v = m.transformPosition(new Vector3f(lx, ly, 0F));
            return new Vec3(w.anchorX + v.x, w.anchorY + v.y, w.anchorZ + v.z);
        }
        return null;
    }

    /** For the in-game test and the docs: the tab shown (0 skills .. 4 diagnostics). */
    public static void selectTab(int index) {
        if (current != null) current.atlas.selectTab(index);
    }

    /** For the in-game test: the skills page shown and the page count, or null when closed. */
    public static int[] page() { return current == null ? null : new int[] {current.atlas.pageIndex(), current.atlas.pageCount()}; }

    /** For the in-game test: the tab shown, or -1 when closed. */
    public static int tab() { return current == null ? -1 : current.atlas.tabIndex(); }

    /** Panels for tests: the panel ids visible now (empty when closed). */
    public static List<String> visiblePanels() {
        return current == null ? List.of() : current.atlas.panels().stream().filter(p -> p.visible).map(p -> p.id).toList();
    }
}
