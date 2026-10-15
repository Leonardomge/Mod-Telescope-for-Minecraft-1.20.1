package com.modtelescopio.client;

import com.modtelescopio.client.model.QuadSink;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Envia os quadriláteros do modelo procedural para um VertexConsumer do 1.20.1. */
final class VertexSink implements QuadSink {
    private PoseStack.Pose pose;
    private VertexConsumer consumer;
    private int light, overlay;

    void begin(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay) {
        this.pose = pose;
        this.consumer = consumer;
        this.light = light;
        this.overlay = overlay;
    }

    @Override
    public void quad(float[] x, float[] y, float[] z, float[] nx, float[] ny, float[] nz, int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int i = 0; i < 4; i++) {
            consumer.vertex(m, x[i], y[i], z[i])
                    .color(r, g, b, 255)
                    .uv(0.5F, 0.5F)
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(n, nx[i], ny[i], nz[i])
                    .endVertex();
        }
    }
}
