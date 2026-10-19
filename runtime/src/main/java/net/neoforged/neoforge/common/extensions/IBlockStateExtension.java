/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.ToolAction;
import net.neoforged.neoforge.common.enums.BubbleColumnDirection;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

public interface IBlockStateExtension {
    private BlockState self() {
        return (BlockState) (Object) this;
    }

    /**
     * Gets the slipperiness at the given location at the given state. Normally
     * between 0 and 1.
     * <p>
     * Note that entities may reduce slipperiness by a certain factor of their own;
     * for {@link LivingEntity}, this is {@code .91}.
     * {@link ItemEntity} uses {@code .98}, and
     * {@link FishingHook} uses {@code .92}.
     *
     * @param level  the level
     * @param pos    the position in the level
     * @param entity the entity in question
     * @return the factor by which the entity's motion should be multiplied
     */
    float getFriction(LevelReader level, BlockPos pos, @Nullable Entity entity) ;

    /**
     * Whether this block state has dynamic light emission which is not solely based on its underlying block or its
     * state properties and instead uses the {@link BlockPos}, the {@link AuxiliaryLightManager} or another external
     * data source to determine its light value in {@link #getLightEmission(BlockGetter, BlockPos)}
     *
     * @return true if this block state cannot determine its light emission solely based on its properties, false otherwise
     */
    default boolean hasDynamicLightEmission() {
        return ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).hasDynamicLightEmission(self());
    }

    /**
     * Get a light value for this block, taking into account the given state and coordinates, normal ranges are between 0 and 15
     */
    int getLightEmission(BlockGetter level, BlockPos pos) ;

    /**
     * Called when lava is updating, checks if a block face can catch fire from lava.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @param face  The face that the fire is coming from
     * @return True if the face can catch fire from lava, false otherwise.
     */
    default boolean ignitedByLava(BlockGetter level, BlockPos pos, Direction face) {
        return ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).ignitedByLava(self(), level, pos, face);
    }

    /**
     * Checks if a player or entity can use this block to 'climb' like a ladder.
     *
     * @param level  The current level
     * @param pos    Block position in level
     * @param entity The entity trying to use the ladder, CAN be null.
     * @return True if the block should act like a ladder
     */
    boolean isLadder(LevelReader level, BlockPos pos, LivingEntity entity) ;

    /**
     * Determines if the player can harvest this block, obtaining it's drops when the block is destroyed.
     *
     * @param level  The current level
     * @param pos    The block's current position
     * @param player The player damaging the block
     * @return True to spawn the drops
     */
    boolean canHarvestBlock(BlockGetter level, BlockPos pos, Player player) ;

    /**
     * Called when a player removes a block. This is responsible for
     * actually destroying the block, and the block is intact at time of call.
     * This is called regardless of whether the player can harvest the block or
     * not.
     *
     * Return true if the block is actually destroyed.
     *
     * This function is called on both the logical client and logical server.
     *
     * @param level       The current level
     * @param player      The player damaging the block, may be null
     * @param pos         Block position in level
     * @param willHarvest The result of {@link #canHarvestBlock}, if called on the server by a non-creative player, otherwise always false.
     * @param fluid       The current fluid and block state for the position in the level.
     * @return True if the block is actually destroyed.
     */
    boolean onDestroyedByPlayer(Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) ;

    /**
     * Called when a block is removed by {@link PushReaction#DESTROY}. This is responsible for
     * actually destroying the block, and the block is intact at time of call.
     * <p>
     * Will only be called if {@link BlockState#getPistonPushReaction} returns {@link PushReaction#DESTROY}.
     * <p>
     * Note: When used in multiplayer, this is called on both client and
     * server sides!
     *
     * @param level         The current level
     * @param pos           Block position in level
     * @param pushDirection The direction of block movement
     * @param fluid         The current fluid state at current position
     */
    default void onDestroyedByPushReaction(Level level, BlockPos pos, Direction pushDirection, FluidState fluid) {
        ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).onDestroyedByPushReaction(self(), level, pos, pushDirection, fluid);
    }

    /**
     * Determines if this block is classified as a bed, replacing <code>instanceof BedBlock</code> checks.
     * <p>
     * If true, players may sleep in it, though the block must manually put the player to sleep
     * by calling {@link Player#startSleepInBed} from {@link BlockBehaviour#useWithoutItem} or similar.
     *
     * @param level   The current level
     * @param pos     Block position in level
     * @param sleeper The sleeping entity
     * @return True to treat this as a bed
     */
    boolean isBed(BlockGetter level, BlockPos pos, LivingEntity sleeper) ;


    /**
     * Called when a user either starts or stops sleeping in the bed.
     *
     * @param level    The current level
     * @param pos      Block position in level
     * @param sleeper  The sleeper or camera entity, null in some cases.
     * @param occupied True if we are occupying the bed, or false if they are stopping use of the bed
     */
    void setBedOccupied(Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) ;

    /**
     * Returns the direction of the block. Same values that
     * are returned by BlockDirectional
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return Bed direction
     */
    Direction getBedDirection(LevelReader level, BlockPos pos) ;

    /**
     * Location sensitive version of getExplosionResistance
     *
     * @param level     The current level
     * @param pos       Block position in level
     * @param explosion The explosion
     * @return The amount of the explosion absorbed.
     */
    float getExplosionResistance(BlockGetter level, BlockPos pos, Explosion explosion) ;

    /**
     *
     * Called when A user uses the creative pick block button on this block
     *
     * @param target The full target the player is looking at
     * @return A ItemStack to add to the player's inventory, empty itemstack if nothing should be added.
     */
    ItemStack getCloneItemStack(HitResult target, LevelReader level, BlockPos pos, Player player) ;

    /**
     * Allows a block to override the standard EntityLivingBase.updateFallState
     * particles, this is a server side method that spawns particles with
     * WorldServer.spawnParticle.
     *
     * @param level             The current server level
     * @param pos               The position of the block.
     * @param state2            The state at the specific world/pos
     * @param entity            The entity that hit landed on the block
     * @param numberOfParticles That vanilla world have spawned
     * @return True to prevent vanilla landing particles from spawning
     */
    boolean addLandingEffects(ServerLevel level, BlockPos pos, BlockState state2, LivingEntity entity, int numberOfParticles) ;

    /**
     * Allows a block to override the standard vanilla running particles.
     * This is called from {@code Entity#spawnSprintParticle()} and is called both,
     * Client and server side, it's up to the implementor to client check / server check.
     * By default vanilla spawns particles only on the client and the server methods no-op.
     *
     * @param level  The level.
     * @param pos    The position at the entities feet.
     * @param entity The entity running on the block.
     * @return True to prevent vanilla running particles from spawning.
     */
    boolean addRunningEffects(Level level, BlockPos pos, Entity entity) ;

    /**
     * Determines if this block either force allow or force disallow a plant from being placed on it. (Or pass and let the plant's decision win)
     * This will be called in plant's canSurvive method and/or mayPlace method.
     *
     * @param level        The current level
     * @param soilPosition The current position of the block that will sustain the plant
     * @param facing       The direction relative to the given position the plant wants to be, typically its UP
     * @param plant        The plant that is checking survivability
     * @return {@link TriState#TRUE} to allow the plant to be planted/stay. {@link TriState#FALSE} to disallow the plant from placing. {@link TriState#DEFAULT} to allow the plant to make the decision to stay or not.
     */
    default TriState canSustainPlant(BlockGetter level, BlockPos soilPosition, Direction facing, BlockState plant) {
        return ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).canSustainPlant(self(), level, soilPosition, facing, plant);
    }

    /**
     * Called when a tree grows on top of this block and tries to set it to dirt by the trunk placer.
     * An override that returns true is responsible for using the place function to
     * set blocks in the world properly during generation. A modded grass block might override this method
     * to ensure it turns into the corresponding modded dirt instead of regular dirt when a tree grows on it.
     * For modded grass blocks, returning true from this method is NOT a substitute for adding your block
     * to the #minecraft:dirt tag, rather for changing the behaviour to something other than setting to dirt.
     *
     * NOTE: This happens DURING world generation, the generation may be incomplete when this is called.
     * Use the placeFunction when modifying the level.
     *
     * @param level         The current level
     * @param placeFunction Function to set blocks in the level for the tree, use this instead of the level directly
     * @param randomSource  The random source
     * @param pos           Position of the block to be set to dirt
     * @param config        Configuration of the trunk placer. Consider azalea trees, which should place rooted dirt instead of regular dirt.
     * @return True to ignore vanilla behaviour
     */
    boolean onTreeGrow(LevelReader level, BiConsumer<BlockPos, BlockState> placeFunction, RandomSource randomSource, BlockPos pos, TreeConfiguration config) ;

    /**
     * Checks if this soil is fertile, typically this means that growth rates
     * of plants on this soil will be slightly sped up.
     * Only vanilla case is tilledField when it is within range of water.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return True if the soil should be considered fertile.
     */
    boolean isFertile(BlockGetter level, BlockPos pos) ;

    /**
     * Determines if this block can be used as the frame of a conduit.
     *
     * @param level   The current level
     * @param pos     Block position in level
     * @param conduit Conduit position in level
     * @return True, to support the conduit, and make it active with this block.
     */
    boolean isConduitFrame(LevelReader level, BlockPos pos, BlockPos conduit) ;

    /**
     * Determines if this block can be used as part of a frame of a nether portal.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return True, to support being part of a nether portal frame, false otherwise.
     */
    boolean isPortalFrame(BlockGetter level, BlockPos pos) ;

    /**
     * Returns how many experience points this block drops when broken, before application of {@linkplain EnchantmentEffectComponents#BLOCK_EXPERIENCE enchantments}.
     *
     * @param level       The level
     * @param pos         The position of the block being broken
     * @param blockEntity The block entity, if any
     * @param breaker     The entity who broke the block, if known
     * @param tool        The item stack used to break the block. May be empty
     * @return The amount of experience points dropped by this block
     */
    default int getExpDrop(LevelAccessor level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
        return ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).getExpDrop(self(), level, pos, blockEntity, breaker, tool);
    }

    BlockState rotate(LevelAccessor level, BlockPos pos, Rotation direction) ;

    /**
     * Determines the amount of enchanting power this block can provide to an enchanting table.
     *
     * @param level The level
     * @param pos   Block position in level
     * @return The amount of enchanting power this block produces.
     */
    float getEnchantPowerBonus(LevelReader level, BlockPos pos) ;

    /**
     * Called when a block entity on a side of this block changes, is created, or is destroyed.
     *
     * <p>This method is not suitable for listening to capability invalidations.
     * For capability invalidations specifically, use {@link BlockCapabilityCache} instead.
     *
     * @param level    The level
     * @param pos      Block position in level
     * @param neighbor Block position of neighbor
     */
    void onNeighborChange(LevelReader level, BlockPos pos, BlockPos neighbor) ;

    /**
     * Called to determine whether to allow the block to handle its own indirect power rather than using the default rules.
     *
     * @param level The level
     * @param pos   Block position in level
     * @param side  The INPUT side of the block to be powered - ie the opposite of this block's output side
     * @return Whether Block#isProvidingWeakPower should be called when determining indirect power
     */
    boolean shouldCheckWeakPower(SignalGetter level, BlockPos pos, Direction side) ;

    /**
     * If this block should be notified of weak changes.
     * Weak changes are changes 1 block away through a solid block.
     * Similar to comparators.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return true To be notified of changes
     */
    boolean getWeakChanges(LevelReader level, BlockPos pos) ;

    /**
     * Sensitive version of getSoundType
     *
     * @param level  The level
     * @param pos    The position. Note that the level may not necessarily have {@code state} here!
     * @param entity The entity that is breaking/stepping on/placing/hitting/falling on this block, or null if no entity is in this context
     * @return A SoundType to use
     */
    SoundType getSoundType(LevelReader level, BlockPos pos, @Nullable Entity entity) ;

    /**
     * @param level  The level
     * @param pos    The position of this state
     * @param beacon The position of the beacon
     * @return A float RGB [0.0, 1.0] array to be averaged with a beacon's existing beam color, or null to do nothing to the beam
     */
    @Nullable
    default Integer getBeaconColorMultiplier(LevelReader level, BlockPos pos, BlockPos beacon) {
        return ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).getBeaconColorMultiplier(self(), level, pos, beacon);
    }

    /**
     * Used to determine the state 'viewed' by an entity (see
     * {@link Camera#getBlockAtCamera()}).
     * Can be used by fluid blocks to determine if the viewpoint is within the fluid or not.
     *
     * @param level     the level
     * @param pos       the position
     * @param viewpoint the viewpoint
     * @return the block state that should be 'seen'
     */
    BlockState getStateAtViewpoint(BlockGetter level, BlockPos pos, Vec3 viewpoint) ;

    /**
     * @return true if the block is sticky block which used for pull or push adjacent blocks (use by piston)
     */
    boolean isSlimeBlock() ;

    /**
     * @return true if the block is sticky block which used for pull or push adjacent blocks (use by piston)
     */
    boolean isStickyBlock() ;

    /**
     * Determines if this block can stick to another block when pushed by a piston.
     *
     * @param other Other block
     * @return True to link blocks
     */
    boolean canStickTo(BlockState other) ;

    /**
     * Chance that fire will spread and consume this block.
     * 300 being a 100% chance, 0, being a 0% chance.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @param face  The face that the fire is coming from
     * @return A number ranging from 0 to 300 relating used to determine if the block will be consumed by fire
     */
    int getFlammability(BlockGetter level, BlockPos pos, Direction face) ;

    /**
     * Called when fire is updating, checks if a block face can catch fire.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @param face  The face that the fire is coming from
     * @return True if the face can be on fire, false otherwise.
     */
    boolean isFlammable(BlockGetter level, BlockPos pos, Direction face) ;

    /**
     * If the block is flammable, this is called when it gets lit on fire.
     *
     * @param level   The current level
     * @param pos     Block position in level
     * @param face    The face that the fire is coming from
     * @param igniter The entity that lit the fire
     */
    void onCaughtFire(Level level, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) ;

    /**
     * Called when fire is updating on a neighbor block.
     * The higher the number returned, the faster fire will spread around this block.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @param face  The face that the fire is coming from
     * @return A number that is used to determine the speed of fire growth around the block
     */
    int getFireSpreadSpeed(BlockGetter level, BlockPos pos, Direction face) ;

    /**
     * Currently only called by fire when it is on top of this block.
     * Returning true will prevent the fire from naturally dying during updating.
     * Also prevents firing from dying from rain.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @param side  The face that the fire is coming from
     * @return True if this block sustains fire, meaning it will never go out.
     */
    boolean isFireSource(LevelReader level, BlockPos pos, Direction side) ;

    /**
     * Determines if this block is can be destroyed by the specified entities normal behavior.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return True to allow the ender dragon to destroy this block
     */
    boolean canEntityDestroy(BlockGetter level, BlockPos pos, Entity entity) ;

    /**
     * Determines if this block should set fire and deal fire damage
     * to entities coming into contact with it.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return True if the block should deal damage
     */
    boolean isBurning(BlockGetter level, BlockPos pos) ;

    /**
     * Gets the path type of this block when an entity is pathfinding. When
     * {@code null}, uses vanilla behavior.
     *
     * @param level the level which contains this block
     * @param pos   the position of the block
     * @param mob   the mob currently pathfinding, may be {@code null}
     * @return the path type of this block
     */
    @Nullable
    PathType getBlockPathType(BlockGetter level, BlockPos pos, @Nullable Mob mob) ;

    /**
     * Gets the path type of the adjacent block to a pathfinding entity.
     * Path types with a negative malus are not traversable for the entity.
     * Pathfinding entities will favor paths consisting of a lower malus.
     * When {@code null}, uses vanilla behavior.
     *
     * @param level        the level which contains this block
     * @param pos          the position of the block
     * @param mob          the mob currently pathfinding, may be {@code null}
     * @param originalType the path type of the source the entity is on
     * @return the path type of this block
     */
    @Nullable
    PathType getAdjacentBlockPathType(BlockGetter level, BlockPos pos, @Nullable Mob mob, PathType originalType) ;

    /**
     * Determines if this block should drop loot when exploded.
     */
    boolean canDropFromExplosion(BlockGetter level, BlockPos pos, Explosion explosion) ;

    /**
     * Called when the block is destroyed by an explosion.
     * Useful for allowing the block to take into account tile entities,
     * state, etc. when exploded, before it is removed.
     *
     * @param level     The current level
     * @param pos       Block position in level
     * @param explosion The explosion instance affecting the block
     */
    void onBlockExploded(Level level, BlockPos pos, Explosion explosion) ;

    /**
     * Determines if this block's collision box should be treated as though it can extend above its block space.
     * This can be used to replicate fence and wall behavior.
     */
    boolean collisionExtendsVertically(BlockGetter level, BlockPos pos, Entity collidingEntity) ;

    /**
     * Called to determine whether this block should use the fluid overlay texture or flowing texture when it is placed under the fluid.
     *
     * @param level      The level
     * @param pos        Block position in level
     * @param fluidState The state of the fluid
     * @return Whether the fluid overlay texture should be used
     */
    boolean shouldDisplayFluidOverlay(BlockAndTintGetter level, BlockPos pos, FluidState fluidState) ;

    /**
     * Returns the state that this block should transform into when right-clicked by a tool.
     * For example: Used to determine if {@link ToolActions#AXE_STRIP an axe can strip},
     * {@link ToolActions#SHOVEL_FLATTEN a shovel can path}, or {@link ToolActions#HOE_TILL a hoe can till}.
     * Returns {@code null} if nothing should happen.
     *
     * @param context     The use on context that the action was performed in
     * @param itemAbility The action being performed by the tool
     * @param simulate    If {@code true}, no actions that modify the world in any way should be performed. If {@code false}, the world may be modified.
     * @return The resulting state after the action has been performed
     */
    @Nullable
    BlockState getToolModifiedState(UseOnContext context, ToolAction itemAbility, boolean simulate) ;

    /**
     * Checks if a player or entity handles movement on this block like scaffolding.
     *
     * @param entity The entity on the scaffolding
     * @return True if the block should act like scaffolding
     */
    boolean isScaffolding(LivingEntity entity) ;

    /**
     * Whether redstone dust should visually connect to this block on a side.
     * <p>
     * Modded redstone wire blocks should call this function to determine visual connections.
     *
     * @param level     The level
     * @param pos       The block position in level
     * @param direction The coming direction of the redstone dust connection (with respect to the block at pos)
     * @return True if redstone dust should visually connect on the side passed
     */
    boolean canRedstoneConnectTo(BlockGetter level, BlockPos pos, @Nullable Direction direction) ;

    /**
     * Whether this block hides the neighbors face pointed towards by the given direction.
     * <p>
     * This method should only be used for blocks you don't control, for your own blocks override
     * {@link net.minecraft.world.level.block.Block#skipRendering(BlockState, BlockState, Direction)}
     * on the respective block instead
     *
     * @param level         The world
     * @param pos           The blocks position in the world
     * @param neighborState The neighboring blocks {@link BlockState}
     * @param dir           The direction towards the neighboring block
     */
    boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState neighborState, Direction dir) ;

    /**
     * Whether this block allows a neighboring block to hide the face of this block it touches.
     * If this returns true, {@link IBlockStateExtension#hidesNeighborFace(BlockGetter, BlockPos, BlockState, Direction)}
     * will be called on the neighboring block.
     */
    boolean supportsExternalFaceHiding() ;

    /**
     * Called after the {@link BlockState} at the given {@link BlockPos} was changed and neighbors were updated.
     * This method is called on the server and client side.
     * Modifying the level is disallowed in this method.
     * Useful for calculating additional data based on the new state and the neighbor's reactions to the state change.
     *
     * @param level    The level the state was modified in
     * @param pos      The blocks position in the level
     * @param oldState The previous state of the block at the given position, may be a different block than this one
     */
    void onBlockStateChange(LevelReader level, BlockPos pos, BlockState oldState) ;

    /**
     * Returns whether the block can be hydrated by a fluid.
     *
     * <p>Hydration is an arbitrary word which depends on the block.
     * <ul>
     * <li>A farmland has moisture</li>
     * <li>A sponge can soak up the liquid</li>
     * <li>A coral can live</li>
     * </ul>
     *
     * @param getter   the getter which can get the block
     * @param pos      the position of the block being hydrated
     * @param fluid    the state of the fluid
     * @param fluidPos the position of the fluid
     * @return {@code true} if the block can be hydrated, {@code false} otherwise
     */
    boolean canBeHydrated(BlockGetter getter, BlockPos pos, FluidState fluid, BlockPos fluidPos) ;

    /**
     * Returns the {@link BlockState} that this state reports to look like on the given side for querying by other mods.
     *
     * @param level      The level this block is in
     * @param pos        The block's position in the level
     * @param side       The side of the block that is being queried
     * @param queryState The state of the block that is querying the appearance, or {@code null} if not applicable
     * @param queryPos   The position of the block that is querying the appearance, or {@code null} if not applicable
     * @return The appearance of this block from the given side
     * @see IBlockExtension#getAppearance(BlockState, BlockAndTintGetter, BlockPos, Direction, BlockState, BlockPos)
     */
    BlockState getAppearance(BlockAndTintGetter level, BlockPos pos, Direction side, @Nullable BlockState queryState, @Nullable BlockPos queryPos) ;

    /**
     * Return true if the state is able to be replaced with Blocks.AIR in chunk sections that is entirely made of blocks that return true for isEmpty
     *
     * @return True if the block should be allowed to be optimized away into Blocks.AIR
     */
    default boolean isEmpty() {
        return ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).isEmpty(self());
    }

    /**
     * Determines if this block can spawn Bubble Columns and if so, what direction the column flows.
     * <p>
     * NOTE: The block itself will still need to call {@link net.minecraft.world.level.block.BubbleColumnBlock#updateColumn(LevelAccessor, BlockPos, BlockState)} in their tick method and schedule a block tick in the block's onPlace.
     * Also, schedule a fluid tick in updateShape method if update direction is up. Both are needed in order to get the Bubble Columns to function properly. See {@link net.minecraft.world.level.block.SoulSandBlock} and {@link net.minecraft.world.level.block.MagmaBlock} for example.
     *
     * @return BubbleColumnDirection.NONE for no Bubble Column. Otherwise, will spawn Bubble Column flowing with specified direction
     */
    default BubbleColumnDirection getBubbleColumnDirection() {
        return ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).getBubbleColumnDirection(self());
    }

    /**
     * Determines if a fluid adjacent to the block on the given side should not be rendered.
     *
     * @param selfFace      the face of this block that the fluid is adjacent to
     * @param adjacentFluid the fluid that is touching that face
     * @return true if this block should cause the fluid's face to not render
     */
    default boolean shouldHideAdjacentFluidFace(Direction selfFace, FluidState adjacentFluid) {
        return ((net.neoforged.neoforge.common.extensions.IBlockExtension) (Object) self().getBlock()).shouldHideAdjacentFluidFace(self(), selfFace, adjacentFluid);
    }
}
