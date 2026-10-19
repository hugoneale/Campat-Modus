package net.neoforged.neoforge.event;

import io.github.hugoneale.campat.runtime.mixin.accessor.BlockEntityTypeAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/** NeoForge's event for adding valid blocks to block entity types (mod bus, fired after registration). */
public class BlockEntityTypeAddBlocksEvent extends Event implements IModBusEvent {
    public BlockEntityTypeAddBlocksEvent() {}

    public void modify(BlockEntityType<?> type, Block... blocks) {
        BlockEntityTypeAccessor acc = (BlockEntityTypeAccessor) type;
        Set<Block> set = new HashSet<>(acc.campat$getValidBlocks());
        set.addAll(List.of(blocks));
        acc.campat$setValidBlocks(set);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void modify(ResourceKey<BlockEntityType<?>> key, Block... blocks) {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get((ResourceKey) key);
        if (type != null) modify(type, blocks);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void modify(BiPredicate<ResourceKey<BlockEntityType<?>>, BlockEntityType<?>> filter, Block... blocks) {
        for (var e : BuiltInRegistries.BLOCK_ENTITY_TYPE.entrySet()) {
            if (filter.test((ResourceKey) e.getKey(), e.getValue())) modify(e.getValue(), blocks);
        }
    }
}
