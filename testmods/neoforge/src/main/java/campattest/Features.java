package campattest;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Attachments, capabilities and NeoForge's extension methods on vanilla classes. */
final class Features {
    static final BlockPos CAP_POS = new BlockPos(0, 100, 0);
    static final BlockPos CHEST_POS = new BlockPos(2, 100, 0);

    static DeferredRegister<AttachmentType<?>> ATTACHMENTS;
    static Supplier<AttachmentType<Integer>> COUNTER;
    static DeferredBlock<CapBlock> CAP_BLOCK;
    static DeferredHolder<BlockEntityType<?>, BlockEntityType<CapBlockEntity>> CAP_BE;

    static void setup(IEventBus modBus) {
        ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CampatTest.MODID);
        COUNTER = ATTACHMENTS.register("counter", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
        ATTACHMENTS.register(modBus);

        DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(CampatTest.MODID);
        CAP_BLOCK = blocks.register("cap_block", () -> new CapBlock(BlockBehaviour.Properties.of()));
        blocks.register(modBus);
        DeferredRegister<BlockEntityType<?>> bes = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CampatTest.MODID);
        CAP_BE = bes.register("cap_block", () -> BlockEntityType.Builder.of(CapBlockEntity::new, CAP_BLOCK.get()).build(null));
        bes.register(modBus);

        modBus.addListener(Features::registerCaps);
        T.pass("features.setup");
    }

    private static void registerCaps(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CAP_BE.get(), (be, side) -> be.handler);
        T.pass("capability.register");
    }

    static void onServerStarted(ServerLevel level) {
        T.run("capability.neo", () -> {
            level.setBlockAndUpdate(CAP_POS, CAP_BLOCK.get().defaultBlockState());
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, CAP_POS, Direction.UP);
            T.check("capability.neo", h != null && h.getSlots() == 1, h);
            h.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
        });
        T.run("capability.vanilla", () -> {
            level.setBlockAndUpdate(CHEST_POS, Blocks.CHEST.defaultBlockState());
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, CHEST_POS, Direction.UP);
            T.check("capability.vanilla", h != null && h.getSlots() == 27, h);
        });
        T.run("attachment.level", () -> {
            level.setData(COUNTER, 41);
            T.check("attachment.level", level.getData(COUNTER) == 41 && level.hasData(COUNTER), level.getData(COUNTER));
        });
        T.run("extension.itemstack", () -> T.check("extension.itemstack",
            new ItemStack(Items.DIAMOND_AXE).canPerformAction(ItemAbilities.AXE_DIG) && !new ItemStack(Items.STICK).canPerformAction(ItemAbilities.AXE_DIG), "axe"));
        T.run("extension.block", () -> T.check("extension.block",
            Blocks.OAK_PLANKS.defaultBlockState().isFlammable(level, CAP_POS, Direction.UP) && !Blocks.STONE.defaultBlockState().isFlammable(level, CAP_POS, Direction.UP), "flammable"));
    }

    static void onLogin(Player player) {
        T.run("attachment.entity", () -> {
            int before = player.getData(COUNTER);
            player.setData(COUNTER, before + 1);
            T.check("attachment.entity", player.getData(COUNTER) == before + 1, before);
        });
    }
}
