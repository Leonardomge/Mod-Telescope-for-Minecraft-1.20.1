package com.modtelescopio.network;

import com.modtelescopio.block.TelescopeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: "estou observando por este telescópio, apontando para cá". */
public record AimMessage(BlockPos pos, float yaw, float alt) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeFloat(yaw);
        buf.writeFloat(alt);
    }

    public static AimMessage decode(FriendlyByteBuf buf) {
        return new AimMessage(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !Float.isFinite(yaw) || !Float.isFinite(alt)) {
            return;
        }
        Level level = player.level();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TelescopeBlockEntity be) {
            be.setAim(yaw, alt);
        }
    }
}
