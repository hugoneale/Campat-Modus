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

    /** getModels() of the baking events: keys may be given as plain ids; lambdas see plain ids */
    public static java.util.Map getModels(ModelEvent.ModifyBakingResult event) {
        return new ModelMap(event.getModels());
    }

    public static java.util.Map getModels(ModelEvent.BakingCompleted event) {
        return new ModelMap(event.getModels());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class ModelMap extends java.util.AbstractMap<Object, BakedModel> {
        private final java.util.Map<ModelResourceLocation, BakedModel> models;

        ModelMap(java.util.Map<ModelResourceLocation, BakedModel> models) {
            this.models = models;
        }

        private static ModelResourceLocation key(Object k) {
            if (k instanceof ModelResourceLocation m) return m;
            if (k instanceof ResourceLocation r) return ClientEventBridge.standalone(r);
            return null;
        }

        private static Object outward(ModelResourceLocation m) {
            return m.id();
        }

        @Override
        public BakedModel get(Object k) {
            ModelResourceLocation m = key(k);
            BakedModel b = m == null ? null : models.get(m);
            if (b == null && k instanceof ResourceLocation r && !(k instanceof ModelResourceLocation)) b = models.get(ModelResourceLocation.inventory(r));
            return b;
        }

        @Override
        public boolean containsKey(Object k) {
            return get(k) != null;
        }

        @Override
        public BakedModel put(Object k, BakedModel v) {
            return models.put(key(k), v);
        }

        @Override
        public BakedModel remove(Object k) {
            return models.remove(key(k));
        }

        @Override
        public BakedModel computeIfPresent(Object k, java.util.function.BiFunction f) {
            ModelResourceLocation m = key(k);
            if (m == null || !models.containsKey(m)) return null;
            BakedModel v = (BakedModel) f.apply(outward(m), models.get(m));
            if (v == null) models.remove(m);
            else models.put(m, v);
            return v;
        }

        @Override
        public BakedModel compute(Object k, java.util.function.BiFunction f) {
            ModelResourceLocation m = key(k);
            BakedModel v = (BakedModel) f.apply(outward(m), models.get(m));
            if (v == null) models.remove(m);
            else models.put(m, v);
            return v;
        }

        @Override
        public java.util.Set<Entry<Object, BakedModel>> entrySet() {
            java.util.Set<Entry<Object, BakedModel>> out = new java.util.LinkedHashSet<>();
            for (Entry<ModelResourceLocation, BakedModel> e : models.entrySet()) {
                out.add(new java.util.AbstractMap.SimpleEntry<>(outward(e.getKey()), e.getValue()) {
                    @Override
                    public BakedModel setValue(BakedModel value) {
                        models.put(e.getKey(), value);
                        return super.setValue(value);
                    }
                });
            }
            return out;
        }
    }

    public static void register(ModelEvent.RegisterAdditional event, Object id) {
        event.register(location(id));
    }

    public static BakedModel getModel(ModelManager manager, Object id) {
        return manager.getModel(location(id));
    }
}
