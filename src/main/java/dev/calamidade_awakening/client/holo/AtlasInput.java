package dev.calamidade_awakening.client.holo;

import com.mojang.blaze3d.platform.InputConstants;
import dev.calamidade_awakening.api.holo.HoloInputArbiter;
import dev.calamidade_awakening.api.holo.HoloInputArbiter.Owner;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Input router of the Star Atlas in the world (0.11.2). There is no {@code Screen}: walking, jumping and looking are
 * never captured; only clicks, the wheel and the vanilla actions <b>with the aim on a panel</b>. Adapted from the
 * RespectCraft holographic menu ({@code HoloWorldInput}, stage 4.8.4-B, same author), whose hooks were checked in the
 * NeoForge sources: {@code InputEvent.MouseButton.Pre} runs in {@code MouseHandler.onPress} before the vanilla
 * handling (cancelled, the attack/use key never goes down: no attack, mining, use or swing);
 * {@code InputEvent.MouseScrollingEvent} runs before the hotbar changes; {@code InteractionKeyMappingTriggered} is the
 * safety net (held mining that started in the world never acts behind a panel) and the owner for actions bound to the
 * keyboard. Cursor mode (hold the key, left Alt by default) frees the mouse to point with the cursor.
 */
public final class AtlasInput {
    private static final HoloInputArbiter ARBITER = new HoloInputArbiter();
    private static boolean cursorMode, regrabPending;
    /** Hold to point with a free cursor while the Atlas is open (0.11.2). */
    public static final KeyMapping CURSOR = new KeyMapping("key.calamidade_awakening.atlas_cursor",
        GLFW.GLFW_KEY_LEFT_ALT, "key.categories.calamidade_awakening");
    /** Brings the open Atlas in front of the current aim (0.11.2). */
    public static final KeyMapping RECALL = new KeyMapping("key.calamidade_awakening.atlas_recall",
        GLFW.GLFW_KEY_B, "key.categories.calamidade_awakening");

    private AtlasInput() {}

    static boolean cursorMode() { return cursorMode; }

    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        int button = event.getButton();
        if (button < 0 || button >= HoloInputArbiter.MOUSE_BUTTONS) return;
        Minecraft mc = Minecraft.getInstance();
        AtlasWorld w = AtlasWorld.current();
        if (event.getAction() == InputConstants.PRESS) {
            int action = actionOf(button);
            if (!cursorMode && mc.screen == null && !mc.mouseHandler.isMouseGrabbed()) {
                // A click that only gives the mouse back to the game: with the aim on a panel it is focus only.
                boolean holo = w != null && w.acceptsInput() && w.ownsAim();
                ARBITER.press(button, holo);
                if (holo) {
                    event.setCanceled(true);
                    mc.mouseHandler.grabMouse();
                }
                return;
            }
            boolean holo = w != null && w.acceptsInput() && (cursorMode || action >= 0 && w.ownsAim());
            if (ARBITER.press(button, holo) == Owner.HOLO) {
                event.setCanceled(true);
                if (w != null && action != HoloInputArbiter.PICK) w.press(button, action);
            }
        } else if (event.getAction() == InputConstants.RELEASE) {
            if (ARBITER.release(button) == Owner.HOLO) {
                event.setCanceled(true);
                if (w != null) w.release(button);
            }
        }
    }

    static void onScroll(InputEvent.MouseScrollingEvent event) {
        AtlasWorld w = AtlasWorld.current();
        if (w == null || !w.acceptsInput() || !(cursorMode || w.ownsAim())) return;
        event.setCanceled(true);
        w.scroll(event.getScrollDeltaY());
    }

    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        int action = event.isAttack() ? HoloInputArbiter.ATTACK : event.isUseItem() ? HoloInputArbiter.USE
            : event.isPickBlock() ? HoloInputArbiter.PICK : -1;
        if (action < 0) return;
        InputConstants.Key key = event.getKeyMapping().getKey();
        AtlasWorld w = AtlasWorld.current();
        Owner owner;
        if (key.getType() == InputConstants.Type.MOUSE) {
            owner = ARBITER.owner(key.getValue());
        } else {
            int source = HoloInputArbiter.keySource(action);
            owner = ARBITER.owner(source);
            if (owner == Owner.NONE) {
                boolean holo = w != null && w.acceptsInput() && (cursorMode || w.ownsAim());
                owner = ARBITER.press(source, holo);
                if (owner == Owner.HOLO && action != HoloInputArbiter.PICK) w.press(source, action);
            }
        }
        // Panel owner: nothing in the world. World owner: held repetition never acts behind a panel in front of the aim.
        if (owner == Owner.HOLO || owner == Owner.WORLD && w != null && w.ownsAim()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    static void onLoggingOut() {
        ARBITER.clear();
        cursorMode = false;
        regrabPending = false;
    }

    /** Every frame with the Atlas open and every tick: released keyboard actions, lost releases, cursor mode. */
    static void poll() {
        Minecraft mc = Minecraft.getInstance();
        AtlasWorld w = AtlasWorld.current();
        for (int action = HoloInputArbiter.ATTACK; action <= HoloInputArbiter.PICK; action++) {
            int source = HoloInputArbiter.keySource(action);
            if (ARBITER.owner(source) != Owner.NONE && !mapping(mc, action).isDown()
                && ARBITER.release(source) == Owner.HOLO && w != null) w.release(source);
        }
        long window = mc.getWindow().getWindow();
        for (int b = 0; b < HoloInputArbiter.MOUSE_BUTTONS; b++) {
            if (ARBITER.owner(b) == Owner.HOLO && GLFW.glfwGetMouseButton(window, b) != GLFW.GLFW_PRESS) {
                ARBITER.release(b);
                if (w != null) w.release(b);
            }
        }
        boolean want = w != null && w.acceptsInput() && !w.closing() && mc.isWindowActive() && physicallyDown(mc, CURSOR);
        if (want && !cursorMode) {
            w.cancelDrag();
            cursorMode = true;
            mc.mouseHandler.releaseMouse();
        } else if (!want && cursorMode) {
            exitCursor(true);
        }
        if (regrabPending && !cursorMode) {
            if (mc.screen != null || mc.level == null) regrabPending = false;
            else if (mc.isWindowActive()) {
                regrabPending = false;
                if (!mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
            }
        }
    }

    /** The Atlas closed: leave cursor mode and give mouse-look back. */
    static void closed() { exitCursor(true); }

    /** A screen is opening (it frees and grabs the mouse itself): leave cursor mode without grabbing. */
    static void screenOpened() {
        exitCursor(false);
        regrabPending = false;
    }

    private static void exitCursor(boolean regrab) {
        if (!cursorMode) return;
        cursorMode = false;
        AtlasWorld w = AtlasWorld.current();
        if (w != null) w.cancelDrag();
        Minecraft mc = Minecraft.getInstance();
        if (!regrab || mc.screen != null || mc.level == null) return;
        if (mc.isWindowActive()) mc.mouseHandler.grabMouse();
        else regrabPending = true;
    }

    private static int actionOf(int button) {
        var o = Minecraft.getInstance().options;
        if (o.keyAttack.matchesMouse(button)) return HoloInputArbiter.ATTACK;
        if (o.keyUse.matchesMouse(button)) return HoloInputArbiter.USE;
        if (o.keyPickItem.matchesMouse(button)) return HoloInputArbiter.PICK;
        return -1;
    }

    private static KeyMapping mapping(Minecraft mc, int action) {
        return action == HoloInputArbiter.ATTACK ? mc.options.keyAttack : action == HoloInputArbiter.USE ? mc.options.keyUse
            : mc.options.keyPickItem;
    }

    /** Physically held (a key released while the window had no focus never leaves cursor mode stuck). */
    private static boolean physicallyDown(Minecraft mc, KeyMapping km) {
        if (!km.isDown()) return false;
        InputConstants.Key key = km.getKey();
        long window = mc.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> true;
        };
    }
}
