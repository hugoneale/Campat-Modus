package campattest;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CampatTest.MODID)
public class CampatTest {
    public static final String MODID = "campattest";

    static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static final ModConfigSpec.BooleanValue FLAG = BUILDER.comment("test flag").define("flag", true);
    static final ModConfigSpec.IntValue NUMBER = BUILDER.defineInRange("number", 42, 0, 100);
    static final ModConfigSpec SPEC = BUILDER.build();

    static DeferredRegister.Items ITEMS;
    static DeferredRegister.Blocks BLOCKS;
    static DeferredItem<Item> TEST_ITEM;
    static DeferredBlock<Block> TEST_BLOCK;

    public CampatTest(IEventBus modBus, ModContainer container, Dist dist) {
        T.check("construct.args", modBus != null && container != null && container.getModId().equals(MODID) && dist == FMLEnvironment.dist,
            modBus + " " + container + " " + dist);
        T.run("registries.deferred", () -> Registration.setup(modBus));
        T.run("config.register", () -> {
            container.registerConfig(ModConfig.Type.COMMON, SPEC);
            T.pass("config.register");
        });
        T.run("modlist", () -> T.check("modlist", ModList.get().isLoaded("campattest") && ModList.get().isLoaded("minecraft"), "isLoaded"));
        T.run("fmlpaths", () -> T.check("fmlpaths", FMLPaths.CONFIGDIR.get() != null, "configdir"));
        T.run("bus.modlistener", () -> modBus.addListener(this::commonSetup));
        T.run("bus.typed", () -> modBus.addListener(FMLCommonSetupEvent.class, e -> T.pass("bus.typed")));
        T.run("network.register", () -> Network.setup(modBus));
        T.run("bus.game", () -> NeoForge.EVENT_BUS.addListener(this::serverStarted));
        T.run("bus.login", () -> NeoForge.EVENT_BUS.addListener(this::login));
        T.run("ticks", () -> Ticks.setup());
        T.run("features.setup", () -> Features.setup(modBus));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        T.pass("lifecycle.commonsetup");
        T.run("config.values", () -> T.check("config.values", FLAG.get() && NUMBER.get() == 42, FLAG.get() + " " + NUMBER.get()));
        event.enqueueWork(() -> T.pass("lifecycle.enqueuework"));
    }

    private void serverStarted(ServerStartedEvent event) {
        T.check("bus.game", event.getServer() != null, "server");
        T.run("registries.lookup", () -> Registration.verify());
        T.run("features", () -> Features.onServerStarted(event.getServer().overworld()));
    }

    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        T.pass("event.login " + event.getEntity().getName().getString());
        T.run("network.send", () -> Network.greet(event.getEntity()));
        T.run("features.login", () -> Features.onLogin(event.getEntity()));
    }
}
