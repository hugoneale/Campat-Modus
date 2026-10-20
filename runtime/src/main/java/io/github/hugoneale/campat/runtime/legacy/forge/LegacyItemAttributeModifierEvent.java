package io.github.hugoneale.campat.runtime.legacy.forge;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import io.github.hugoneale.campat.runtime.legacy.LegacyHolders;
import java.util.Collection;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

/** Forge 47's {@code ItemAttributeModifierEvent}, fired whenever an item's modifiers for a slot are collected. */
public class LegacyItemAttributeModifierEvent extends Event {
    private final ItemStack stack;
    private final EquipmentSlot slotType;
    private final Multimap<Attribute, AttributeModifier> originalModifiers;
    private Multimap<Attribute, AttributeModifier> modifiers;

    public LegacyItemAttributeModifierEvent() {
        this(ItemStack.EMPTY, EquipmentSlot.MAINHAND, HashMultimap.create());
    }

    public LegacyItemAttributeModifierEvent(ItemStack stack, EquipmentSlot slotType, Multimap<Attribute, AttributeModifier> modifiers) {
        this.stack = stack;
        this.slotType = slotType;
        this.originalModifiers = Multimaps.unmodifiableMultimap(modifiers);
        this.modifiers = modifiers;
    }

    public Multimap<Attribute, AttributeModifier> getModifiers() {
        return Multimaps.unmodifiableMultimap(modifiers);
    }

    public Multimap<Attribute, AttributeModifier> getOriginalModifiers() {
        return originalModifiers;
    }

    private Multimap<Attribute, AttributeModifier> mutable() {
        if (modifiers == originalModifiers) modifiers = HashMultimap.create(originalModifiers);
        return modifiers;
    }

    public boolean addModifier(Attribute attribute, AttributeModifier modifier) {
        return mutable().put(attribute, modifier);
    }

    public boolean removeModifier(Attribute attribute, AttributeModifier modifier) {
        return mutable().remove(attribute, modifier);
    }

    public Collection<AttributeModifier> removeAttribute(Attribute attribute) {
        return mutable().removeAll(attribute);
    }

    public void clearModifiers() {
        mutable().clear();
    }

    public EquipmentSlot getSlotType() {
        return slotType;
    }

    public ItemStack getItemStack() {
        return stack;
    }

    /** called around ItemStack.forEachModifier(slot, consumer) */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BiConsumer<Holder<Attribute>, AttributeModifier> wrap(ItemStack stack, EquipmentSlot slot, Runnable collect, java.util.function.Consumer<BiConsumer<Holder<Attribute>, AttributeModifier>> run,
                                                                   BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        run.accept((h, m) -> map.put(h.value(), m));
        LegacyItemAttributeModifierEvent e = new LegacyItemAttributeModifierEvent(stack, slot, map);
        MinecraftForge.EVENT_BUS.post(e);
        e.modifiers.forEach((a, m) -> consumer.accept(LegacyHolders.wrap(a), m));
        return null;
    }
}
