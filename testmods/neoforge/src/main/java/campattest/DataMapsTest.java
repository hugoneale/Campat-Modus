package campattest;

import com.mojang.serialization.Codec;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.callback.AddCallback;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** NeoForge data maps (custom and builtin) and registry callbacks. */
final class DataMapsTest {
    static final DataMapType<Item, Integer> WEIGHT = DataMapType.builder(
        ResourceLocation.fromNamespaceAndPath(CampatTest.MODID, "weight"), Registries.ITEM, Codec.INT).build();
    static final AtomicInteger ADDED = new AtomicInteger();

    static void setup(IEventBus modBus) {
        modBus.addListener((RegisterDataMapTypesEvent e) -> {
            e.register(WEIGHT);
            T.pass("datamap.register");
        });
        BuiltInRegistries.ITEM.addCallback((AddCallback<Item>) (registry, id, key, value) -> ADDED.incrementAndGet());
    }

    static void verify() {
        T.check("registry.callback", ADDED.get() > 1000, ADDED.get());
        T.run("datamap.custom", () -> {
            Integer apple = BuiltInRegistries.ITEM.wrapAsHolder(Items.APPLE).getData(WEIGHT);
            Integer viaRegistry = BuiltInRegistries.ITEM.getData(WEIGHT, BuiltInRegistries.ITEM.getResourceKey(Items.APPLE).orElseThrow());
            Integer log = BuiltInRegistries.ITEM.wrapAsHolder(Items.OAK_LOG).getData(WEIGHT);
            T.check("datamap.custom", Integer.valueOf(7).equals(apple) && Integer.valueOf(7).equals(viaRegistry) && Integer.valueOf(3).equals(log),
                apple + " " + viaRegistry + " " + log);
        });
        T.check("datamap.builtin", ComposterBlock.COMPOSTABLES.getFloat(Items.DIRT) == 0.5f, ComposterBlock.COMPOSTABLES.getFloat(Items.DIRT));
    }
}
