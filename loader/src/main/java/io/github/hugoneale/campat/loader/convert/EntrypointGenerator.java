package io.github.hugoneale.campat.loader.convert;

import io.github.hugoneale.campat.loader.remap.ClassConverter;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Generates one Forge {@code @Mod} class per NeoForge mod id. Forge only supports a single
 * {@code @Mod} class per mod id and only the {@code FMLJavaModLoadingContext} constructor, while
 * NeoForge allows several (dist specific) entrypoints with injected constructor arguments. The
 * generated class hands everything to {@code NeoEntrypoints} in the runtime.
 */
public final class EntrypointGenerator {
    public static final String RUNTIME_ENTRYPOINTS = "io/github/hugoneale/campat/runtime/loading/NeoEntrypoints";

    public record Generated(String internalName, byte[] bytes) {}

    public static String packageFor(String modId) {
        String safe = modId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return "io/github/hugoneale/campat/gen/m_" + safe;
    }

    public Generated generate(String modId, List<ClassConverter.Entrypoint> entrypoints) {
        String name = packageFor(modId) + "/NeoModEntry";
        // "class;DIST,DIST|class;..." - an empty dist list means both sides
        String spec = entrypoints.stream()
            .map(e -> e.className() + ";" + String.join(",", e.dists()))
            .collect(Collectors.joining("|"));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        AnnotationVisitor av = cw.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        av.visit("value", modId);
        av.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitLdcInsn(modId);
        mv.visitLdcInsn(spec);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME_ENTRYPOINTS, "construct",
            "(Lnet/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext;Ljava/lang/String;Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return new Generated(name, cw.toByteArray());
    }
}
