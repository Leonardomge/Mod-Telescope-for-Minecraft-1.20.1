package com.modtelescopio.block;

import com.modtelescopio.client.model.Assembly;
import com.modtelescopio.client.model.ModelBounds;
import com.modtelescopio.common.Optics;
import com.modtelescopio.common.TelescopeType;
import com.modtelescopio.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Guarda para onde o tubo está apontando (sincronizado com todos os jogadores) e, nas
 * montagens-bloco, qual tubo óptico está instalado.
 */
public class TelescopeBlockEntity extends BlockEntity {
    private float aimYaw;
    private float aimAlt = Optics.DEFAULT_ALTITUDE;
    private boolean hasAim;
    private TelescopeType tube;

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
        changed();
    }

    public float getAimYaw() {
        if (hasAim) {
            return aimYaw;
        }
        BlockState state = getBlockState();
        return state.hasProperty(TelescopeBlockBase.FACING) ? state.getValue(TelescopeBlockBase.FACING).toYRot() : 0.0F;
    }

    public float getAimAlt() {
        return aimAlt;
    }

    /** Tubo instalado (só nas montagens-bloco). */
    public TelescopeType getTube() {
        return tube;
    }

    public void setTube(TelescopeType tube) {
        this.tube = tube;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** O que este bloco desenha, ou null se o bloco não for de telescópio. */
    public Assembly assembly() {
        Block b = getBlockState().getBlock();
        if (b instanceof TelescopeBlock t) {
            return Assembly.of(t.type());
        }
        if (b instanceof MountBlock m) {
            return Assembly.of(m.mount(), tube);
        }
        return null;
    }

    /** Tubo usado na observação (o do telescópio completo ou o instalado na montagem). */
    public TelescopeType opticalTube() {
        Block b = getBlockState().getBlock();
        if (b instanceof TelescopeBlock t) {
            return t.type();
        }
        return b instanceof MountBlock ? tube : null;
    }

    private void writeSync(CompoundTag tag) {
        tag.putBoolean("HasAim", hasAim);
        tag.putFloat("AimYaw", aimYaw);
        tag.putFloat("AimAlt", aimAlt);
        if (tube != null) {
            tag.putString("Tube", tube.id);
        }
    }

    private void readSync(CompoundTag tag) {
        hasAim = tag.getBoolean("HasAim");
        aimYaw = tag.getFloat("AimYaw");
        aimAlt = tag.contains("AimAlt") ? tag.getFloat("AimAlt") : Optics.DEFAULT_ALTITUDE;
        tube = tag.contains("Tube") ? TelescopeType.byId(tag.getString("Tube")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeSync(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readSync(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeSync(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = worldPosition;
        Assembly a = assembly();
        if (a == null) {
            return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 2.5, p.getZ() + 2);
        }
        ModelBounds b = ModelBounds.of(a);
        double r = b.horizontalReach() + 0.5;
        return new AABB(p.getX() + 0.5 - r, p.getY() + Math.min(0.0, b.minY), p.getZ() + 0.5 - r,
                p.getX() + 0.5 + r, p.getY() + b.maxY + b.height() * 0.3 + 0.5, p.getZ() + 0.5 + r);
    }
}
