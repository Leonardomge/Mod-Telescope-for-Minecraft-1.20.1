package com.modtelescopio.item;

import com.modtelescopio.block.MountBlock;
import com.modtelescopio.client.TelescopeItemRenderer;
import com.modtelescopio.common.MountBlockType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Item da montagem em tripé/pilar (desenhado em 3D, sem tubo). */
public class MountBlockItem extends BlockItem {
    public MountBlockItem(MountBlock block, Properties properties) {
        super(block, properties);
    }

    public MountBlockType mount() {
        return ((MountBlock) getBlock()).mount();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        MountBlockType m = mount();
        lines.add(Component.translatable(m.preset.langKey()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("telescopio.tooltip.capacity", Tooltips.fmt1(m.capacityKg))
                .withStyle(ChatFormatting.GRAY));
        if (m.goTo) {
            lines.add(Component.translatable("telescopio.tooltip.goto").withStyle(ChatFormatting.GREEN));
        }
        Tooltips.hint(lines, "telescopio.tooltip.mount_use");
        Tooltips.hint(lines, "telescopio.tooltip.mount_remove");
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
