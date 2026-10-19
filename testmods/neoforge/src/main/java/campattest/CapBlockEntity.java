package campattest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CapBlockEntity extends BlockEntity {
    final ItemStackHandler handler = new ItemStackHandler(1);

    public CapBlockEntity(BlockPos pos, BlockState state) {
        super(Features.CAP_BE.get(), pos, state);
    }
}
