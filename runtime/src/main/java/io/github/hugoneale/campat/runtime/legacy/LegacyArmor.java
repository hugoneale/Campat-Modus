package io.github.hugoneale.campat.runtime.legacy;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/** Converts 1.20.1 armor materials (interface implementations) to 1.21.1's record holders and back. */
public final class LegacyArmor {
    private LegacyArmor() {}

    private static final Map<LegacyArmorMaterial, Holder<ArmorMaterial>> HOLDERS = new IdentityHashMap<>();
    private static final Map<ArmorMaterial, LegacyArmorMaterial> LEGACY = new IdentityHashMap<>();

    public static synchronized Holder<ArmorMaterial> holder(LegacyArmorMaterial m) {
        if (m == null) return null;
        if (m instanceof LegacyArmorMaterials v) return v.holder();
        return HOLDERS.computeIfAbsent(m, k -> {
            Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type t : ArmorItem.Type.values()) {
                int d;
                try {
                    d = k.getDefenseForType(t == ArmorItem.Type.BODY ? ArmorItem.Type.CHESTPLATE : t);
                } catch (RuntimeException e) {
                    d = 0;
                }
                defense.put(t, d);
            }
            SoundEvent sound = k.getEquipSound();
            Holder<SoundEvent> soundHolder = sound == null ? SoundEvents.ARMOR_EQUIP_GENERIC : BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
            String name = k.getName();
            ResourceLocation asset = name.indexOf(':') >= 0 ? ResourceLocation.parse(name) : ResourceLocation.withDefaultNamespace(name);
            ArmorMaterial material = new ArmorMaterial(defense, k.getEnchantmentValue(), soundHolder, k::getRepairIngredient,
                List.of(new ArmorMaterial.Layer(asset)), k.getToughness(), k.getKnockbackResistance());
            LEGACY.put(material, k);
            return Holder.direct(material);
        });
    }

    public static synchronized LegacyArmorMaterial legacy(Holder<ArmorMaterial> holder) {
        if (holder == null) return null;
        ArmorMaterial m = holder.value();
        LegacyArmorMaterial l = LEGACY.get(m);
        if (l != null) return l;
        for (LegacyArmorMaterials v : LegacyArmorMaterials.values()) if (v.holder().value() == m) return v;
        return new View(holder);
    }

    /** ArmorItem(material, type, properties): the 1.20.1 constructor also set the durability */
    public static Object[] armorItemArgs(LegacyArmorMaterial material, ArmorItem.Type type, Item.Properties properties) {
        try {
            properties.durability(material.getDurabilityForType(type));
        } catch (RuntimeException ignored) {
        }
        return new Object[]{holder(material), type, properties};
    }

    public static LegacyArmorMaterial getMaterial(ArmorItem item) {
        return legacy(item.getMaterial());
    }

    /** a 1.21.1 material seen through the 1.20.1 interface */
    private record View(Holder<ArmorMaterial> holder) implements LegacyArmorMaterial {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return type.getDurability(15);
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return holder.value().getDefense(type);
        }

        @Override
        public int getEnchantmentValue() {
            return holder.value().enchantmentValue();
        }

        @Override
        public SoundEvent getEquipSound() {
            return holder.value().equipSound().value();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return holder.value().repairIngredient().get();
        }

        @Override
        public String getName() {
            return holder.unwrapKey().map(k -> k.location().toString()).orElse("unknown");
        }

        @Override
        public float getToughness() {
            return holder.value().toughness();
        }

        @Override
        public float getKnockbackResistance() {
            return holder.value().knockbackResistance();
        }
    }
}
