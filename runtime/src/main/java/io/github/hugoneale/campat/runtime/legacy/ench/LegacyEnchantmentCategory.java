package io.github.hugoneale.campat.runtime.legacy.ench;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;

/** Minecraft 1.20.1's {@code EnchantmentCategory} (an extensible enum on Forge 47). */
public class LegacyEnchantmentCategory {
    private static final List<LegacyEnchantmentCategory> VALUES = new ArrayList<>();

    public static final LegacyEnchantmentCategory ARMOR = new LegacyEnchantmentCategory("ARMOR", i -> i instanceof ArmorItem);
    public static final LegacyEnchantmentCategory ARMOR_FEET = new LegacyEnchantmentCategory("ARMOR_FEET", i -> i instanceof ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.FEET);
    public static final LegacyEnchantmentCategory ARMOR_LEGS = new LegacyEnchantmentCategory("ARMOR_LEGS", i -> i instanceof ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.LEGS);
    public static final LegacyEnchantmentCategory ARMOR_CHEST = new LegacyEnchantmentCategory("ARMOR_CHEST", i -> i instanceof ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.CHEST);
    public static final LegacyEnchantmentCategory ARMOR_HEAD = new LegacyEnchantmentCategory("ARMOR_HEAD", i -> i instanceof ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.HEAD);
    public static final LegacyEnchantmentCategory WEAPON = new LegacyEnchantmentCategory("WEAPON", i -> i instanceof SwordItem);
    public static final LegacyEnchantmentCategory DIGGER = new LegacyEnchantmentCategory("DIGGER", i -> i instanceof DiggerItem);
    public static final LegacyEnchantmentCategory FISHING_ROD = new LegacyEnchantmentCategory("FISHING_ROD", i -> i instanceof FishingRodItem);
    public static final LegacyEnchantmentCategory TRIDENT = new LegacyEnchantmentCategory("TRIDENT", i -> i instanceof TridentItem);
    public static final LegacyEnchantmentCategory BREAKABLE = new LegacyEnchantmentCategory("BREAKABLE", i -> i.components().has(net.minecraft.core.component.DataComponents.MAX_DAMAGE));
    public static final LegacyEnchantmentCategory BOW = new LegacyEnchantmentCategory("BOW", i -> i instanceof BowItem);
    public static final LegacyEnchantmentCategory WEARABLE = new LegacyEnchantmentCategory("WEARABLE", i -> i instanceof ArmorItem || i instanceof BlockItem b && b.getBlock() instanceof net.minecraft.world.level.block.AbstractSkullBlock);
    public static final LegacyEnchantmentCategory CROSSBOW = new LegacyEnchantmentCategory("CROSSBOW", i -> i instanceof CrossbowItem);
    public static final LegacyEnchantmentCategory VANISHABLE = new LegacyEnchantmentCategory("VANISHABLE", i -> BREAKABLE.canEnchant(i));

    private final String name;
    private final Predicate<Item> test;

    protected LegacyEnchantmentCategory(String name, Predicate<Item> test) {
        this.name = name;
        this.test = test;
        VALUES.add(this);
    }

    /** Forge 47's {@code EnchantmentCategory.create(name, predicate)} */
    public static LegacyEnchantmentCategory create(String name, Predicate<Item> test) {
        return new LegacyEnchantmentCategory(name, test);
    }

    public boolean canEnchant(Item item) {
        return item != null && test.test(item);
    }

    public String name() {
        return name;
    }

    public int ordinal() {
        return VALUES.indexOf(this);
    }

    public static LegacyEnchantmentCategory[] values() {
        return VALUES.toArray(LegacyEnchantmentCategory[]::new);
    }

    @Override
    public String toString() {
        return name;
    }

    static Block unused() {
        return null;
    }
}
