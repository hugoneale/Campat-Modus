package io.github.hugoneale.campat.runtime.client;

import io.github.hugoneale.campat.runtime.lifecycle.NeoLifecycle;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.ClientExtensionsManager;

import java.util.IdentityHashMap;
import java.util.Map;

/** Client-only NeoForge mod bus events, fired where NeoForge fires them. */
public final class ClientLifecycle {
    private ClientLifecycle() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(GuiLayerBridge::onAddLayers);
        modBus.addListener(ClientLifecycle::onClientSetup);
        ClientTickBridge.register();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientExtensionsManager.init();
            Map<MenuType<?>, MenuScreens.ScreenConstructor<?, ?>> screens = new IdentityHashMap<>();
            NeoLifecycle.postModBus(new RegisterMenuScreensEvent(screens));
            screens.forEach((type, ctor) -> MenuScreens.register((MenuType<AbstractContainerMenu>) type, (MenuScreens.ScreenConstructor) ctor));
        });
    }
}
