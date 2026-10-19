/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidType;

public interface IBoatExtension {
    private Boat self() {
        return (Boat) (Object) this;
    }

    /**
     * Returns whether the boat can be used on the fluid.
     *
     * @param state the state of the fluid
     * @return {@code true} if the boat can be used, {@code false} otherwise
     */
    boolean canBoatInFluid(FluidState state) ;

    /**
     * Returns whether the boat can be used on the fluid.
     *
     * @param type the type of the fluid
     * @return {@code true} if the boat can be used, {@code false} otherwise
     */
    boolean canBoatInFluid(FluidType type) ;
}
