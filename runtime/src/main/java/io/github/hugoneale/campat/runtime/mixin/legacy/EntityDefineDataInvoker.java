package io.github.hugoneale.campat.runtime.mixin.legacy;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityDefineDataInvoker {
    @Invoker("defineSynchedData")
    void campat$invokeDefineSynchedData(SynchedEntityData.Builder builder);
}
