package io.github.hugoneale.campat.runtime.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client side extension points: NeoForge's config screen factory becomes Forge's. */
public final class ClientExtensionPoints {
    private ClientExtensionPoints() {}

    public static boolean register(ModContainer container, Object extension) {
        if (extension instanceof IConfigScreenFactory factory) {
            container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> factory.createScreen(container, parent)));
            return true;
        }
        return false;
    }
}
