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

/** Máscara da ocular (círculo) ou da câmera (retângulo do sensor) + informações na tela. */
final class ObservationOverlay {
    private static final int SEGMENTS = 96;

    private ObservationOverlay() {
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!ClientObservation.isActive()) {
            return;
        }
        Matrix4f m = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (ClientObservation.isCamera()) {
            cameraMask(buf, m, ClientObservation.type(), width, height);
        } else {
            float cx = width * 0.5F, cy = height * 0.5F;
            float r = Math.min(width, height) * 0.46F;
            float outer = (float) Math.sqrt((double) width * width + (double) height * height);
            // tudo fora do campo da ocular fica preto; a borda escurece suavemente (diafragma de campo)
            ring(buf, m, cx, cy, r, outer, 255, 255);
            ring(buf, m, cx, cy, r * 0.86F, r, 0, 255);
        }
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

    /** Quadro do sensor (proporção real), bordas finas, cantos e mira central. */
    private static void cameraMask(BufferBuilder buf, Matrix4f m, TelescopeType type, int w, int h) {
        float aspect = type.sensorW / type.sensorH;
        float fh = h * 0.84F, fw = fh * aspect;
        if (fw > w * 0.94F) {
            fw = w * 0.94F;
            fh = fw / aspect;
        }
        float x0 = (w - fw) * 0.5F, y0 = (h - fh) * 0.5F, x1 = x0 + fw, y1 = y0 + fh;
        rect(buf, m, 0, 0, w, y0, 0xFF000000);
        rect(buf, m, 0, y1, w, h, 0xFF000000);
        rect(buf, m, 0, y0, x0, y1, 0xFF000000);
        rect(buf, m, x1, y0, w, y1, 0xFF000000);
        int border = 0x59FFFFFF;
        rect(buf, m, x0, y0, x1, y0 + 1, border);
        rect(buf, m, x0, y1 - 1, x1, y1, border);
        rect(buf, m, x0, y0, x0 + 1, y1, border);
        rect(buf, m, x1 - 1, y0, x1, y1, border);
        float c = Math.min(fw, fh) * 0.08F;
        int corner = 0xCCFFFFFF;
        rect(buf, m, x0 + 4, y0 + 4, x0 + 4 + c, y0 + 5, corner);
        rect(buf, m, x0 + 4, y0 + 4, x0 + 5, y0 + 4 + c, corner);
        rect(buf, m, x1 - 4 - c, y0 + 4, x1 - 4, y0 + 5, corner);
        rect(buf, m, x1 - 5, y0 + 4, x1 - 4, y0 + 4 + c, corner);
        rect(buf, m, x0 + 4, y1 - 5, x0 + 4 + c, y1 - 4, corner);
        rect(buf, m, x0 + 4, y1 - 4 - c, x0 + 5, y1 - 4, corner);
        rect(buf, m, x1 - 4 - c, y1 - 5, x1 - 4, y1 - 4, corner);
        rect(buf, m, x1 - 5, y1 - 4 - c, x1 - 4, y1 - 4, corner);
        float cx = w * 0.5F, cy = h * 0.5F;
        int cross = 0x99FFFFFF;
        rect(buf, m, cx - 6, cy - 0.5F, cx - 2, cy + 0.5F, cross);
        rect(buf, m, cx + 2, cy - 0.5F, cx + 6, cy + 0.5F, cross);
        rect(buf, m, cx - 0.5F, cy - 6, cx + 0.5F, cy - 2, cross);
        rect(buf, m, cx - 0.5F, cy + 2, cx + 0.5F, cy + 6, cross);
        rect(buf, m, x0 + 8, y0 + 8, x0 + 13, y0 + 13, 0xE6E53935);
    }

    private static void rect(BufferBuilder buf, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        buf.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
    }

    private static void drawInfo(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        TelescopeType t = ClientObservation.type();

        int y = 6;
        g.drawString(font, ClientObservation.title(), 6, y, 0xFFD27F, true);
        y += 11;
        g.drawString(font, I18n.get(t.design.langKey()), 6, y, 0xA0A0A0, true);

        String[] bottom;
        if (ClientObservation.isCamera()) {
            float dz = ClientObservation.currentDigitalZoom();
            double secs = ClientObservation.secondsObserving();
            bottom = new String[]{
                    zoomStrip(),
                    I18n.get("telescopio.hud.sensor", fmt1(t.sensorW), fmt1(t.sensorH), fmtZoom(dz)),
                    I18n.get("telescopio.hud.camfield", fmt2(Optics.cameraFieldW(t) / dz), fmt2(Optics.cameraFieldH(t) / dz)),
                    I18n.get("telescopio.hud.specs", t.aperture, t.focal, fmt1(t.focalRatio())),
                    I18n.get("telescopio.hud.stack", (int) (secs / Optics.STACK_EXPOSURE_S), fmt0(secs)),
            };
        } else {
            int fe = ClientObservation.currentEyepiece();
            double mag = Optics.magnification(t, fe);
            bottom = new String[]{
                    eyepieceStrip(),
                    I18n.get("telescopio.hud.eyepiece", fe, fmt0(mag)),
                    I18n.get("telescopio.hud.field", fmt2(Optics.trueField(mag)), fmt1(Optics.exitPupil(t, fe))),
                    I18n.get("telescopio.hud.specs", t.aperture, t.focal, fmt1(t.focalRatio())),
                    I18n.get("telescopio.hud.limits", fmt1(Optics.limitingMagnitude(t)), fmt2(Optics.dawesLimit(t))),
            };
        }
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
        String help = I18n.get(ClientObservation.isCamera() ? "telescopio.hud.help_camera" : "telescopio.hud.help");
        g.drawString(font, help, w - 6 - font.width(help), 30, 0x9A9A9A, true);
    }

    /** Lista das oculares com a atual destacada, ex.: "32 [25] 15 9 6 mm". */
    private static String eyepieceStrip() {
        StringBuilder sb = new StringBuilder();
        int[] list = ClientObservation.eyepieces();
        for (int i = 0; i < list.length; i++) {
            append(sb, i, String.valueOf(list[i]));
        }
        sb.append(" mm");
        return sb.toString();
    }

    /** Passos de zoom digital com o atual destacado, ex.: "1x [2x] 3x". */
    private static String zoomStrip() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Optics.DIGITAL_ZOOM.length; i++) {
            append(sb, i, fmtZoom(Optics.DIGITAL_ZOOM[i]) + "x");
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, int i, String text) {
        if (i > 0) {
            sb.append(' ');
        }
        if (i == ClientObservation.stepIndex()) {
            sb.append("§e[").append(text).append("]§r");
        } else {
            sb.append("§7").append(text).append("§r");
        }
    }

    private static String fmtZoom(float z) {
        return z == (int) z ? String.valueOf((int) z) : String.format(Locale.ROOT, "%.1f", z);
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
