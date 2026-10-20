package io.github.hugoneale.campat.runtime.legacy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Minecraft 1.20.1 rendering calls that 1.21.1 removed or changed. */
public final class LegacyRender {
    private LegacyRender() {}

    private static final Map<Tesselator, LegacyBufferBuilder> BUILDERS = new WeakHashMap<>();

    // ---- Tesselator / BufferBuilder lifecycle ----

    public static BufferBuilder getBuilder(Tesselator tesselator) {
        synchronized (BUILDERS) {
            return BUILDERS.computeIfAbsent(tesselator, LegacyBufferBuilder::new);
        }
    }

    public static void end(Tesselator tesselator) {
        MeshData mesh = ((LegacyBufferBuilder) getBuilder(tesselator)).legacyEnd();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    public static BufferBuilder newBufferBuilder(int capacity) {
        return new LegacyBufferBuilder(capacity);
    }

    public static void begin(BufferBuilder builder, VertexFormat.Mode mode, VertexFormat format) {
        if (!(builder instanceof LegacyBufferBuilder legacy)) throw new IllegalStateException("1.20.1 begin() on a 1.21 BufferBuilder");
        legacy.legacyBegin(mode, format);
    }

    public static MeshData end(BufferBuilder builder) {
        return builder instanceof LegacyBufferBuilder legacy ? legacy.legacyEnd() : builder.build();
    }

    public static MeshData endOrDiscardIfEmpty(BufferBuilder builder) {
        return end(builder);
    }

    public static boolean building(BufferBuilder builder) {
        return builder instanceof LegacyBufferBuilder legacy && legacy.legacyBuilding();
    }

    public static void release(MeshData mesh) {
        if (mesh != null) mesh.close();
    }

    public static void drawWithShader(MeshData mesh) {
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    public static MultiBufferSource.BufferSource immediate(BufferBuilder builder) {
        ByteBufferBuilder buffer = builder instanceof LegacyBufferBuilder legacy && legacy.buffer() != null ? legacy.buffer() : new ByteBufferBuilder(256);
        return MultiBufferSource.immediate(buffer);
    }

    // ---- particle render types: 1.20.1 began the builder it was given ----

    private static final Map<Class<?>, java.lang.reflect.Method> PARTICLE_BEGIN = new java.util.concurrent.ConcurrentHashMap<>();

    /** ParticleRenderType.begin(tesselator, textures) of a 1.20.1 render type */
    public static BufferBuilder particleBegin(net.minecraft.client.particle.ParticleRenderType type, Tesselator tesselator, net.minecraft.client.renderer.texture.TextureManager textures) {
        java.lang.reflect.Method old = PARTICLE_BEGIN.computeIfAbsent(type.getClass(), c -> {
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                try {
                    java.lang.reflect.Method m = k.getDeclaredMethod("begin", BufferBuilder.class, net.minecraft.client.renderer.texture.TextureManager.class);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        });
        if (old == null) return null;
        LegacyBufferBuilder legacy = (LegacyBufferBuilder) getBuilder(tesselator);
        try {
            old.invoke(type, legacy, textures);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return legacy.takeCurrent();
    }

    // ---- VertexConsumer methods without a 1.21 counterpart ----

    public static void endVertex(VertexConsumer consumer) {
    }

    public static void defaultColor(VertexConsumer consumer, int r, int g, int b, int a) {
    }

    public static void unsetDefaultColor(VertexConsumer consumer) {
    }

    public static VertexConsumer overlayCoords(VertexConsumer consumer, int u, int v) {
        return consumer.setUv1(u, v);
    }

    public static VertexConsumer uv2(VertexConsumer consumer, int light) {
        return consumer.setLight(light);
    }

    public static VertexConsumer normal(VertexConsumer consumer, Matrix3f matrix, float x, float y, float z) {
        Vector3f n = matrix.transform(new Vector3f(x, y, z)).normalize();
        return consumer.setNormal(n.x(), n.y(), n.z());
    }

    // ---- the model view stack became a Matrix4fStack ----

    public static PoseStack getModelViewStack() {
        return new ModelViewPoseStack(RenderSystem.getModelViewStack());
    }

    /** A PoseStack that mirrors its operations onto 1.21's model view Matrix4fStack. */
    static final class ModelViewPoseStack extends PoseStack {
        private final Matrix4fStack stack;

        ModelViewPoseStack(Matrix4fStack stack) {
            this.stack = stack;
            last().pose().set(stack);
        }

        @Override
        public void pushPose() {
            super.pushPose();
            stack.pushMatrix();
        }

        @Override
        public void popPose() {
            super.popPose();
            stack.popMatrix();
        }

        @Override
        public void translate(float x, float y, float z) {
            super.translate(x, y, z);
            stack.translate(x, y, z);
        }

        @Override
        public void translate(double x, double y, double z) {
            translate((float) x, (float) y, (float) z);
        }

        @Override
        public void scale(float x, float y, float z) {
            super.scale(x, y, z);
            stack.scale(x, y, z);
        }

        @Override
        public void mulPose(Quaternionf rotation) {
            super.mulPose(rotation);
            stack.rotate(rotation);
        }

        @Override
        public void mulPose(Matrix4f matrix) {
            super.mulPose(matrix);
            stack.mul(matrix);
        }

        @Override
        public void setIdentity() {
            super.setIdentity();
            stack.identity();
        }
    }
}
