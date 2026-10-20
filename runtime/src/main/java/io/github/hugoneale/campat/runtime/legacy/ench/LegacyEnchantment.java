package io.github.hugoneale.campat.runtime.legacy.ench;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Base class for 1.20.1 enchantments: 1.21.1 enchantments are data driven records that cannot be
 * subclassed, so mod enchantment classes extend this instead. They load and keep their behaviour
 * methods, but are not part of 1.21.1's enchantment system (they never appear on items).
 */
public abstract class LegacyEnchantment {
    /** Forge 47 / 1.20.1 {@code Enchantment.Rarity} */
    public enum Rarity {
        COMMON(10),
        UNCOMMON(5),
        RARE(2),
        VERY_RARE(1);

        private final int weight;

        Rarity(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }
    }

    private final Rarity rarity;
    public final LegacyEnchantmentCategory category;
    protected final EquipmentSlot[] slots;
    protected String descriptionId;
    private Enchantment vanilla;

    protected LegacyEnchantment(Rarity rarity, LegacyEnchantmentCategory category, EquipmentSlot[] slots) {
        this.rarity = rarity;
        this.category = category;
        this.slots = slots;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return 1;
    }

    public int getMinCost(int level) {
        return 1 + level * 10;
    }

    public int getMaxCost(int level) {
        return getMinCost(level) + 5;
    }

    public int getDamageProtection(int level, DamageSource source) {
        return 0;
    }

    public float getDamageBonus(int level, LegacyMobType type) {
        return 0f;
    }

    public final boolean isCompatibleWith(Enchantment other) {
        return checkCompatibility(other);
    }

    protected boolean checkCompatibility(Enchantment other) {
        return true;
    }

    protected String getOrCreateDescriptionId() {
        if (descriptionId == null) descriptionId = "enchantment.legacy." + Integer.toHexString(System.identityHashCode(this));
        return descriptionId;
    }

    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    public Component getFullname(int level) {
        MutableComponent c = Component.translatable(getDescriptionId());
        if (level != 1 || getMaxLevel() != 1) c.append(" ").append(Component.translatable("enchantment.level." + level));
        return c;
    }

    public boolean canEnchant(ItemStack stack) {
        return category != null && category.canEnchant(stack.getItem());
    }

    public void doPostAttack(LivingEntity user, Entity target, int level) {}

    public void doPostHurt(LivingEntity user, Entity attacker, int level) {}

    public boolean isTreasureOnly() {
        return false;
    }

    public boolean isCurse() {
        return false;
    }

    public boolean isTradeable() {
        return true;
    }

    public boolean isDiscoverable() {
        return true;
    }

    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    public boolean isAllowedOnBooks() {
        return true;
    }

    /** an unregistered 1.21.1 enchantment describing this one, for vanilla APIs */
    public synchronized Enchantment asVanilla() {
        if (vanilla == null) {
            List<net.minecraft.core.Holder<Item>> items = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (category != null && category.canEnchant(item)) items.add(BuiltInRegistries.ITEM.wrapAsHolder(item));
            }
            vanilla = Enchantment.enchantment(Enchantment.definition(HolderSet.direct(items), rarity.getWeight(), Math.max(1, getMaxLevel()),
                    Enchantment.constantCost(getMinCost(1)), Enchantment.constantCost(getMaxCost(1)), 1, EquipmentSlotGroup.ANY))
                .build(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("campat", "legacy_enchantment"));
        }
        return vanilla;
    }
}
