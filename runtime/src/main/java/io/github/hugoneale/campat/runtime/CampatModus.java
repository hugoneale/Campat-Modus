package io.github.hugoneale.campat.runtime;

import com.mojang.logging.LogUtils;
import io.github.hugoneale.campat.runtime.lifecycle.NeoLifecycle;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CampatModus.MOD_ID)
public final class CampatModus {
    public static final String MOD_ID = "campat_modus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CampatModus(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();
        modBus.addListener(NeoLifecycle::onCommonSetup);
        modBus.addListener(net.neoforged.neoforge.common.DeferredSpawnEggItem.CommonHandler::onCommonSetup);
        NeoLifecycle.register(modBus);
        modBus.addListener(io.github.hugoneale.campat.runtime.registry.NeoRegistries::onNewRegistry);
        modBus.addListener(io.github.hugoneale.campat.runtime.crafting.CustomIngredients::onRegister);
        modBus.addListener(io.github.hugoneale.campat.runtime.legacy.LegacyCriteria::onRegister);
        modBus.addListener(io.github.hugoneale.campat.runtime.legacy.ench.LegacyEnchantments::onNewRegistry);
        modBus.addListener(io.github.hugoneale.campat.runtime.legacy.forge.LegacyForgeCrafting::onRegister);
        io.github.hugoneale.campat.runtime.crafting.CustomIngredients.registerBuiltins(modBus);
        io.github.hugoneale.campat.runtime.registry.RuntimeRegistrations.register(modBus);
        io.github.hugoneale.campat.runtime.events.TickBridge.register();
        io.github.hugoneale.campat.runtime.events.EventShimBridge.register();
        io.github.hugoneale.campat.runtime.datamap.DataMaps.register();
        io.github.hugoneale.campat.runtime.datamap.RegistryCallbacks.register(modBus);
        io.github.hugoneale.campat.runtime.attachment.AttachmentPersistence.register();
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            io.github.hugoneale.campat.runtime.client.ClientLifecycle.register(modBus);
        }
        LOGGER.info("Campat Modus runtime loaded");
    }
}
