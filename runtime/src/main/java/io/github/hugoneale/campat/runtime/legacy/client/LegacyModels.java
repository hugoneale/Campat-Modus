package io.github.hugoneale.campat.runtime.legacy.client;

import io.github.hugoneale.campat.runtime.bridge.client.ClientEventBridge;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

/** 1.20.1 registered and looked up additional models by plain id; 1.21.1 wants a ModelResourceLocation. */
public final class LegacyModels {
    private LegacyModels() {}

    /** ModelResourceLocation extended ResourceLocation in 1.20.1, so either may arrive here */
    private static ModelResourceLocation location(Object id) {
        return id instanceof ModelResourceLocation m ? m : ClientEventBridge.standalone((ResourceLocation) id);
    }

    public static void register(ModelEvent.RegisterAdditional event, Object id) {
        event.register(location(id));
    }

    public static BakedModel getModel(ModelManager manager, Object id) {
        return manager.getModel(location(id));
    }
}
