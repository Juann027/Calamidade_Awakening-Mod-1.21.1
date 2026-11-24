package dev.bossestesting.client.bar;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.bossestesting.BossesTesting;
import dev.bossestesting.encounter.BossDefs;
import dev.calamidade_awakening.api.v1.client.BossBars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The two exclusive boss bars (BossBars.style + View). Phase 1: military panel of graphite and green, orange fill,
 * reactor emblem. Phase 2: damaged frame, cyan fill, red emergency lamps, instability (jitter, sparks of the
 * frame). Overloaded (phase index 1 of the second controller): extra warning lamps, energy pulses, stronger look.
 * Both show name, health %, recent damage (ghost + number), phase pips and label, a phase-change flash, and react to
 * scale and high contrast. The second bar boots up on its first frame, so the swap reads as one continuous bar.
 */
public final class GuilherminoBars {
    static final ResourceLocation PANEL_P1 = BossesTesting.id("textures/gui/bar/panel_p1.png"), PANEL_P2 = BossesTesting.id("textures/gui/bar/panel_p2.png"),
        FILL = BossesTesting.id("textures/gui/bar/fill.png"), EMBLEM_P1 = BossesTesting.id("textures/gui/bar/emblem_p1.png"),
        EMBLEM_P2 = BossesTesting.id("textures/gui/bar/emblem_p2.png"), GLOW = BossesTesting.id("textures/gui/bar/glow.png");

    /** Client memory per bar title: first frame, last progress and the recent damage counter. */
    private static final class Memory { long lastDraw = -1000; long born = -1; float last = -1; float recent; long recentAt; float shownProgress = -1; }
    private static final Map<String, Memory> MEMORY = new HashMap<>();

    private GuilherminoBars() {}

    public static void register() {
        BossBars.style(BossDefs.STYLE_P1, (g, x, y, bar) -> draw(g, x, y, bar, false));
        BossBars.style(BossDefs.STYLE_P2, (g, x, y, bar) -> draw(g, x, y, bar, true));
    }

    static int draw(GuiGraphics g, int x, int y, BossBars.View bar, boolean phase2) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float time = (mc.level != null ? mc.level.getGameTime() : 0) + partial;
        Memory m = MEMORY.computeIfAbsent((phase2 ? "2:" : "1:") + bar.name().getString(), k -> new Memory());
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        if (now - m.lastDraw > 40) { m.born = now; m.last = -1; m.recent = 0; } // a new bar (spawn or swap) boots up
        m.lastDraw = now;
        if (m.last >= 0 && bar.progress() < m.last - 1e-4) { m.recent += m.last - bar.progress(); m.recentAt = now; }
        if (now - m.recentAt > 50) m.recent = 0;
        m.last = bar.progress();

        boolean overloaded = phase2 && bar.phase() == 1;
        boolean crisis = !phase2 && bar.phase() == 2;
        boolean hc = bar.highContrast();
        float scale = bar.scale();
        int width = 256, height = 40;
        float boot = phase2 ? Mth.clamp((now - m.born + partial) / 40f, 0, 1) : 1;

        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 91, y, 0);
        pose.scale(scale, scale, 1);
        float jitter = (phase2 && !hc ? (float) (Math.sin(time * 7.3) * Math.sin(time * 2.1)) * (overloaded ? .9f : .35f) : 0)
            + (crisis ? (float) Math.sin(time * 13) * .8f : 0);
        pose.translate(-width / 2f + jitter, 0, 0);
        RenderSystem.enableBlend();

        // ---- panel
        if (hc) {
            g.fill(0, 4, width, height - 2, 0xff000000);
            g.renderOutline(0, 4, width, height - 6, 0xffffffff);
        } else {
            float flick = phase2 && boot < 1 ? (Math.sin(time * 40) > 0 ? 1 : .3f) : 1;
            g.setColor(flick, flick, flick, boot < 1 ? .35f + .65f * boot : 1);
            g.blit(phase2 ? PANEL_P2 : PANEL_P1, 0, 0, 0, 0, width, height, width, height);
            g.setColor(1, 1, 1, 1);
        }

        // ---- fill window 44..244 x 16..27
        int fx = 44, fy = 16, fw = 200, fh = 12;
        float shown = boot < 1 ? bar.progress() * easeOut(boot) : bar.progress();
        int ghostW = Math.round(fw * Math.max(bar.ghost(), shown));
        int fillW = Math.round(fw * shown);
        int accent = phase2 ? (overloaded ? 0xff60f0ff : 0xff30d8ff) : 0xffff8a20;
        int ghostColor = phase2 ? 0xffff3a4a : 0xfffff0c8;
        g.fill(fx, fy, fx + ghostW, fy + fh, hc ? 0xffaaaaaa : ghostColor);
        if (hc) g.fill(fx, fy, fx + fillW, fy + fh, 0xffffffff);
        else {
            float r = ((accent >> 16) & 255) / 255f, gr = ((accent >> 8) & 255) / 255f, b = (accent & 255) / 255f;
            float pulse = overloaded ? .85f + .15f * (float) Math.sin(time * .9) : 1;
            g.setColor(r * pulse, gr * pulse, b * pulse, 1);
            float scroll = (time * (overloaded ? 1.6f : .6f)) % 64;
            if (fillW > 0) g.blit(FILL, fx, fy, fillW, fh, scroll, 0, fillW, fh, 64, 12);
            g.setColor(1, 1, 1, 1);
            // energy pulses traveling along the fill (Overloaded) / hot edge
            if (fillW > 2) {
                int edge = fx + fillW;
                g.fill(edge - 2, fy, edge, fy + fh, 0xffffffff);
                if (overloaded) for (int i = 0; i < 3; i++) {
                    int px = fx + (int) ((time * 3 + i * 70) % Math.max(1, fillW));
                    g.fill(px, fy, px + 3, fy + fh, 0x90ffffff);
                }
            }
        }
        // phase marks: the bar halves are where fury / overload begin
        int mark = fx + fw / 2;
        g.fill(mark, fy - 2, mark + 1, fy + fh + 2, hc ? 0xffffffff : (phase2 ? 0xffff3a4a : 0xfffff0a0));

        // ---- emblem with a pulsing glow
        if (!hc) {
            float beat = phase2 ? (overloaded ? .6f + .4f * (float) Math.sin(time * 1.2) : .5f + .3f * (float) Math.sin(time * .35))
                : crisis ? (Math.sin(time * 2.6) > 0 ? .9f : .1f) : .45f + .25f * (float) Math.sin(time * .2);
            int gc = phase2 ? accent : 0xffff8a20;
            g.setColor(((gc >> 16) & 255) / 255f, ((gc >> 8) & 255) / 255f, (gc & 255) / 255f, beat);
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            g.blit(GLOW, -4, -4, 0, 0, 48, 48, 48, 48);
            RenderSystem.defaultBlendFunc();
            g.setColor(1, 1, 1, crisis && Math.sin(time * 2.6) < 0 ? .4f : 1);
            g.blit(phase2 ? EMBLEM_P2 : EMBLEM_P1, 4, 4, 0, 0, 32, 32, 32, 32);
            g.setColor(1, 1, 1, 1);
        }

        // ---- emergency lamps (phase 2): two always, three more when Overloaded
        if (phase2) {
            int lamps = overloaded ? 5 : 2;
            for (int i = 0; i < lamps; i++) {
                boolean on = hc || Math.sin(time * (overloaded ? 1.1 : .4) + i * 1.7) > 0;
                int lx = 200 - i * 12;
                g.fill(lx, 6, lx + 8, 10, on ? 0xffff2a3a : 0xff3a0a0e);
                if (on && !hc) g.fill(lx - 1, 5, lx + 9, 11, 0x40ff2a3a);
            }
        }

        // ---- texts: name (left), health % and recent damage (right), phase label under the bar
        int textColor = hc ? 0xffffffff : (phase2 ? 0xffd8faff : 0xfffff0d8);
        Component name = bar.name();
        g.drawString(font, name, 44, 5, textColor, true);
        String pct = Math.round(bar.progress() * 1000) / 10f + "%";
        g.drawString(font, pct, fx + fw - font.width(pct), 5, textColor, true);
        if (m.recent > 1e-4 && !hc) {
            float fade = 1 - Mth.clamp((now - m.recentAt) / 50f, 0, 1);
            String dmg = "-" + Math.round(m.recent * 1000) / 10f + "%";
            int a = (int) (fade * 255) << 24;
            g.drawString(font, dmg, fx + fw - font.width(pct) - font.width(dmg) - 6, 5, a | (phase2 ? 0xff5a6a : 0xffd070), true);
        }
        Component label = bar.label();
        int ly = height - 1;
        if (label != null) {
            String extra = overloaded ? "  " + Component.translatable("bar.bossestesting.overloaded").getString() : crisis ? "  " + Component.translatable("bar.bossestesting.critical").getString() : "";
            g.drawString(font, label.getString() + extra, 44, ly, overloaded || crisis ? 0xffff5a5a : (hc ? 0xffffffff : 0xffb8c0b0), true);
        }
        // phase pips: two forms, the current one lit
        for (int i = 0; i < 2; i++) {
            boolean lit = phase2 ? i == 1 : i == 0;
            int px = fx + fw - 22 + i * 12;
            g.fill(px, ly + 2, px + 8, ly + 6, lit ? accent : 0xff2a2a2a);
        }
        // phase-change flash (already reduced by accessibility in the view)
        if (bar.flash() > .01f && !hc) g.fill(0, 4, width, height - 2, ((int) (bar.flash() * 120) << 24) | 0xffffff);
        if (!bar.vulnerable() && !hc) g.fill(fx, fy, fx + fw, fy + fh, 0x50000000);
        pose.popPose();
        RenderSystem.disableBlend();
        return Math.round((height + 12) * scale);
    }

    static float easeOut(float t) { return 1 - (1 - t) * (1 - t) * (1 - t); }
}
