package io.github.hugoneale.campat.runtime.bridge;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/**
 * Vanilla fields NeoForge's access transformer makes public but Forge keeps private (Forge redirects
 * them to accessor methods with a coremod). Field reads in converted mods are redirected here.
 */
public final class FieldBridge {
    private FieldBridge() {}

    public static Fluid bucketContent(BucketItem item) {
        return item.getFluid();
    }

    public static FlowingFluid liquidBlockFluid(LiquidBlock block) {
        return block.getFluid();
    }

    /** values written to ChunkGenerator.featuresPerStep by NeoForge mixins (Supplier there, ClearableLazy on Forge) */
    public static net.minecraftforge.common.util.ClearableLazy clearableLazy(java.util.function.Supplier supplier) {
        return supplier instanceof net.minecraftforge.common.util.ClearableLazy c ? c : net.minecraftforge.common.util.ClearableLazy.of(supplier);
    }
}
