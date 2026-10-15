package com.modtelescopio.client;

import com.modtelescopio.common.Optics;
import com.modtelescopio.common.TelescopeType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

import java.util.Locale;

/** Máscara circular da ocular + informações ópticas na tela. */
final class ObservationOverlay {
    private static final int SEGMENTS = 96;

    private ObservationOverlay() {
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!ClientObservation.isActive()) {
            return;
        }
        float cx = width * 0.5F, cy = height * 0.5F;
        float r = Math.min(width, height) * 0.46F;
        float outer = (float) Math.sqrt((double) width * width + (double) height * height);

        Matrix4f m = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // tudo fora do campo da ocular fica preto; a borda escurece suavemente (diafragma de campo)
        ring(buf, m, cx, cy, r, outer, 255, 255);
        ring(buf, m, cx, cy, r * 0.86F, r, 0, 255);
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();

        drawInfo(graphics, width, height);
    }

    private static void ring(BufferBuilder buf, Matrix4f m, float cx, float cy, float rIn, float rOut, int aIn, int aOut) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = 2.0 * Math.PI * i / SEGMENTS, a1 = 2.0 * Math.PI * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            buf.vertex(m, cx + c0 * rIn, cy + s0 * rIn, 0).color(0, 0, 0, aIn).endVertex();
            buf.vertex(m, cx + c1 * rIn, cy + s1 * rIn, 0).color(0, 0, 0, aIn).endVertex();
            buf.vertex(m, cx + c1 * rOut, cy + s1 * rOut, 0).color(0, 0, 0, aOut).endVertex();
            buf.vertex(m, cx + c0 * rOut, cy + s0 * rOut, 0).color(0, 0, 0, aOut).endVertex();
        }
    }

    private static void drawInfo(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        TelescopeType t = ClientObservation.type();
        int fe = ClientObservation.currentEyepiece();
        double mag = Optics.magnification(t, fe);

        int y = 6;
        g.drawString(font, t.displayName, 6, y, 0xFFD27F, true);
        y += 11;
        g.drawString(font, I18n.get(t.design.langKey()), 6, y, 0xA0A0A0, true);

        String[] bottom = {
                eyepieceStrip(),
                I18n.get("telescopio.hud.eyepiece", fe, fmt0(mag)),
                I18n.get("telescopio.hud.field", fmt2(Optics.trueField(mag)), fmt1(Optics.exitPupil(t, fe))),
                I18n.get("telescopio.hud.specs", t.aperture, t.focal, fmt1(t.focalRatio())),
                I18n.get("telescopio.hud.limits", fmt1(Optics.limitingMagnitude(t)), fmt2(Optics.dawesLimit(t))),
        };
        y = h - 8 - bottom.length * 11;
        for (String line : bottom) {
            g.drawString(font, line, 6, y, 0xFFFFFF, true);
            y += 11;
        }

        if (mc.player != null) {
            String aim = I18n.get("telescopio.hud.aim", fmt0(Optics.compassAzimuth(mc.player.getYRot())),
                    fmt0(-mc.player.getXRot()));
            g.drawString(font, aim, w - 6 - font.width(aim), h - 19, 0xDDDDDD, true);
        }
        String help = I18n.get("telescopio.hud.help");
        g.drawString(font, help, w - 6 - font.width(help), 6, 0x9A9A9A, true);
    }

    /** Lista das oculares com a atual destacada, ex.: "32 [25] 15 9 6 mm". */
    private static String eyepieceStrip() {
        StringBuilder sb = new StringBuilder();
        int[] list = ClientObservation.eyepieces();
        for (int i = 0; i < list.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            if (i == ClientObservation.eyepieceIndex()) {
                sb.append("§e[").append(list[i]).append("]§r");
            } else {
                sb.append("§7").append(list[i]).append("§r");
            }
        }
        sb.append(" mm");
        return sb.toString();
    }

    private static String fmt0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
