package campattest;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

final class ClientFeatures {
    static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(CampatTest.MODID, "test_layer");
    static final IClientItemExtensions EXT = new IClientItemExtensions() {};
    static boolean layerRendered, vanillaLayerEvent, guiEvent, ticked;

    static void setup(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, e -> {
            e.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, (g, t) -> {
                if (!layerRendered) { layerRendered = true; T.pass("client.guilayer.render"); }
            });
            T.pass("client.guilayer.register");
        });
        modBus.addListener(RegisterMenuScreensEvent.class, e -> T.pass("client.menuscreens"));
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
            e.register(new KeyMapping("key.campattest.test", 'K', "key.categories.misc"));
            T.pass("client.keymapping");
        });
        modBus.addListener(RegisterClientExtensionsEvent.class, e -> {
            e.registerItem(EXT, CampatTest.TEST_ITEM.get());
            T.pass("client.extensions.register");
        });
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, e -> {
            if (!vanillaLayerEvent && e.getName().equals(VanillaGuiLayers.CROSSHAIR)) { vanillaLayerEvent = true; T.pass("client.guilayer.event"); }
        });
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, e -> {
            if (!guiEvent) { guiEvent = true; T.pass("client.guievent"); }
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            if (!ticked) {
                ticked = true;
                T.pass("client.tick");
                T.check("client.extensions.lookup", IClientItemExtensions.of(CampatTest.TEST_ITEM.get()) == EXT, IClientItemExtensions.of(CampatTest.TEST_ITEM.get()));
            }
        });
    }
}
