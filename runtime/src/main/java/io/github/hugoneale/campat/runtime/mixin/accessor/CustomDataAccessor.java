package io.github.hugoneale.campat.runtime.mixin.accessor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CustomData.class)
public interface CustomDataAccessor {
    /** wraps the tag without copying it */
    @Invoker("<init>")
    static CustomData campat$wrap(CompoundTag tag) {
        throw new AssertionError();
    }
}
