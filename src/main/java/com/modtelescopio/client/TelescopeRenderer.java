package com.modtelescopio.client;

import com.modtelescopio.TelescopioMod;
import com.modtelescopio.block.TelescopeBlock;
import com.modtelescopio.block.TelescopeBlockEntity;
import com.modtelescopio.client.model.ModelBuilder;
import com.modtelescopio.client.model.TelescopeModel;
import com.modtelescopio.common.Optics;
import com.modtelescopio.common.TelescopeType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Desenha o telescópio colocado no mundo, com o tubo apontando para onde foi mirado. */
public class TelescopeRenderer implements BlockEntityRenderer<TelescopeBlockEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation(TelescopioMod.MODID, "textures/misc/white.png");

    private final ModelBuilder builder = new ModelBuilder();
    private final VertexSink sink = new VertexSink();

    public TelescopeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TelescopeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof TelescopeBlock block)) {
            return;
        }
        float facing = state.getValue(TelescopeBlock.FACING).toYRot();
        updateRenderAim(be, partialTick);
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        renderModel(builder, sink, block.type(), facing, be.renderYaw, be.renderAlt, pose, buffers, light, overlay);
        pose.popPose();
    }

    static void renderModel(ModelBuilder builder, VertexSink sink, TelescopeType type, float facing, float yaw,
                            float alt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        sink.begin(pose.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), light, overlay);
        TelescopeModel.build(builder.reset(sink), type, facing, yaw, alt);
    }

    private static void updateRenderAim(TelescopeBlockEntity be, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (be.liveAim && player != null) {
            // o jogador local está olhando por este telescópio: o tubo segue a câmera sem atraso
            be.renderYaw = player.getViewYRot(partialTick);
            be.renderAlt = Optics.clampAltitude(-player.getViewXRot(partialTick));
            be.renderInit = true;
            return;
        }
        float yaw = be.getAimYaw(), alt = be.getAimAlt();
        if (!be.renderInit) {
            be.renderYaw = yaw;
            be.renderAlt = alt;
            be.renderInit = true;
            return;
        }
        // outros jogadores mexendo: movimento suave até a nova posição
        be.renderYaw += Optics.wrapDegrees(yaw - be.renderYaw) * 0.2F;
        be.renderAlt += (alt - be.renderAlt) * 0.2F;
    }

    @Override
    public boolean shouldRenderOffScreen(TelescopeBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
