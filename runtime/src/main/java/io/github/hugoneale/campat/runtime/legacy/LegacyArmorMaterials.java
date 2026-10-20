package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.crafting.Ingredient;

/** Minecraft 1.20.1's {@code ArmorMaterials} enum, backed by the 1.21.1 registered materials. */
public enum LegacyArmorMaterials implements LegacyArmorMaterial {
    LEATHER("leather", 5),
    CHAIN("chainmail", 15),
    IRON("iron", 15),
    GOLD("gold", 7),
    DIAMOND("diamond", 33),
    TURTLE("turtle", 25),
    NETHERITE("netherite", 37);

    private final String name;
    private final int durabilityMultiplier;

    LegacyArmorMaterials(String name, int durabilityMultiplier) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
    }

    public Holder<ArmorMaterial> holder() {
        return switch (this) {
            case LEATHER -> ArmorMaterials.LEATHER;
            case CHAIN -> ArmorMaterials.CHAIN;
            case IRON -> ArmorMaterials.IRON;
            case GOLD -> ArmorMaterials.GOLD;
            case DIAMOND -> ArmorMaterials.DIAMOND;
            case TURTLE -> ArmorMaterials.TURTLE;
            case NETHERITE -> ArmorMaterials.NETHERITE;
        };
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return type.getDurability(durabilityMultiplier);
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return holder().value().getDefense(type);
    }

    @Override
    public int getEnchantmentValue() {
        return holder().value().enchantmentValue();
    }

    @Override
    public SoundEvent getEquipSound() {
        return holder().value().equipSound().value();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return holder().value().repairIngredient().get();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return holder().value().toughness();
    }

    @Override
    public float getKnockbackResistance() {
        return holder().value().knockbackResistance();
    }
}
