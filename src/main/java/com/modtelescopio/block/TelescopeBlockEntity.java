package com.modtelescopio.block;

import com.modtelescopio.common.Optics;
import com.modtelescopio.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Guarda para onde o tubo está apontando (sincronizado com todos os jogadores). */
public class TelescopeBlockEntity extends BlockEntity {
    private float aimYaw;
    private float aimAlt = Optics.DEFAULT_ALTITUDE;
    private boolean hasAim;

    // estado só do cliente: ângulo desenhado, suavizado
    public float renderYaw, renderAlt;
    public boolean renderInit;
    /** true enquanto o jogador local está observando por este telescópio. */
    public boolean liveAim;

    public TelescopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TELESCOPE_BE.get(), pos, state);
    }

    public void setAim(float yaw, float alt) {
        aimYaw = Optics.wrapDegrees(yaw);
        aimAlt = Optics.clampAltitude(alt);
        hasAim = true;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public float getAimYaw() {
        if (hasAim) {
            return aimYaw;
        }
        BlockState state = getBlockState();
        return state.hasProperty(TelescopeBlock.FACING) ? state.getValue(TelescopeBlock.FACING).toYRot() : 0.0F;
    }

    public float getAimAlt() {
        return aimAlt;
    }

    private void writeAim(CompoundTag tag) {
        tag.putBoolean("HasAim", hasAim);
        tag.putFloat("AimYaw", aimYaw);
        tag.putFloat("AimAlt", aimAlt);
    }

    private void readAim(CompoundTag tag) {
        hasAim = tag.getBoolean("HasAim");
        aimYaw = tag.getFloat("AimYaw");
        aimAlt = tag.contains("AimAlt") ? tag.getFloat("AimAlt") : Optics.DEFAULT_ALTITUDE;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeAim(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readAim(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeAim(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = worldPosition;
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 2.5, p.getZ() + 2);
    }
}
