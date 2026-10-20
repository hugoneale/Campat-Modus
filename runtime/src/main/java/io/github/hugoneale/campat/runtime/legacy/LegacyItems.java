package io.github.hugoneale.campat.runtime.legacy;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Item classes 1.21.1 replaced by item properties and components */
public final class LegacyItems {
    private LegacyItems() {}

    /** {@code BowlFoodItem}: eating leaves the bowl */
    public static class BowlFoodItem extends Item {
        public BowlFoodItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack rest = super.finishUsingItem(stack, level, entity);
            return entity instanceof Player p && p.getAbilities().instabuild ? rest : rest.isEmpty() ? new ItemStack(Items.BOWL) : rest;
        }
    }

    /** {@code SimpleFoiledItem} */
    public static class SimpleFoiledItem extends Item {
        public SimpleFoiledItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }
    }

    /** {@code EnchantedGoldenAppleItem} */
    public static class EnchantedGoldenAppleItem extends SimpleFoiledItem {
        public EnchantedGoldenAppleItem(Item.Properties properties) {
            super(properties);
        }
    }

    /** {@code DyeableArmorItem} */
    public static class DyeableArmorItem extends ArmorItem implements LegacyDyeable {
        public DyeableArmorItem(LegacyArmorMaterial material, ArmorItem.Type type, Item.Properties properties) {
            super(LegacyArmor.holder(material), type, durability(properties, material, type));
        }

        private static Item.Properties durability(Item.Properties properties, LegacyArmorMaterial material, ArmorItem.Type type) {
            try {
                properties.durability(material.getDurabilityForType(type));
            } catch (RuntimeException ignored) {
            }
            return properties;
        }
    }
}
