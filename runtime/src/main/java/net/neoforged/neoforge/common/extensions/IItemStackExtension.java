/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

/*
 * Extension added to ItemStack that bounces to ItemSack sensitive Item methods. Typically this is just for convince.
 */
public interface IItemStackExtension {
    // Helpers for accessing Item data
    private ItemStack self() {
        return (ItemStack) (Object) this;
    }

    /**
     * ItemStack sensitive version of {@link Item#getCraftingRemainingItem()}.
     * Returns a full ItemStack instance of the result.
     *
     * @return The resulting ItemStack
     */
    ItemStack getCraftingRemainingItem() ;

    /**
     * ItemStack sensitive version of {@link Item#hasCraftingRemainingItem()}.
     *
     * @return True if this item has a crafting remaining item
     */
    boolean hasCraftingRemainingItem() ;

    /**
     * Returns the fuel burn time for this item stack. If it is zero, this item is not a fuel.
     * <p>
     * Will never return a negative value.
     * 
     * @return the fuel burn time for this item stack in a furnace.
     * @apiNote This method by default returns the {@code burn_time} specified in
     *          the {@code furnace_fuels.json} file.
     */
    int getBurnTime(@Nullable RecipeType<?> recipeType) ;

    InteractionResult onItemUseFirst(UseOnContext context) ;

    /**
     * Queries if an item can perform the given action.
     * See {@link ToolActions} for a description of each stock action
     * 
     * @param itemAbility The action being queried
     * @return True if the stack can perform the action
     */
    boolean canPerformAction(ToolAction itemAbility) ;

    /**
     * Called when the player is mining a block and the item in his hand changes.
     * Allows to not reset blockbreaking if only NBT or similar changes.
     *
     * @param newStack The new stack
     * @return True to reset block break progress
     */
    boolean shouldCauseBlockBreakReset(ItemStack newStack) ;

    /**
     * @see {@link IItemExtension#isPrimaryItemFor(ItemStack, Holder)}
     */
    default boolean isPrimaryItemFor(Holder<Enchantment> enchantment) {
        return ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).isPrimaryItemFor(self(), enchantment);
    }

    /**
     * @see {@link IItemExtension#supportsEnchantment(ItemStack, Holder)}
     */
    default boolean supportsEnchantment(Holder<Enchantment> enchantment) {
        return ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).supportsEnchantment(self(), enchantment);
    }

    /**
     * Gets the gameplay level of the target enchantment on this stack.
     * <p>
     * Use in place of {@link EnchantmentHelper#getTagEnchantmentLevel} for gameplay logic.
     * <p>
     * Use {@link EnchantmentHelper#getEnchantmentsForCrafting} and {@link EnchantmentHelper#setEnchantments} when modifying the item's enchantments.
     *
     * @param enchantment The enchantment being checked for.
     * @return The level of the enchantment, or 0 if not present.
     * @see {@link #getAllEnchantments} to get all gameplay enchantments
     */
    default int getEnchantmentLevel(Holder<Enchantment> enchantment) {
        int level = ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).getEnchantmentLevel(self(), enchantment);
        return level;
    }

    /**
     * Gets the gameplay level of all enchantments on this stack.
     * <p>
     * Use in place of {@link ItemStack#getTagEnchantments()} for gameplay logic.
     * <p>
     * Use {@link EnchantmentHelper#getEnchantmentsForCrafting} and {@link EnchantmentHelper#setEnchantments} when modifying the item's enchantments.
     *
     * @return Map of all enchantments on the stack, or an empty map if no enchantments are present.
     * @see {@link #getEnchantmentLevel} to get the level of a single enchantment for gameplay purposes
     */
    default ItemEnchantments getAllEnchantments(RegistryLookup<Enchantment> lookup) {
        var enchantments = ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).getAllEnchantments(self(), lookup);
        return enchantments;
    }

    /**
     * ItemStack sensitive version of {@link Item#getEnchantmentValue()}.
     *
     * @return the enchantment value of this ItemStack
     */
    int getEnchantmentValue() ;

    /**
     * Override this to set a non-default armor slot for an ItemStack, but <em>do
     * not use this to get the armor slot of said stack; for that, use
     * {@link LivingEntity#getEquipmentSlotForItem(ItemStack)}.</em>
     *
     * @return the armor slot of the ItemStack, or {@code null} to let the default
     *         vanilla logic as per {@code LivingEntity.getSlotForItemStack(stack)}
     *         decide
     */
    @Nullable
    EquipmentSlot getEquipmentSlot() ;

    /**
     * Can this Item disable a shield
     *
     * @param shield   The shield in question
     * @param entity   The LivingEntity holding the shield
     * @param attacker The LivingEntity holding the ItemStack
     * @return True if this ItemStack can disable the shield in question.
     */
    boolean canDisableShield(ItemStack shield, LivingEntity entity, LivingEntity attacker) ;

    /**
     * Called when a entity tries to play the 'swing' animation.
     *
     * @param entity The entity swinging the item.
     * @return True to cancel any further processing by {@link LivingEntity}
     * @deprecated To be replaced with hand sensitive version in 21.2
     * @see #onEntitySwing(LivingEntity, InteractionHand)
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "21.1")
    boolean onEntitySwing(LivingEntity entity) ;

    /**
     * Called when a entity tries to play the 'swing' animation.
     *
     * @param entity The entity swinging the item.
     * @param hand   The hand the item is held in.
     * @return True to cancel any further processing by {@link LivingEntity}
     */
    default boolean onEntitySwing(LivingEntity entity, InteractionHand hand) {
        return ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).onEntitySwing(self(), entity, hand);
    }

    /**
     * Called when an entity stops using an item item for any reason.
     *
     * @param entity The entity using the item, typically a player
     * @param count  The amount of time in tick the item has been used for continuously
     */
    void onStopUsing(LivingEntity entity, int count) ;

    /**
     * Retrieves the normal 'lifespan' of this item when it is dropped on the ground
     * as a EntityItem. This is in ticks, standard result is 6000, or 5 mins.
     *
     * @param level The level the entity is in
     * @return The normal lifespan in ticks.
     */
    int getEntityLifespan(Level level) ;

    /**
     * Called by the default implemetation of EntityItem's onUpdate method, allowing
     * for cleaner control over the update of the item without having to write a
     * subclass.
     *
     * @param entity The entity Item
     * @return Return true to skip any further update code.
     */
    boolean onEntityItemUpdate(ItemEntity entity) ;

    /**
     * Determines the amount of durability the mending enchantment
     * will repair, on average, per point of experience.
     */
    default float getXpRepairRatio() {
        return ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).getXpRepairRatio(self());
    }

    /**
     * Called every tick when this item is equipped {@linkplain Mob#isBodyArmorItem(ItemStack) as an armor item} by a horse {@linkplain Mob#canWearBodyArmor()} that can wear armor}.
     * <p>
     * In vanilla, only {@linkplain Horse horses} and {@linkplain Wolf wolves} can wear armor, and they can only equip items that extend {@link AnimalArmorItem}.
     *
     * @param level The level the horse is in
     * @param horse The horse wearing this item
     */
    default void onAnimalArmorTick(Level level, Mob horse) {
        ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).onAnimalArmorTick(self(), level, horse);
    }

    /**
     * Determines if the specific ItemStack can be placed in the specified armor
     * slot, for the entity.
     *
     * @param armorType Armor slot to be verified.
     * @param entity    The entity trying to equip the armor
     * @return True if the given ItemStack can be inserted in the slot
     */
    default boolean canEquip(EquipmentSlot armorType, LivingEntity entity) {
        return ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).canEquip(self(), armorType, entity);
    }

    /**
     * Allow or forbid the specific book/item combination as an anvil enchant
     *
     * @param book The book
     * @return if the enchantment is allowed
     */
    boolean isBookEnchantable(ItemStack book) ;

    /**
     * Called when a player drops the item into the world, returning false from this
     * will prevent the item from being removed from the players inventory and
     * spawning in the world
     *
     * @param player The player that dropped the item
     */
    boolean onDroppedByPlayer(Player player) ;

    /**
     * Allow the item one last chance to modify its name used for the tool highlight
     * useful for adding something extra that can't be removed by a user in the
     * displayed name, such as a mode of operation.
     *
     * @param displayName the name that will be displayed unless it is changed in
     *                    this method.
     */
    Component getHighlightTip(Component displayName) ;

    /**
     *
     * Should this item, when held, allow sneak-clicks to pass through to the underlying block?
     *
     * @param level  The level
     * @param pos    Block position in level
     * @param player The Player that is wielding the item
     */
    boolean doesSneakBypassUse(net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) ;

    /**
     * Determines if a item is reparable, used by Repair recipes and Grindstone.
     *
     * @return True if reparable
     */
    default boolean isRepairable() {
        return ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).isRepairable(self());
    }

    /**
     * Called by Piglins when checking to see if they will give an item or something in exchange for this item.
     *
     * @return True if this item can be used as "currency" by piglins
     */
    boolean isPiglinCurrency() ;

    /**
     * Called by Piglins to check if a given item prevents hostility on sight. If this is true the Piglins will be neutral to the entity wearing this item, and will not
     * attack on sight. Note: This does not prevent Piglins from becoming hostile due to other actions, nor does it make Piglins that are already hostile stop being so.
     *
     * @param wearer The entity wearing this ItemStack
     *
     * @return True if piglins are neutral to players wearing this item in an armor slot
     */
    boolean makesPiglinsNeutral(LivingEntity wearer) ;

    /**
     * Whether this Item can be used to hide player head for enderman.
     *
     * @param player         The player watching the enderman
     * @param endermanEntity The enderman that the player look
     * @return true if this Item can be used.
     */
    boolean isEnderMask(Player player, EnderMan endermanEntity) ;

    /**
     * Used to determine if the player can use Elytra flight.
     * This is called Client and Server side.
     *
     * @param entity The entity trying to fly.
     * @return True if the entity can use Elytra flight.
     */
    boolean canElytraFly(LivingEntity entity) ;

    /**
     * Used to determine if the player can continue Elytra flight,
     * this is called each tick, and can be used to apply ItemStack damage,
     * consume Energy, or what have you.
     * For example the Vanilla implementation of this, applies damage to the
     * ItemStack every 20 ticks.
     *
     * @param entity      The entity currently in Elytra flight.
     * @param flightTicks The number of ticks the entity has been Elytra flying for.
     * @return True if the entity should continue Elytra flight or False to stop.
     */
    boolean elytraFlightTick(LivingEntity entity, int flightTicks) ;

    /**
     * Called by the powdered snow block to check if a living entity wearing this can walk on the snow, granting the same behavior as leather boots.
     * Only affects items worn in the boots slot.
     *
     * @param wearer The entity wearing this ItemStack
     *
     * @return True if the entity can walk on powdered snow
     */
    boolean canWalkOnPowderedSnow(LivingEntity wearer) ;

    /**
     * Get a bounding box ({@link AABB}) of a sweep attack.
     *
     * @param player the performing the attack the attack.
     * @param target the entity targeted by the attack.
     * @return the bounding box.
     */

    AABB getSweepHitBox(Player player, Entity target) ;

    /**
     * Called when an item entity for this stack is destroyed. Note: The {@link ItemStack} can be retrieved from the item entity.
     *
     * @param itemEntity   The item entity that was destroyed.
     * @param damageSource Damage source that caused the item entity to "die".
     */
    void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) ;

    /**
     * Get the food properties for this item.
     * This is a bouncer for easier use of {@link IItemExtension#getFoodProperties(ItemStack, LivingEntity)}
     *
     * The @Nullable annotation was only added, due to the default method, also being @Nullable.
     * Use this with a grain of salt, as if you return null here and true at {@link Item#isEdible()}, NPEs will occur!
     *
     * @param entity The entity which wants to eat the food. Be aware that this can be null!
     * @return The current FoodProperties for the item.
     */
    @Nullable // read javadoc to find a potential problem
    default FoodProperties getFoodProperties(@Nullable LivingEntity entity) {
        return ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).getFoodProperties(self(), entity);
    }

    /**
     * Whether this stack should be excluded (if possible) when selecting the target hotbar slot of a "pick" action.
     * By default, this returns true for enchanted stacks.
     *
     * @see Inventory#getSuitableHotbarSlot()
     * @param player        the player performing the picking
     * @param inventorySlot the inventory slot of the item being up for replacement
     * @return true to leave this stack in the hotbar if possible
     */
    boolean isNotReplaceableByPickAction(Player player, int inventorySlot) ;

    /**
     * {@return true if the given ItemStack can be put into a grindstone to be repaired and/or stripped of its enchantments}
     */
    boolean canGrindstoneRepair() ;

    @Nullable
    default <T, C extends @Nullable Object> T getCapability(ItemCapability<T, C> capability, C context) {
        return capability.getCapability(self(), context);
    }

    @Nullable
    default <T> T getCapability(ItemCapability<T, @Nullable Void> capability) {
        return capability.getCapability(self(), null);
    }

    /**
     * Computes the gameplay attribute modifiers for this item stack. Used in place of direct access to {@link DataComponents.ATTRIBUTE_MODIFIERS}
     * or {@link Item#getDefaultAttributeModifiers(ItemStack)} when querying attributes for gameplay purposes.
     * <p>
     * This method first computes the default modifiers, using {@link DataComponents.ATTRIBUTE_MODIFIERS} if present, otherwise
     * falling back to {@link Item#getDefaultAttributeModifiers(ItemStack)}.
     * <p>
     * The {@link ItemAttributeModifiersEvent} is then fired to allow external adjustments.
     */
    default ItemAttributeModifiers getAttributeModifiers() {
        ItemAttributeModifiers defaultModifiers = self().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        if (defaultModifiers.modifiers().isEmpty()) {
            defaultModifiers = ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).getDefaultAttributeModifiers(self());
        }

        return defaultModifiers;
    }

    /**
     * Determines whether the item held by this stack can be safely stored inside another container item, optionally
     * taking this stack's data into account.
     *
     * @return whether the item held by this stack can fit inside a container item
     */
    default boolean canFitInsideContainerItems() {
        return ((net.neoforged.neoforge.common.extensions.IItemExtension) (Object) self().getItem()).canFitInsideContainerItems(self());
    }
}
