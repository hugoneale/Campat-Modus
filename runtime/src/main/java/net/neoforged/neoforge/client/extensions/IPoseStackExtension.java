/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import org.joml.Vector3f;

/**
 * Extension interface for {@link com.mojang.blaze3d.vertex.PoseStack}.
 */
public interface IPoseStackExtension {
    private PoseStack self() {
        return (PoseStack) (Object) this;
    }

    /**
     * Pushes and applies the {@code transformation} to this pose stack. <br>
     * The effects of this method can be reversed by a corresponding {@link PoseStack#popPose()} call.
     *
     * @param transformation the transformation to push
     */
    void pushTransformation(Transformation transformation) ;
}
