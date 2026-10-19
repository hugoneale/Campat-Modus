package campattest;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class Ticks {
    private static int ticks;

    static void setup() {
        NeoForge.EVENT_BUS.addListener(Ticks::onTick);
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (++ticks == 20) T.pass("event.servertick");
    }
}
