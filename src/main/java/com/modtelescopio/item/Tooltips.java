package com.modtelescopio.item;

import com.modtelescopio.common.TelescopeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Linhas de tooltip comuns aos itens de telescópio, tubo óptico e montagem. */
final class Tooltips {
    private Tooltips() {
    }

    static void optics(List<Component> lines, TelescopeType t) {
        lines.add(Component.translatable("telescopio.tooltip.optics", Component.translatable(t.design.langKey()),
                t.aperture, fmt1(t.focalRatio())).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("telescopio.tooltip.focal", t.focal).withStyle(ChatFormatting.GRAY));
    }

    static void extras(List<Component> lines, TelescopeType t) {
        if (t.design.usesCamera()) {
            lines.add(Component.translatable("telescopio.tooltip.camera").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (t.has(TelescopeType.FLAG_CONCEPT)) {
            lines.add(Component.translatable("telescopio.tooltip.concept").withStyle(ChatFormatting.GOLD));
        }
    }

    static void hint(List<Component> lines, String key) {
        lines.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    static String fmt1(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
