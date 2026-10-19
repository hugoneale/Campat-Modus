/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraftforge.client.model.IQuadTransformer;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Extension interface for {@link VertexConsumer}.
 */
public interface IVertexConsumerExtension {
    private VertexConsumer self() {
        return (VertexConsumer) (Object) this;
    }

    /**
     * Consumes an unknown {@link VertexFormatElement} as a raw int data array.
     * <p>
     * If the consumer needs to store the data for later use, it must copy it. There are no guarantees on immutability.
     */
    VertexConsumer misc(VertexFormatElement element, int... rawData) ;

    /**
     * Variant with no per-vertex shading.
     */
    void putBulkData(PoseStack.Pose pose, BakedQuad bakedQuad, float red, float green, float blue, float alpha, int packedLight, int packedOverlay, boolean readExistingColor) ;

    int applyBakedLighting(int packedLight, ByteBuffer data) ;

    void applyBakedNormals(Vector3f generated, ByteBuffer data, Matrix3f normalTransform) ;
}
