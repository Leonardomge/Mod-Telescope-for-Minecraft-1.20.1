package com.modtelescopio;

import com.modtelescopio.network.Network;
import com.modtelescopio.registry.ModRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TelescopioMod.MODID)
public class TelescopioMod {
    public static final String MODID = "telescopio";

    public TelescopioMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
    }
}
