package com.modtelescopio.client;

import com.modtelescopio.client.model.ModelBounds;
import com.modtelescopio.client.model.ModelBuilder;
import com.modtelescopio.common.Optics;
import com.modtelescopio.common.TelescopeType;
import com.modtelescopio.item.TelescopeItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Telescópio em 3D no inventário, na mão, no chão e em molduras. */
public class TelescopeItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static TelescopeItemRenderer instance;

    private final ModelBuilder builder = new ModelBuilder();
    private final VertexSink sink = new VertexSink();

    private TelescopeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static TelescopeItemRenderer instance() {
        if (instance == null) {
            instance = new TelescopeItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        if (!(stack.getItem() instanceof TelescopeItem item)) {
            return;
        }
        TelescopeType type = item.type();
        ModelBounds b = ModelBounds.of(type);
        float s = b.fitScale(1.0F);
        pose.pushPose();
        // o modelo inteiro cabe no cubo 0..1 do item, centralizado
        pose.translate(0.5, 0.5 - b.height() * s * 0.5, 0.5);
        pose.scale(s, s, s);
        pose.translate(-b.centerX(), -b.minY, -b.centerZ());
        TelescopeRenderer.renderModel(builder, sink, type, 0.0F, 0.0F, Optics.DEFAULT_ALTITUDE, pose, buffers, light, overlay);
        pose.popPose();
    }
}
