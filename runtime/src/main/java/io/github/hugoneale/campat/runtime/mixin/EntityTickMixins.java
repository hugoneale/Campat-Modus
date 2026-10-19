package io.github.hugoneale.campat.runtime.mixin;

import io.github.hugoneale.campat.runtime.events.TickBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge fires {@code EntityTickEvent.Pre/Post} around entity ticks; Forge has no equivalent. */
@Mixin(ServerLevel.class)
public abstract class EntityTickMixins {
    @Redirect(method = {"tickNonPassenger", "tickPassenger"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void campat$entityTick(Entity entity) {
        if (TickBridge.entityTickPre(entity)) {
            entity.tick();
            TickBridge.entityTickPost(entity);
        }
    }
}
