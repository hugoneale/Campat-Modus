package io.github.hugoneale.campat.runtime.legacy.client;

import com.mojang.blaze3d.platform.Window;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.IModBusEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Forge 47's HUD overlay API (IGuiOverlay, VanillaGuiOverlay, RegisterGuiOverlaysEvent,
 * RenderGuiOverlayEvent, RenderGuiEvent), on top of the GUI layer bridge used for NeoForge mods.
 */
public final class LegacyGuiOverlays {
    private LegacyGuiOverlays() {}

    /** Forge 47's {@code IGuiOverlay} */
    @FunctionalInterface
    public interface IGuiOverlay {
        void render(LegacyForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight);
    }

    /** Forge 47's {@code NamedGuiOverlay} */
    public record NamedGuiOverlay(ResourceLocation id, IGuiOverlay overlay) {}

    /** Forge 47's {@code VanillaGuiOverlay}, each mapped to the layer that draws it now */
    public enum VanillaGuiOverlay {
        VIGNETTE("vignette", VanillaGuiLayers.CAMERA_OVERLAYS),
        SPYGLASS("spyglass", VanillaGuiLayers.CAMERA_OVERLAYS),
        HELMET("helmet", VanillaGuiLayers.CAMERA_OVERLAYS),
        FROSTBITE("frostbite", VanillaGuiLayers.CAMERA_OVERLAYS),
        PORTAL("portal", VanillaGuiLayers.CAMERA_OVERLAYS),
        HOTBAR("hotbar", VanillaGuiLayers.HOTBAR),
        CROSSHAIR("crosshair", VanillaGuiLayers.CROSSHAIR),
        BOSS_EVENT_PROGRESS("boss_event_progress", VanillaGuiLayers.BOSS_OVERLAY),
        PLAYER_HEALTH("player_health", VanillaGuiLayers.PLAYER_HEALTH),
        ARMOR_LEVEL("armor_level", VanillaGuiLayers.ARMOR_LEVEL),
        FOOD_LEVEL("food_level", VanillaGuiLayers.FOOD_LEVEL),
        AIR_LEVEL("air_level", VanillaGuiLayers.AIR_LEVEL),
        MOUNT_HEALTH("mount_health", VanillaGuiLayers.VEHICLE_HEALTH),
        JUMP_BAR("jump_bar", VanillaGuiLayers.JUMP_METER),
        EXPERIENCE_BAR("experience_bar", VanillaGuiLayers.EXPERIENCE_BAR),
        ITEM_NAME("item_name", VanillaGuiLayers.SELECTED_ITEM_NAME),
        SLEEP_FADE("sleep_fade", VanillaGuiLayers.SLEEP_OVERLAY),
        POTION_ICONS("potion_icons", VanillaGuiLayers.EFFECTS),
        DEBUG_TEXT("debug_text", VanillaGuiLayers.DEBUG_OVERLAY),
        FPS_GRAPH("fps_graph", VanillaGuiLayers.DEBUG_OVERLAY),
        RECORD_OVERLAY("record_overlay", VanillaGuiLayers.OVERLAY_MESSAGE),
        TITLE_TEXT("title_text", VanillaGuiLayers.TITLE),
        SUBTITLES("subtitles", VanillaGuiLayers.SUBTITLE_OVERLAY),
        SCOREBOARD("scoreboard", VanillaGuiLayers.SCOREBOARD_SIDEBAR),
        CHAT_PANEL("chat_panel", VanillaGuiLayers.CHAT),
        PLAYER_LIST("player_list", VanillaGuiLayers.TAB_LIST);

        private final ResourceLocation id;
        private final ResourceLocation layer;
        private final NamedGuiOverlay type;

        VanillaGuiOverlay(String id, ResourceLocation layer) {
            this.id = ResourceLocation.withDefaultNamespace(id);
            this.layer = layer;
            this.type = new NamedGuiOverlay(this.id, (gui, g, pt, w, h) -> {});
        }

        public ResourceLocation id() {
            return id;
        }

        public NamedGuiOverlay type() {
            return type;
        }

        public ResourceLocation layer() {
            return layer;
        }
    }

    private static final Map<ResourceLocation, NamedGuiOverlay> MODDED = new HashMap<>();

    /** built on first use: the enum's constants must be initialized first */
    private static final class Index {
        static final Map<ResourceLocation, VanillaGuiOverlay> BY_LAYER = new HashMap<>();
        static final Map<ResourceLocation, ResourceLocation> LEGACY_TO_LAYER = new HashMap<>();

        static {
            for (VanillaGuiOverlay o : VanillaGuiOverlay.values()) {
                BY_LAYER.putIfAbsent(o.layer, o);
                LEGACY_TO_LAYER.put(o.id, o.layer);
            }
        }
    }

    private static ResourceLocation layerOf(ResourceLocation legacyId) {
        return Index.LEGACY_TO_LAYER.getOrDefault(legacyId, legacyId);
    }

    /** the 1.20.1 overlay describing a layer, for RenderGuiOverlayEvent */
    public static NamedGuiOverlay overlayOf(ResourceLocation layer) {
        VanillaGuiOverlay v = Index.BY_LAYER.get(layer);
        if (v != null) return v.type();
        return MODDED.get(layer);
    }

    /** Forge 47's {@code GuiOverlayManager} */
    public static final class GuiOverlayManager {
        private GuiOverlayManager() {
        }

        public static NamedGuiOverlay findOverlay(ResourceLocation id) {
            return overlayOf(layerOf(id));
        }

        public static java.util.List<NamedGuiOverlay> getOverlays() {
            java.util.List<NamedGuiOverlay> all = new java.util.ArrayList<>();
            for (VanillaGuiOverlay o : VanillaGuiOverlay.values()) all.add(o.type());
            all.addAll(MODDED.values());
            return java.util.Collections.unmodifiableList(all);
        }
    }

    private static LayeredDraw.Layer layer(IGuiOverlay overlay) {
        return (g, delta) -> {
            Window w = Minecraft.getInstance().getWindow();
            overlay.render(LegacyForgeGui.get(), g, delta.getGameTimeDeltaPartialTick(false), w.getGuiScaledWidth(), w.getGuiScaledHeight());
        };
    }

    /** Forge 47's {@code RegisterGuiOverlaysEvent}, delegating to NeoForge's layer registration */
    public static class RegisterGuiOverlaysEvent extends Event implements IModBusEvent {
        private final RegisterGuiLayersEvent layers;

        public RegisterGuiOverlaysEvent() {
            this(null);
        }

        public RegisterGuiOverlaysEvent(RegisterGuiLayersEvent layers) {
            this.layers = layers;
        }

        private static ResourceLocation id(String path) {
            return ResourceLocation.fromNamespaceAndPath(ModLoadingContext.get().getActiveNamespace(), path);
        }

        private void remember(ResourceLocation id, IGuiOverlay overlay) {
            MODDED.put(id, new NamedGuiOverlay(id, overlay));
        }

        public void registerBelowAll(String id, IGuiOverlay overlay) {
            ResourceLocation rl = id(id);
            remember(rl, overlay);
            layers.registerBelowAll(rl, layer(overlay));
        }

        public void registerBelow(ResourceLocation other, String id, IGuiOverlay overlay) {
            ResourceLocation rl = id(id);
            remember(rl, overlay);
            layers.registerBelow(layerOf(other), rl, layer(overlay));
        }

        public void registerAbove(ResourceLocation other, String id, IGuiOverlay overlay) {
            ResourceLocation rl = id(id);
            remember(rl, overlay);
            layers.registerAbove(layerOf(other), rl, layer(overlay));
        }

        public void registerAboveAll(String id, IGuiOverlay overlay) {
            ResourceLocation rl = id(id);
            remember(rl, overlay);
            layers.registerAboveAll(rl, layer(overlay));
        }
    }

    /** Forge 47's {@code RenderGuiOverlayEvent} */
    public abstract static class RenderGuiOverlayEvent extends Event {
        private final Window window;
        private final GuiGraphics graphics;
        private final float partialTick;
        private final NamedGuiOverlay overlay;

        protected RenderGuiOverlayEvent() {
            this(null, null, 0f, null);
        }

        protected RenderGuiOverlayEvent(Window window, GuiGraphics graphics, float partialTick, NamedGuiOverlay overlay) {
            this.window = window;
            this.graphics = graphics;
            this.partialTick = partialTick;
            this.overlay = overlay;
        }

        public Window getWindow() {
            return window;
        }

        public GuiGraphics getGuiGraphics() {
            return graphics;
        }

        public float getPartialTick() {
            return partialTick;
        }

        public NamedGuiOverlay getOverlay() {
            return overlay;
        }

        @Cancelable
        public static class Pre extends RenderGuiOverlayEvent {
            public Pre() {}

            public Pre(Window window, GuiGraphics graphics, float partialTick, NamedGuiOverlay overlay) {
                super(window, graphics, partialTick, overlay);
            }
        }

        public static class Post extends RenderGuiOverlayEvent {
            public Post() {}

            public Post(Window window, GuiGraphics graphics, float partialTick, NamedGuiOverlay overlay) {
                super(window, graphics, partialTick, overlay);
            }
        }
    }

    /** Forge 47's {@code RenderGuiEvent} */
    public abstract static class RenderGuiEvent extends Event {
        private final Window window;
        private final GuiGraphics graphics;
        private final float partialTick;

        protected RenderGuiEvent() {
            this(null, null, 0f);
        }

        protected RenderGuiEvent(Window window, GuiGraphics graphics, float partialTick) {
            this.window = window;
            this.graphics = graphics;
            this.partialTick = partialTick;
        }

        public Window getWindow() {
            return window;
        }

        public GuiGraphics getGuiGraphics() {
            return graphics;
        }

        public float getPartialTick() {
            return partialTick;
        }

        @Cancelable
        public static class Pre extends RenderGuiEvent {
            public Pre() {}

            public Pre(Window window, GuiGraphics graphics, float partialTick) {
                super(window, graphics, partialTick);
            }
        }

        public static class Post extends RenderGuiEvent {
            public Post() {}

            public Post(Window window, GuiGraphics graphics, float partialTick) {
                super(window, graphics, partialTick);
            }
        }
    }

    // ---- hooks from the GUI bridge ----

    public static boolean firePre(GuiGraphics g, DeltaTracker delta) {
        LegacyForgeGui.beginFrame();
        return MinecraftForge.EVENT_BUS.post(new RenderGuiEvent.Pre(Minecraft.getInstance().getWindow(), g, delta.getGameTimeDeltaPartialTick(false)));
    }

    public static void firePost(GuiGraphics g, DeltaTracker delta) {
        MinecraftForge.EVENT_BUS.post(new RenderGuiEvent.Post(Minecraft.getInstance().getWindow(), g, delta.getGameTimeDeltaPartialTick(false)));
    }

    public static boolean fireLayerPre(ResourceLocation layer, GuiGraphics g, DeltaTracker delta) {
        NamedGuiOverlay o = overlayOf(layer);
        if (o == null) return false;
        return MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Pre(Minecraft.getInstance().getWindow(), g, delta.getGameTimeDeltaPartialTick(false), o));
    }

    public static void fireLayerPost(ResourceLocation layer, GuiGraphics g, DeltaTracker delta) {
        NamedGuiOverlay o = overlayOf(layer);
        if (o != null) MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Post(Minecraft.getInstance().getWindow(), g, delta.getGameTimeDeltaPartialTick(false), o));
    }

    static Map<VanillaGuiOverlay, ResourceLocation> layers() {
        Map<VanillaGuiOverlay, ResourceLocation> m = new EnumMap<>(VanillaGuiOverlay.class);
        for (VanillaGuiOverlay o : VanillaGuiOverlay.values()) m.put(o, o.layer);
        return m;
    }
}
