package io.github.hugoneale.campat.runtime.legacy.forge;

import io.github.hugoneale.campat.runtime.legacy.LegacyHolders;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;

/** Forge 47's {@code EntityAttributeModificationEvent} took the attribute itself, Forge 52 takes its holder */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class LegacyAttributeEvents {
    private LegacyAttributeEvents() {}

    public static void add(EntityAttributeModificationEvent event, EntityType type, Attribute attribute, double value) {
        event.add(type, (Holder<Attribute>) LegacyHolders.wrap(attribute), value);
    }

    public static void add(EntityAttributeModificationEvent event, EntityType type, Attribute attribute) {
        event.add(type, (Holder<Attribute>) LegacyHolders.wrap(attribute));
    }

    public static boolean has(EntityAttributeModificationEvent event, EntityType type, Attribute attribute) {
        return event.has(type, (Holder<Attribute>) LegacyHolders.wrap(attribute));
    }

    /** MobEffect.addAttributeModifier(attribute, uuid, amount, operation): 1.21.1 names modifiers by id */
    public static net.minecraft.world.effect.MobEffect addAttributeModifier(net.minecraft.world.effect.MobEffect effect, Attribute attribute, String uuid, double amount,
                                                                             net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
        return effect.addAttributeModifier((Holder<Attribute>) LegacyHolders.wrap(attribute),
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("campat", uuid.toLowerCase(java.util.Locale.ROOT)), amount, operation);
    }
}
