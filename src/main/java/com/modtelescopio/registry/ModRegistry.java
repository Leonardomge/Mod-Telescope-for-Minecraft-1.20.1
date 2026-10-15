package com.modtelescopio.registry;

import com.modtelescopio.TelescopioMod;
import com.modtelescopio.block.TelescopeBlock;
import com.modtelescopio.block.TelescopeBlockEntity;
import com.modtelescopio.common.ComponentType;
import com.modtelescopio.common.TelescopeType;
import com.modtelescopio.item.TelescopeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** Registro de blocos, itens, block entity e aba criativa. */
public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TelescopioMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TelescopioMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TelescopioMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TelescopioMod.MODID);

    public static final Map<TelescopeType, RegistryObject<TelescopeBlock>> TELESCOPE_BLOCKS = new EnumMap<>(TelescopeType.class);
    public static final Map<TelescopeType, RegistryObject<TelescopeItem>> TELESCOPE_ITEMS = new EnumMap<>(TelescopeType.class);
    public static final Map<ComponentType, RegistryObject<Item>> COMPONENTS = new EnumMap<>(ComponentType.class);

    static {
        for (ComponentType c : ComponentType.values()) {
            COMPONENTS.put(c, ITEMS.register(c.id, () -> new Item(new Item.Properties())));
        }
        for (TelescopeType t : TelescopeType.values()) {
            RegistryObject<TelescopeBlock> block = BLOCKS.register(t.id, () -> new TelescopeBlock(t,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 6.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()));
            TELESCOPE_BLOCKS.put(t, block);
            TELESCOPE_ITEMS.put(t, ITEMS.register(t.id, () -> new TelescopeItem(block.get(), new Item.Properties().stacksTo(1))));
        }
    }

    public static final RegistryObject<BlockEntityType<TelescopeBlockEntity>> TELESCOPE_BE =
            BLOCK_ENTITIES.register("telescope", () -> BlockEntityType.Builder.of(TelescopeBlockEntity::new,
                    TELESCOPE_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("telescopios", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.telescopio"))
            .icon(() -> new ItemStack(TELESCOPE_ITEMS.get(TelescopeType.SKYWATCHER_250PDS_EQ6R).get()))
            .displayItems((params, output) -> {
                TELESCOPE_ITEMS.values().forEach(item -> output.accept(item.get()));
                COMPONENTS.values().forEach(item -> output.accept(item.get()));
            })
            .build());

    private ModRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }
}
