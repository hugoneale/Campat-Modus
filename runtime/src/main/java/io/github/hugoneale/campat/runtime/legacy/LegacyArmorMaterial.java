package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Minecraft 1.20.1's {@code ArmorMaterial} interface, which mods implemented (usually with an enum).
 * In 1.21.1 it is a registered record; {@link LegacyArmor#holder} builds one from this.
 */
public interface LegacyArmorMaterial {
    int getDurabilityForType(ArmorItem.Type type);

    int getDefenseForType(ArmorItem.Type type);

    int getEnchantmentValue();

    SoundEvent getEquipSound();

    Ingredient getRepairIngredient();

    String getName();

    float getToughness();

    float getKnockbackResistance();
}
