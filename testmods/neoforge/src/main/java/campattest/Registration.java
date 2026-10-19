package campattest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

final class Registration {
    static void setup(IEventBus modBus) {
        CampatTest.ITEMS = DeferredRegister.createItems(CampatTest.MODID);
        CampatTest.BLOCKS = DeferredRegister.createBlocks(CampatTest.MODID);
        CampatTest.TEST_ITEM = CampatTest.ITEMS.registerSimpleItem("test_item");
        CampatTest.TEST_BLOCK = CampatTest.BLOCKS.registerSimpleBlock("test_block", BlockBehaviour.Properties.of());
        CampatTest.ITEMS.registerSimpleBlockItem(CampatTest.TEST_BLOCK);
        CampatTest.ITEMS.register(modBus);
        CampatTest.BLOCKS.register(modBus);
        T.pass("registries.deferred");
    }

    static void verify() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CampatTest.MODID, "test_item");
        T.check("registries.lookup", BuiltInRegistries.ITEM.containsKey(id) && CampatTest.TEST_ITEM.get() == BuiltInRegistries.ITEM.get(id)
            && CampatTest.TEST_BLOCK.get().asItem() != null, BuiltInRegistries.ITEM.containsKey(id));
        T.check("registries.holder", CampatTest.TEST_ITEM.getId().equals(id) && CampatTest.TEST_ITEM.isBound(), CampatTest.TEST_ITEM.getId());
    }
}
