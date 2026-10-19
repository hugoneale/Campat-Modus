package io.github.hugoneale.campat.runtime.mixin.client;

import io.github.hugoneale.campat.runtime.events.TickBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientLevel.class)
public abstract class ClientEntityTickMixin {
    @Redirect(method = {"tickNonPassenger", "tickPassenger"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void campat$entityTick(Entity entity) {
        if (TickBridge.entityTickPre(entity)) {
            entity.tick();
            TickBridge.entityTickPost(entity);
        }
    }
}
