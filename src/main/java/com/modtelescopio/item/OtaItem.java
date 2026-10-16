package com.modtelescopio.item;

import com.modtelescopio.client.TelescopeItemRenderer;
import com.modtelescopio.common.TelescopeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Tubo óptico avulso: vai em cima de uma montagem-bloco. */
public class OtaItem extends Item {
    private final TelescopeType type;

    public OtaItem(TelescopeType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public TelescopeType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        Tooltips.optics(lines, type);
        lines.add(Component.translatable("telescopio.tooltip.weight", Tooltips.fmt1(type.weightKg))
                .withStyle(ChatFormatting.GRAY));
        Tooltips.extras(lines, type);
        Tooltips.hint(lines, "telescopio.tooltip.ota_use");
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
