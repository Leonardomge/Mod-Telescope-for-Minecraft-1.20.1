package com.modtelescopio.block;

import com.modtelescopio.client.ClientObservation;
import com.modtelescopio.common.TelescopeType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Telescópio completo (tubo + montagem de fábrica). */
public class TelescopeBlock extends TelescopeBlockBase {
    private final TelescopeType type;

    public TelescopeBlock(TelescopeType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public TelescopeType type() {
        return type;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientObservation.toggle(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
