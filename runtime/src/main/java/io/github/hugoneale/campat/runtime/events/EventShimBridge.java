package io.github.hugoneale.campat.runtime.events;

import com.mojang.datafixers.util.Either;
import io.github.hugoneale.campat.runtime.legacy.LegacyRegistries;
import io.github.hugoneale.campat.runtime.lifecycle.NeoLifecycle;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.world.level.BaseSpawner;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.event.network.GatherLoginConfigurationTasksEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

/** Fires NeoForge-only events from their nearest Forge counterparts and copies results back. */
public final class EventShimBridge {
    private EventShimBridge() {}

    public static void register() {
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, false, BrewingRecipeRegisterEvent.class,
            e -> bus.post(new RegisterBrewingRecipesEvent(e.getBuilder(), LegacyRegistries.access())));
        bus.addListener(EventPriority.HIGHEST, true, MobSpawnEvent.FinalizeSpawn.class, EventShimBridge::finalizeSpawn);
        bus.addListener(EventPriority.HIGHEST, true, ShieldBlockEvent.class, EventShimBridge::shieldBlock);
        bus.addListener(EventPriority.HIGHEST, false, SleepingLocationCheckEvent.class, EventShimBridge::sleeping);
        bus.addListener(EventPriority.HIGHEST, false, GatherLoginConfigurationTasksEvent.class, e -> {
            if (e.getConnection().getPacketListener() instanceof ServerConfigurationPacketListener l) {
                NeoLifecycle.postModBus(new RegisterConfigurationTasksEvent(l, e::addTask));
            }
        });
    }

    /** mod bus, after registries are populated */
    public static void afterRegistration() {
        NeoLifecycle.postModBus(new BlockEntityTypeAddBlocksEvent());
    }

    private static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn e) {
        BaseSpawner spawner = e.getSpawner();
        Either<net.minecraft.world.level.block.entity.BlockEntity, net.minecraft.world.entity.Entity> source = null;
        if (spawner != null) {
            if (spawner.getSpawnerBlockEntity() != null) source = Either.left(spawner.getSpawnerBlockEntity());
            else if (spawner.getSpawnerEntity() != null) source = Either.right(spawner.getSpawnerEntity());
        }
        FinalizeSpawnEvent neo = new FinalizeSpawnEvent(e.getEntity(), e.getLevel(), e.getX(), e.getY(), e.getZ(),
            e.getDifficulty(), e.getSpawnType(), e.getSpawnData(), source);
        MinecraftForge.EVENT_BUS.post(neo);
        e.setDifficulty(neo.getDifficulty());
        e.setSpawnData(neo.getSpawnData());
        if (neo.isSpawnCancelled()) e.setSpawnCancelled(true);
        if (neo.isCanceled()) e.setCanceled(true);
    }

    private static void shieldBlock(ShieldBlockEvent e) {
        LivingShieldBlockEvent neo = new LivingShieldBlockEvent(e.getEntity(), e.getDamageSource(), e.getBlockedDamage(), true);
        MinecraftForge.EVENT_BUS.post(neo);
        if (neo.isCanceled() || !neo.getBlocked()) {
            e.setCanceled(true);
            return;
        }
        e.setBlockedDamage(neo.getBlockedDamage());
        if (neo.shieldDamage() <= 0) e.setShieldTakesDamage(false);
    }

    private static void sleeping(SleepingLocationCheckEvent e) {
        CanContinueSleepingEvent neo = new CanContinueSleepingEvent(e.getEntity(), null);
        MinecraftForge.EVENT_BUS.post(neo);
        if (!neo.mayContinueSleeping()) e.setResult(Event.Result.DENY);
    }
}
