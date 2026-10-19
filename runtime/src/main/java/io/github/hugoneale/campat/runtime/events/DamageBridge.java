package io.github.hugoneale.campat.runtime.events;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * NeoForge 1.21's damage events from Forge's: incoming damage from {@link LivingHurtEvent} (before
 * armor), {@code LivingDamageEvent.Pre} from Forge's {@link LivingDamageEvent} (after reductions)
 * and {@code Post} once every listener of that event ran. One {@link DamageContainer} per hit is
 * shared, like NeoForge does.
 */
final class DamageBridge {
    private DamageBridge() {}

    private static final Map<LivingEntity, DamageContainer> CURRENT = new WeakHashMap<>();

    static void register() {
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, false, LivingHurtEvent.class, e -> {
            DamageContainer container = new DamageContainer(e.getSource(), e.getAmount());
            synchronized (CURRENT) {
                CURRENT.put(e.getEntity(), container);
            }
            LivingIncomingDamageEvent neo = new LivingIncomingDamageEvent(e.getEntity(), container);
            bus.post(neo);
            if (neo.isCanceled()) e.setCanceled(true);
            else e.setAmount(container.getNewDamage());
        });
        bus.addListener(EventPriority.HIGHEST, false, LivingDamageEvent.class, e -> {
            DamageContainer container = container(e.getEntity(), e);
            container.setNewDamage(e.getAmount());
            bus.post(new net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre(e.getEntity(), container));
            e.setAmount(container.getNewDamage());
        });
        bus.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, e -> {
            DamageContainer container = container(e.getEntity(), e);
            container.setNewDamage(e.getAmount());
            synchronized (CURRENT) {
                CURRENT.remove(e.getEntity());
            }
            bus.post(new net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post(e.getEntity(), container));
        });
    }

    private static DamageContainer container(LivingEntity entity, LivingDamageEvent e) {
        synchronized (CURRENT) {
            DamageContainer c = CURRENT.get(entity);
            if (c == null || c.getSource() != e.getSource()) {
                c = new DamageContainer(e.getSource(), e.getAmount());
                CURRENT.put(entity, c);
            }
            return c;
        }
    }
}
