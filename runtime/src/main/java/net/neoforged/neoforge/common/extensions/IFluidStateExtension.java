/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

public interface IFluidStateExtension {
    private FluidState self() {
        return (FluidState) (Object) this;
    }

    /**
     * Returns the explosion resistance of the fluid.
     *
     * @param level     the level the fluid is in
     * @param pos       the position of the fluid
     * @param explosion the explosion the fluid is absorbing
     * @return the amount of the explosion the fluid can absorb
     */
    float getExplosionResistance(BlockGetter level, BlockPos pos, Explosion explosion) ;

    /**
     * Returns the type of this fluid.
     *
     * @return the type of this fluid
     */
    FluidType getFluidType() ;

    /**
     * Performs how an entity moves when within the fluid. If using custom
     * movement logic, the method should return {@code true}. Otherwise, the
     * movement logic will default to water.
     *
     * @param entity         the entity moving within the fluid
     * @param movementVector the velocity of how the entity wants to move
     * @param gravity        the gravity to apply to the entity
     * @return {@code true} if custom movement logic is performed, {@code false} otherwise
     */
    boolean move(LivingEntity entity, Vec3 movementVector, double gravity) ;

    /**
     * Returns whether the fluid can create a source.
     *
     * @param level the level that can get the fluid
     * @param pos   the location of the fluid
     * @return {@code true} if the fluid can create a source, {@code false} otherwise
     */
    boolean canConvertToSource(Level level, BlockPos pos) ;

    /**
     * Returns whether the boat can be used on the fluid.
     *
     * @param boat the boat trying to be used on the fluid
     * @return {@code true} if the boat can be used, {@code false} otherwise
     */
    boolean supportsBoating(Boat boat) ;

    /**
     * Gets the path type of this fluid when an entity is pathfinding. When
     * {@code null}, uses vanilla behavior.
     *
     * @param level       the level which contains this fluid
     * @param pos         the position of the fluid
     * @param mob         the mob currently pathfinding, may be {@code null}
     * @param canFluidLog {@code true} if the path is being applied for fluids that can log blocks,
     *                    should be checked against if the fluid can log a block
     * @return the path type of this fluid
     */
    @Nullable
    PathType getBlockPathType(BlockGetter level, BlockPos pos, @org.jetbrains.annotations.Nullable Mob mob, boolean canFluidLog) ;

    /**
     * Gets the path type of the adjacent fluid to a pathfinding entity.
     * Path types with a negative malus are not traversable for the entity.
     * Pathfinding entities will favor paths consisting of a lower malus.
     * When {@code null}, uses vanilla behavior.
     *
     * @param level        the level which contains this fluid
     * @param pos          the position of the fluid
     * @param mob          the mob currently pathfinding, may be {@code null}
     * @param originalType the path type of the source the entity is on
     * @return the path type of this fluid
     */
    @Nullable
    PathType getAdjacentBlockPathType(BlockGetter level, BlockPos pos, @org.jetbrains.annotations.Nullable Mob mob, PathType originalType) ;

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
     * @param getter    the getter which can get the fluid
     * @param pos       the position of the fluid
     * @param source    the state of the block being hydrated
     * @param sourcePos the position of the block being hydrated
     * @return {@code true} if the block can be hydrated, {@code false} otherwise
     */
    boolean canHydrate(BlockGetter getter, BlockPos pos, BlockState source, BlockPos sourcePos) ;

    /**
     * Returns whether the block can be extinguished by this fluid.
     *
     * @param getter the getter which can get the fluid
     * @param pos    the position of the fluid
     * @return {@code true} if the block can be extinguished, {@code false} otherwise
     */
    boolean canExtinguish(BlockGetter getter, BlockPos pos) ;
}
