/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;

public interface ILivingEntityExtension extends IEntityExtension {
    LivingEntity self() ;

    @Override
    boolean canSwimInFluidType(FluidType type) ;

    /**
     * Performs what to do when an entity attempts to go up or "jump" in a fluid.
     *
     * @param type the type of the fluid
     */
    void jumpInFluid(FluidType type) ;

    /**
     * Performs what to do when an entity attempts to go down or "sink" in a fluid.
     *
     * @param type the type of the fluid
     */
    void sinkInFluid(FluidType type) ;

    /**
     * Returns whether the entity can drown in the fluid.
     *
     * @param type the type of the fluid
     * @return {@code true} if the entity can drown in the fluid, {@code false} otherwise
     */
    boolean canDrownInFluidType(FluidType type) ;

    /**
     * Performs how an entity moves when within the fluid. If using custom
     * movement logic, the method should return {@code true}. Otherwise, the
     * movement logic will default to water.
     *
     * @param state          the state of the fluid
     * @param movementVector the velocity of how the entity wants to move
     * @param gravity        the gravity to apply to the entity
     * @return {@code true} if custom movement logic is performed, {@code false} otherwise
     */
    boolean moveInFluid(FluidState state, Vec3 movementVector, double gravity) ;

}
