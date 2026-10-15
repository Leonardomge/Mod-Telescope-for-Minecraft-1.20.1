package com.modtelescopio.network;

import com.modtelescopio.TelescopioMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TelescopioMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Network() {
    }

    public static void register() {
        CHANNEL.messageBuilder(AimMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AimMessage::encode)
                .decoder(AimMessage::decode)
                .consumerMainThread(AimMessage::handle)
                .add();
    }
}
