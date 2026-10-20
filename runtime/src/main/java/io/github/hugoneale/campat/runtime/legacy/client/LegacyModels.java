package io.github.hugoneale.campat.runtime.legacy.client;

import io.github.hugoneale.campat.runtime.bridge.client.ClientEventBridge;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

/** 1.20.1 registered and looked up additional models by plain id; 1.21.1 wants a ModelResourceLocation. */
public final class LegacyModels {
    private LegacyModels() {}

    public static void register(ModelEvent.RegisterAdditional event, ResourceLocation id) {
        event.register(ClientEventBridge.standalone(id));
    }

    public static BakedModel getModel(ModelManager manager, ResourceLocation id) {
        return manager.getModel(ClientEventBridge.standalone(id));
    }
}
