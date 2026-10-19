package io.github.hugoneale.campat.runtime.bridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/** Client-side NeoForge event members that Forge's events lack or type differently. */
@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class ClientEventBridge {
    private ClientEventBridge() {}

    // ---- RenderLevelStageEvent: NeoForge hands out a PoseStack and a DeltaTracker ----

    public static PoseStack getPoseStack(RenderLevelStageEvent event) {
        PoseStack stack = new PoseStack();
        stack.mulPose(event.getPoseStack());
        return stack;
    }

    public static org.joml.Matrix4f getModelViewMatrix(RenderLevelStageEvent event) {
        return event.getPoseStack();
    }

    public static DeltaTracker getPartialTick(RenderLevelStageEvent event) {
        return Minecraft.getInstance().getTimer();
    }

    // ---- ItemTooltipEvent ----

    public static Item.TooltipContext getContext(ItemTooltipEvent event) {
        var level = Minecraft.getInstance().level;
        return level != null ? Item.TooltipContext.of(level) : Item.TooltipContext.EMPTY;
    }

    // ---- CustomizeGuiOverlayEvent.DebugText: Forge fires it once per side ----

    public static List<String> getLeft(CustomizeGuiOverlayEvent.DebugText event) {
        return event.getSide() == CustomizeGuiOverlayEvent.DebugText.Side.Left ? event.getText() : new ArrayList<>();
    }

    public static List<String> getRight(CustomizeGuiOverlayEvent.DebugText event) {
        return event.getSide() == CustomizeGuiOverlayEvent.DebugText.Side.Right ? event.getText() : new ArrayList<>();
    }

    // ---- ModelEvent.RegisterGeometryLoaders: NeoForge takes the full id ----

    private static java.lang.reflect.Field loadersField;

    public static void register(ModelEvent.RegisterGeometryLoaders event, ResourceLocation id, IGeometryLoader loader) {
        try {
            if (loadersField == null) {
                loadersField = ModelEvent.RegisterGeometryLoaders.class.getDeclaredField("loaders");
                loadersField.setAccessible(true);
            }
            Map<ResourceLocation, IGeometryLoader<?>> loaders = (Map<ResourceLocation, IGeometryLoader<?>>) loadersField.get(event);
            if (loaders.containsKey(id)) throw new IllegalStateException("Geometry loader already registered: " + id);
            loaders.put(id, loader);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- ModelResourceLocation.standalone (NeoForge patch) ----

    public static final String STANDALONE_VARIANT = "standalone";

    public static ModelResourceLocation standalone(ResourceLocation location) {
        return new ModelResourceLocation(location, STANDALONE_VARIANT);
    }
}
