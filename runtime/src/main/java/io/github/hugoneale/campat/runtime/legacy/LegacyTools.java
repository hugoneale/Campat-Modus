package io.github.hugoneale.campat.runtime.legacy;

import java.lang.reflect.Method;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;

/**
 * 1.20.1 tool constructors took attack damage and speed; 1.21.1 takes them as attribute modifiers on
 * the item properties. 1.20.1 tiers had a mining level instead of a tag of blocks they cannot mine.
 */
public final class LegacyTools {
    private LegacyTools() {}

    /** SwordItem(tier, damage, speed, properties) */
    public static Object[] sword(Tier tier, int damage, float speed, Item.Properties p) {
        return new Object[]{tier, p.attributes(SwordItem.createAttributes(tier, damage, speed))};
    }

    /** PickaxeItem / HoeItem(tier, damage, speed, properties) */
    public static Object[] diggerInt(Tier tier, int damage, float speed, Item.Properties p) {
        return new Object[]{tier, p.attributes(DiggerItem.createAttributes(tier, damage, speed))};
    }

    /** AxeItem / ShovelItem(tier, damage, speed, properties) */
    public static Object[] diggerFloat(Tier tier, float damage, float speed, Item.Properties p) {
        return new Object[]{tier, p.attributes(DiggerItem.createAttributes(tier, damage, speed))};
    }

    /** DiggerItem(damage, speed, tier, blocks, properties) */
    public static Object[] digger(float damage, float speed, Tier tier, TagKey<Block> blocks, Item.Properties p) {
        return new Object[]{tier, blocks, p.attributes(DiggerItem.createAttributes(tier, damage, speed))};
    }

    /** Tier.getIncorrectBlocksForDrops() for tiers that only know their 1.20.1 mining level */
    public static TagKey<Block> incorrectBlocksForDrops(Tier tier) {
        int level = 0;
        try {
            Method m = tier.getClass().getMethod("getLevel");
            level = (Integer) m.invoke(tier);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }
        return switch (level) {
            case 0 -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            case 1 -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case 2 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case 3 -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        };
    }
}
