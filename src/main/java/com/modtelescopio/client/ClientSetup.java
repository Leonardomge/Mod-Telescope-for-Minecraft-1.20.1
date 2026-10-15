package com.modtelescopio.client;

import com.modtelescopio.TelescopioMod;
import com.modtelescopio.registry.ModRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registros só do cliente (barramento do mod). */
@Mod.EventBusSubscriber(modid = TelescopioMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.TELESCOPE_BE.get(), TelescopeRenderer::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        // abaixo de tudo: o chat e as mensagens continuam visíveis por cima da máscara da ocular
        event.registerBelowAll("eyepiece", ObservationOverlay::render);
    }
}
