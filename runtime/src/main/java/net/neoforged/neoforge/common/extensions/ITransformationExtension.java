/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common.extensions;

import com.mojang.math.Transformation;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Extension interface for {@link Transformation}.
 */
public interface ITransformationExtension {
    private Transformation self() {
        return (Transformation) (Object) this;
    }

    /**
     * {@return whether this transformation is the identity transformation}
     *
     * @see Transformation#identity()
     */
    boolean isIdentity() ;

    /**
     * Transforms the position according to this transformation.
     *
     * @param position the position to transform
     */
    void transformPosition(Vector4f position) ;

    /**
     * Transforms the normal according to this transformation and normalizes it.
     *
     * @param normal the normal to transform
     */
    void transformNormal(Vector3f normal) ;

    /**
     * Rotates the direction according to this transformation and returns the nearest {@code Direction} to the
     * resulting direction.
     *
     * @param facing the direction to transform
     * @return the {@code Direction} value nearest to the resulting transformed direction
     * @see Direction#rotate(Matrix4f, Direction)
     */
    Direction rotateTransform(Direction facing) ;

    /**
     * Converts and returns a new transformation based on this transformation from assuming a center-block system to an
     * opposing-corner-block system.
     *
     * @return a new transformation using the opposing-corner-block system
     */
    Transformation blockCenterToCorner() ;

    /**
     * Converts and returns a new transformation based on this transformation from assuming an opposing-corner-block
     * system to a center-block system.
     *
     * @return a new transformation using the center-block system
     */
    Transformation blockCornerToCenter() ;

    /**
     * Returns a new transformation with a changed origin by applying the given parameter (which is relative to the
     * current origin). This can be used for switching between coordinate systems.
     *
     * @param origin the new origin as relative to the current origin
     * @return a new transformation with a changed origin
     */
    Transformation applyOrigin(Vector3f origin) ;
}
