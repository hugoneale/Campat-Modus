package io.github.hugoneale.campat.runtime.legacy.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Minecraft 1.20.1's long lived BufferBuilder ({@code getBuilder()}, {@code begin}, vertices, {@code end})
 * on 1.21.1, where a BufferBuilder is created per batch by {@link Tesselator#begin}. Vertex calls go to
 * the batch opened by the last {@link #legacyBegin}.
 */
public class LegacyBufferBuilder extends BufferBuilder {
    private static final ByteBufferBuilder PLACEHOLDER = new ByteBufferBuilder(256);

    private final Tesselator tesselator;
    private final ByteBufferBuilder buffer;
    private BufferBuilder current;

    /** shares the tesselator's buffer */
    LegacyBufferBuilder(Tesselator tesselator) {
        super(PLACEHOLDER, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        this.tesselator = tesselator;
        this.buffer = null;
    }

    /** owns its buffer ({@code new BufferBuilder(int)}) */
    LegacyBufferBuilder(int capacity) {
        super(PLACEHOLDER, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        this.tesselator = null;
        this.buffer = new ByteBufferBuilder(capacity);
    }

    ByteBufferBuilder buffer() {
        return buffer;
    }

    void legacyBegin(VertexFormat.Mode mode, VertexFormat format) {
        current = tesselator != null ? tesselator.begin(mode, format) : new BufferBuilder(buffer, mode, format);
    }

    boolean legacyBuilding() {
        return current != null;
    }

    MeshData legacyEnd() {
        BufferBuilder b = current;
        current = null;
        return b == null ? null : b.build();
    }

    private BufferBuilder cur() {
        if (current == null) throw new IllegalStateException("Not building! (1.20.1 BufferBuilder.begin was not called)");
        return current;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        cur().addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        cur().setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        cur().setColor(argb);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        cur().setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        cur().setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setOverlay(int overlay) {
        cur().setOverlay(overlay);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        cur().setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setLight(int light) {
        cur().setLight(light);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        cur().setNormal(x, y, z);
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        cur().addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
    }

    @Override
    public MeshData build() {
        return legacyEnd();
    }

    @Override
    public MeshData buildOrThrow() {
        MeshData m = legacyEnd();
        if (m == null) throw new IllegalStateException("BufferBuilder was empty");
        return m;
    }
}
