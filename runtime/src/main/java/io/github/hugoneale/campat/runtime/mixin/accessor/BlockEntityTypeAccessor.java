package io.github.hugoneale.campat.runtime.mixin.accessor;

import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {
    @Accessor("validBlocks")
    Set<Block> campat$getValidBlocks();

    @Accessor("validBlocks")
    @org.spongepowered.asm.mixin.Mutable
    void campat$setValidBlocks(Set<Block> blocks);
}
