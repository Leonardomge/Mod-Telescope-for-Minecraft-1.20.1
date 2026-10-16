package com.modtelescopio.block;

import com.modtelescopio.client.ClientObservation;
import com.modtelescopio.common.MountBlockType;
import com.modtelescopio.common.TelescopeType;
import com.modtelescopio.item.OtaItem;
import com.modtelescopio.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.Locale;

/**
 * Montagem em tripé/pilar. Clique segurando um tubo óptico para instalar; com tubo, o
 * botão direito observa; agachado com a mão vazia, retira o tubo.
 */
public class MountBlock extends TelescopeBlockBase {
    private final MountBlockType mount;

    public MountBlock(MountBlockType mount, Properties properties) {
        super(properties);
        this.mount = mount;
    }

    public MountBlockType mount() {
        return mount;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof TelescopeBlockEntity be)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() instanceof OtaItem ota) {
            if (!level.isClientSide) {
                TelescopeType tube = ota.type();
                if (be.getTube() != null) {
                    player.displayClientMessage(Component.translatable("telescopio.msg.has_tube"), true);
                } else if (!mount.canCarry(tube)) {
                    player.displayClientMessage(Component.translatable("telescopio.msg.too_heavy",
                            kg(tube.weightKg), kg(mount.capacityKg)), true);
                } else {
                    be.setTube(tube);
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 0.8F, 1.2F);
                    player.displayClientMessage(Component.translatable("telescopio.msg.mounted",
                            Component.translatable(ota.getDescriptionId())), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (player.isShiftKeyDown()) {
            if (held.isEmpty() && be.getTube() != null) {
                if (!level.isClientSide) {
                    TelescopeType tube = be.getTube();
                    be.setTube(null);
                    ItemStack stack = new ItemStack(ModRegistry.OTA_ITEMS.get(tube).get());
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                    level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 0.8F, 0.8F);
                    player.displayClientMessage(Component.translatable("telescopio.msg.removed",
                            Component.translatable(stack.getDescriptionId())), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }

        if (be.getTube() == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("telescopio.msg.no_tube"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientObservation.toggle(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TelescopeBlockEntity be
                && be.getTube() != null) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(ModRegistry.OTA_ITEMS.get(be.getTube()).get()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    private static String kg(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
