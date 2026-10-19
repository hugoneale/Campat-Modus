package io.github.hugoneale.campat.runtime.lifecycle;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Fires NeoForge-only mod bus events at the point in loading where NeoForge fires them. */
public final class NeoLifecycle {
    private NeoLifecycle() {}

    /** Posts a mod bus event to every mod, in mod order, like NeoForge's {@code ModLoader.postEvent}. */
    public static <T extends Event & IModBusEvent> T postModBus(T event) {
        ModList.get().forEachModContainer((id, container) -> {
            if (container instanceof FMLModContainer fml) fml.getEventBus().post(event);
        });
        return event;
    }

    /** Listeners NeoForgeMod registers for NeoForge's own systems. */
    public static void register(net.minecraftforge.eventbus.api.IEventBus modBus) {
        modBus.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH, net.neoforged.neoforge.capabilities.CapabilityHooks::markProxyableCapabilities);
        modBus.addListener(net.neoforged.neoforge.capabilities.CapabilityHooks::registerVanillaProviders);
        modBus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOW, net.neoforged.neoforge.capabilities.CapabilityHooks::registerFallbackVanillaProviders);
        modBus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, io.github.hugoneale.campat.runtime.capability.CapabilityInterop::registerFallbacks);
        var game = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        game.addListener(net.neoforged.neoforge.capabilities.CapabilityHooks::invalidateCapsOnChunkLoad);
        game.addListener(net.neoforged.neoforge.capabilities.CapabilityHooks::invalidateCapsOnChunkUnload);
        game.addListener(net.neoforged.neoforge.capabilities.CapabilityHooks::cleanCapabilityListenerReferencesOnTick);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // NeoForge sets up its network registry right after common setup.
        event.enqueueWork(() -> {
            postModBus(new RegisterPayloadHandlersEvent());
            io.github.hugoneale.campat.runtime.events.EventShimBridge.afterRegistration();
            io.github.hugoneale.campat.runtime.datamap.RegistryCallbacks.bake();
            io.github.hugoneale.campat.runtime.datamap.DataMaps.fireRegisterTypes();
            // NeoForge fires RegisterCapabilitiesEvent once registries are frozen
            net.neoforged.neoforge.capabilities.CapabilityHooks.init();
        });
    }
}
