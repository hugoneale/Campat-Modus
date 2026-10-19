package io.github.hugoneale.campat.runtime.registry;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Objects NeoForge registers that Forge does not have, registered under the neoforge namespace. */
public final class RuntimeRegistrations {
    private RuntimeRegistrations() {}

    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.Keys.ATTRIBUTES, "neoforge");

    static {
        ATTRIBUTES.register("creative_flight", () -> new RangedAttribute("neoforge.creative_flight", 0, 0, 1).setSyncable(true));
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
    }
}
