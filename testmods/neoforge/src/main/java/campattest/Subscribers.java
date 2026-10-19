package campattest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** NeoForge 21.1 picks the bus per listener: one class may mix mod bus and game bus events. */
@EventBusSubscriber(modid = CampatTest.MODID)
public final class Subscribers {
    @SubscribeEvent
    public static void onSetup(FMLCommonSetupEvent event) {
        T.pass("subscriber.modbus");
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        T.check("subscriber.gamebus", event.getDispatcher() != null, "dispatcher");
    }
}
