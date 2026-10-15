package com.modtelescopio.item;

import com.modtelescopio.block.TelescopeBlock;
import com.modtelescopio.client.TelescopeItemRenderer;
import com.modtelescopio.common.TelescopeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Item do telescópio, desenhado em 3D pelo {@link TelescopeItemRenderer}. */
public class TelescopeItem extends BlockItem {
    public TelescopeItem(TelescopeBlock block, Properties properties) {
        super(block, properties);
    }

    public TelescopeType type() {
        return ((TelescopeBlock) getBlock()).type();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        TelescopeType t = type();
        String ratio = String.format(Locale.ROOT, "%.1f", t.focalRatio());
        lines.add(Component.translatable("telescopio.tooltip.optics", Component.translatable(t.design.langKey()),
                t.aperture, ratio).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("telescopio.tooltip.focal", t.focal).withStyle(ChatFormatting.GRAY));
        MutableComponent mount = Component.translatable(t.mount.langKey());
        if (!t.mountName.isEmpty()) {
            mount = mount.append(" (" + t.mountName + ")");
        }
        lines.add(Component.translatable("telescopio.tooltip.mount", mount).withStyle(ChatFormatting.GRAY));
        if (t.goTo) {
            lines.add(Component.translatable("telescopio.tooltip.goto").withStyle(ChatFormatting.GREEN));
        }
        lines.add(Component.translatable("telescopio.tooltip.use").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return TelescopeItemRenderer.instance();
            }
        });
    }
}
