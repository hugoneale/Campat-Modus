package campattest;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CampatTest.MODID, dist = Dist.CLIENT)
public class CampatTestClient {
    public CampatTestClient(IEventBus modBus, ModContainer container) {
        T.pass("construct.client");
        T.run("client.configscreen", () -> {
            container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (c, parent) -> parent);
            T.pass("client.configscreen");
        });
        modBus.addListener(FMLClientSetupEvent.class, e -> T.pass("lifecycle.clientsetup"));
    }
}
