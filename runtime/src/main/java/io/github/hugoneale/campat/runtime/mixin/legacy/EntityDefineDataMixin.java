package io.github.hugoneale.campat.runtime.mixin.legacy;

import io.github.hugoneale.campat.runtime.legacy.LegacyEntityData;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Publishes the synched data builder while {@code defineSynchedData} runs, for 1.20.1 style entities. */
@Mixin(Entity.class)
public abstract class EntityDefineDataMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V"))
    private void campat$defineSynchedData(Entity self, SynchedEntityData.Builder builder) {
        LegacyEntityData.push(builder);
        try {
            ((EntityDefineDataInvoker) self).campat$invokeDefineSynchedData(builder);
        } finally {
            LegacyEntityData.pop();
        }
    }
}
