package io.github.hugoneale.campat.runtime.client;

import io.github.hugoneale.campat.runtime.CampatModus;
import io.github.hugoneale.campat.runtime.lifecycle.NeoLifecycle;
import io.github.hugoneale.campat.runtime.mixin.client.ForgeLayeredDrawAccessor;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.GuiLayerManager;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NeoForge's GUI layers on top of Forge's {@link ForgeLayeredDraw}. NeoForge splits the vanilla HUD
 * into finer layers than Forge, so NeoForge layer names are mapped to the Forge layer containing
 * them; modded layers are inserted next to that layer, and every layer fires NeoForge's
 * {@link RenderGuiLayerEvent}s.
 */
public final class GuiLayerBridge {
    private static final Map<ResourceLocation, ResourceLocation> NEO_TO_FORGE = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> FORGE_TO_NEO = new HashMap<>();
    /** NeoForge's vanilla layer order */
    private static final List<ResourceLocation> NEO_VANILLA = List.of(
        VanillaGuiLayers.CAMERA_OVERLAYS, VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.HOTBAR, VanillaGuiLayers.JUMP_METER,
        VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL,
        VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.SPECTATOR_TOOLTIP,
        VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.EFFECTS, VanillaGuiLayers.BOSS_OVERLAY, VanillaGuiLayers.SLEEP_OVERLAY,
        VanillaGuiLayers.DEMO_OVERLAY, VanillaGuiLayers.DEBUG_OVERLAY, VanillaGuiLayers.SCOREBOARD_SIDEBAR, VanillaGuiLayers.OVERLAY_MESSAGE,
        VanillaGuiLayers.TITLE, VanillaGuiLayers.CHAT, VanillaGuiLayers.TAB_LIST, VanillaGuiLayers.SUBTITLE_OVERLAY, VanillaGuiLayers.SAVING_INDICATOR);

    static {
        map(VanillaGuiLayers.CAMERA_OVERLAYS, ForgeLayeredDraw.CAMERA_OVERLAY);
        map(VanillaGuiLayers.CROSSHAIR, ForgeLayeredDraw.CROSSHAIR);
        map(VanillaGuiLayers.HOTBAR, ForgeLayeredDraw.HOTBAR);
        for (ResourceLocation r : List.of(VanillaGuiLayers.JUMP_METER, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.SPECTATOR_TOOLTIP)) NEO_TO_FORGE.put(r, ForgeLayeredDraw.HOTBAR);
        map(VanillaGuiLayers.EXPERIENCE_BAR, ForgeLayeredDraw.EXPERIENCE);
        NEO_TO_FORGE.put(VanillaGuiLayers.EXPERIENCE_LEVEL, ForgeLayeredDraw.EXPERIENCE);
        map(VanillaGuiLayers.EFFECTS, ForgeLayeredDraw.POTION_EFFECTS);
        map(VanillaGuiLayers.BOSS_OVERLAY, ForgeLayeredDraw.BOSS_OVERLAY);
        map(VanillaGuiLayers.SLEEP_OVERLAY, ForgeLayeredDraw.SLEEP_OVERLAY);
        map(VanillaGuiLayers.DEMO_OVERLAY, ForgeLayeredDraw.DEMO_OVERLAY);
        map(VanillaGuiLayers.DEBUG_OVERLAY, ForgeLayeredDraw.DEBUG_OVERLAY);
        map(VanillaGuiLayers.SCOREBOARD_SIDEBAR, ForgeLayeredDraw.SCOREBOARD);
        map(VanillaGuiLayers.OVERLAY_MESSAGE, ForgeLayeredDraw.HOTBAR_MESSAGE);
        map(VanillaGuiLayers.TITLE, ForgeLayeredDraw.TITLE_OVERLAY);
        map(VanillaGuiLayers.CHAT, ForgeLayeredDraw.CHAT_OVERLAY);
        map(VanillaGuiLayers.TAB_LIST, ForgeLayeredDraw.TAB_LIST);
        map(VanillaGuiLayers.SUBTITLE_OVERLAY, ForgeLayeredDraw.SUBTITLE_OVERLAY);
        NEO_TO_FORGE.put(VanillaGuiLayers.SAVING_INDICATOR, ForgeLayeredDraw.SUBTITLE_OVERLAY);
    }

    private static void map(ResourceLocation neo, ResourceLocation forge) {
        NEO_TO_FORGE.put(neo, forge);
        FORGE_TO_NEO.put(forge, neo);
    }

    private GuiLayerBridge() {}

    private record Placeholder() implements LayeredDraw.Layer {
        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.DeltaTracker t) {}
    }

    public static void onAddLayers(AddGuiOverlayLayersEvent event) {
        ForgeLayeredDraw root = event.getLayeredDraw();
        List<GuiLayerManager.NamedLayer> layers = new ArrayList<>();
        Map<ResourceLocation, LayeredDraw.Layer> placeholders = new HashMap<>();
        for (ResourceLocation r : NEO_VANILLA) {
            Placeholder p = new Placeholder();
            placeholders.put(r, p);
            layers.add(new GuiLayerManager.NamedLayer(r, p));
        }
        NeoLifecycle.postModBus(new RegisterGuiLayersEvent(layers));

        ResourceLocation previousVanilla = null;
        List<GuiLayerManager.NamedLayer> leading = new ArrayList<>();
        for (GuiLayerManager.NamedLayer l : layers) {
            if (placeholders.get(l.name()) == l.layer()) {
                previousVanilla = l.name();
                continue;
            }
            if (placeholders.containsKey(l.name())) {
                // wrapLayer/replaceLayer on a vanilla layer: Forge layers are coarser, so the
                // replacement is drawn in addition to Forge's layer instead of instead of it
                CampatModus.LOGGER.warn("GUI layer {} was replaced by a NeoForge mod; Forge draws it as part of {}, the replacement is added next to it",
                    l.name(), NEO_TO_FORGE.get(l.name()));
            }
            if (previousVanilla == null) {
                leading.add(l);
            } else {
                insertAbove(root, NEO_TO_FORGE.getOrDefault(previousVanilla, ForgeLayeredDraw.SUBTITLE_OVERLAY), l);
            }
            previousVanilla = placeholders.containsKey(l.name()) ? previousVanilla : l.name();
            NEO_TO_FORGE.putIfAbsent(l.name(), l.name());
        }
        for (int i = leading.size() - 1; i >= 0; i--) {
            GuiLayerManager.NamedLayer l = leading.get(i);
            root.addBelow(ForgeLayeredDraw.PRE_SLEEP_STACK, l.name(), ForgeLayeredDraw.CAMERA_OVERLAY, l.layer());
        }
        wrapAll(root);
    }

    private static void insertAbove(ForgeLayeredDraw root, ResourceLocation forgeOther, GuiLayerManager.NamedLayer l) {
        ResourceLocation stack = stackOf(root, forgeOther);
        if (stack == null) {
            root.add(ForgeLayeredDraw.POST_SLEEP_STACK, l.name(), l.layer());
        } else {
            root.addAbove(stack, l.name(), forgeOther, l.layer());
        }
    }

    private static ResourceLocation stackOf(ForgeLayeredDraw root, ResourceLocation layer) {
        for (var e : ((ForgeLayeredDrawAccessor) (Object) root).campat$subLayerStacks().entrySet()) {
            if (e.getValue().getKey() instanceof ForgeLayeredDraw sub && ((ForgeLayeredDrawAccessor) (Object) sub).campat$namedLayers().containsKey(layer)) return e.getKey();
        }
        return ((ForgeLayeredDrawAccessor) (Object) root).campat$namedLayers().containsKey(layer) ? ForgeLayeredDraw.VANILLA_ROOT : null;
    }

    /** Wraps every named layer so NeoForge's per layer events fire. */
    private static void wrapAll(ForgeLayeredDraw draw) {
        ForgeLayeredDrawAccessor acc = (ForgeLayeredDrawAccessor) (Object) draw;
        acc.campat$namedLayers().replaceAll((name, layer) -> layer instanceof EventLayer ? layer : new EventLayer(FORGE_TO_NEO.getOrDefault(name, name), layer));
        for (var e : acc.campat$subLayerStacks().values()) {
            if (e.getKey() instanceof ForgeLayeredDraw sub) wrapAll(sub);
        }
    }

    private record EventLayer(ResourceLocation name, LayeredDraw.Layer inner) implements LayeredDraw.Layer {
        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.DeltaTracker t) {
            if (MinecraftForge.EVENT_BUS.post(new RenderGuiLayerEvent.Pre(g, t, name, inner))) return;
            inner.render(g, t);
            MinecraftForge.EVENT_BUS.post(new RenderGuiLayerEvent.Post(g, t, name, inner));
        }
    }
}
