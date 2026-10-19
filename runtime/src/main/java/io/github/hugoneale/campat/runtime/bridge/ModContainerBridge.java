package io.github.hugoneale.campat.runtime.bridge;

import io.github.hugoneale.campat.runtime.CampatModus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.function.Supplier;

/** NeoForge {@code ModContainer} methods missing on Forge's {@code ModContainer}. */
@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class ModContainerBridge {
    private ModContainerBridge() {}

    public static IEventBus getEventBus(ModContainer container) {
        if (container instanceof FMLModContainer fml) return fml.getEventBus();
        return null;
    }

    public static void registerConfig(ModContainer container, ModConfig.Type type, IConfigSpec spec) {
        container.addConfig(new ModConfig(type, spec, container));
        ConfigBridge.afterRegister(container, type);
    }

    public static void registerConfig(ModContainer container, ModConfig.Type type, IConfigSpec spec, String fileName) {
        container.addConfig(new ModConfig(type, spec, container, fileName));
        ConfigBridge.afterRegister(container, type);
    }

    public static void registerExtensionPoint(ModContainer container, Class point, IExtensionPoint extension) {
        register(container, point, extension);
    }

    public static void registerExtensionPoint(ModContainer container, Class point, Supplier extension) {
        register(container, point, extension.get());
    }

    private static void register(ModContainer container, Class point, Object extension) {
        if (extension instanceof IExtensionPoint.DisplayTest dt) {
            container.registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> dt);
            return;
        }
        if (FMLEnvironment.dist == Dist.CLIENT && ClientExtensions.tryRegister(container, extension)) return;
        // Anything else is only visible through getCustomExtension, as on NeoForge.
        Supplier supplier = () -> extension;
        ((ModContainer) container).registerExtensionPoint((Class) point, supplier);
    }

    public static void acceptEvent(ModContainer container, Event event) {
        IEventBus bus = getEventBus(container);
        if (bus != null) bus.post(event);
    }

    public static void acceptEvent(ModContainer container, EventPriority phase, Event event) {
        acceptEvent(container, event);
    }

    /** Kept separate so dedicated servers never load client classes. */
    static final class ClientExtensions {
        static boolean tryRegister(ModContainer container, Object extension) {
            try {
                return io.github.hugoneale.campat.runtime.client.ClientExtensionPoints.register(container, extension);
            } catch (LinkageError e) {
                CampatModus.LOGGER.debug("Client extension point bridge unavailable", e);
                return false;
            }
        }
    }
}
