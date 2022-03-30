package com.cheatbreaker.obf.transformer.misc;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.transformer.methods.ProxyTransformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.FixedClassWriter;
import com.cheatbreaker.obf.utils.configuration.NumberConversions;
import com.cheatbreaker.obf.utils.pair.ClassMethodNode;
import lombok.SneakyThrows;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChecksumTransformer extends Transformer {

    public ChecksumTransformer(Obf obf) {
        super(obf);

        this.targets = config.getStringList("targets");
        this.reobfTarget = config.getBoolean("reobf", true);
        this.holders = config.getStringList("holders");

    }

    @Override
    public void visit(ClassNode classNode) {

    }

    private List<String> targets;
    private List<String> holders;
    private boolean reobfTarget;

    @Override
    public String getSection() {
        return "misc.checksum";
    }

    @SneakyThrows
    @Override
    protected void after() {

        if (targets.isEmpty() || holders.isEmpty()) return;

        for (String target : targets) {
            for (String holder : holders) {
                if (target.equals(holder)) {
                    error("Target and holder are the same: {}",  target);
                }
            }
        }

        List<ClassNode> classNodes = new ArrayList<>(obf.getClasses());
        Collections.shuffle(classNodes);

        for (ClassNode classNode : classNodes) {

            if (targets.contains(classNode.name)) {

                Collections.shuffle(holders);
                String holderName = holders.get(0);

                byte[] b;
                FixedClassWriter writer = new FixedClassWriter(obf, ClassWriter.COMPUTE_FRAMES);
                classNode.accept(writer);
                b = writer.toByteArray();

                obf.addGeneratedClass(classNode.name, b);

                ClassNode holder = obf.assureLoaded(holderName);
                if (holder == null) {
                    error("Holder class not found: %s", holderName);
                } else {
                    MethodNode checkMethod;
                    String name = " ";
                    String desc = "()V";
                    checkMethod = new MethodNode(ACC_STATIC | ACC_SYNTHETIC, name, desc, null, null);
                    checkMethod.visitCode();

                    long r1 = random.nextLong();

                    checkMethod.visitLdcInsn(r1);
                    checkMethod.visitVarInsn(LSTORE, 5);

                    checkMethod.visitLdcInsn(Type.getType("L" + holder.name +  ";"));
                    checkMethod.visitLdcInsn("/" + classNode.name + ".class");
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
                    checkMethod.visitVarInsn(ASTORE, 1);
                    checkMethod.visitTypeInsn(NEW, "java/io/ByteArrayOutputStream");
                    checkMethod.visitInsn(DUP);
                    checkMethod.visitMethodInsn(INVOKESPECIAL, "java/io/ByteArrayOutputStream", "<init>", "()V", false);
                    checkMethod.visitVarInsn(ASTORE, 2);
                    checkMethod.visitIntInsn(SIPUSH, 4096);
                    checkMethod.visitIntInsn(NEWARRAY, T_BYTE);
                    checkMethod.visitVarInsn(ASTORE, 3);
                    Label label0 = new Label();
                    checkMethod.visitLabel(label0);
                    checkMethod.visitInsn(ICONST_M1);
                    checkMethod.visitVarInsn(ALOAD, 1);
                    checkMethod.visitVarInsn(ALOAD, 3);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/io/InputStream", "read", "([B)I", false);
                    checkMethod.visitVarInsn(LLOAD, 5);

                    long r2 = random.nextLong();

                    checkMethod.visitLdcInsn(r2);
                    checkMethod.visitInsn(LXOR);

                    checkMethod.visitVarInsn(LSTORE, 5);

                    checkMethod.visitInsn(DUP);
                    checkMethod.visitVarInsn(ISTORE, 4);
                    Label label1 = new Label();
                    checkMethod.visitJumpInsn(IF_ICMPEQ, label1);
                    checkMethod.visitVarInsn(ALOAD, 2);
                    checkMethod.visitVarInsn(ALOAD, 3);
                    checkMethod.visitInsn(ICONST_0);
                    checkMethod.visitVarInsn(ILOAD, 4);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "write", "([BII)V", false);
                    checkMethod.visitJumpInsn(GOTO, label0);
                    checkMethod.visitLabel(label1);
                    checkMethod.visitVarInsn(ALOAD, 2);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "size", "()I", false);

                    for (long i = 0; i < Math.floorDiv(b.length, 4096); i++) {
                        r1 ^= r2;
                    }

                    long len = b.length ^ r1;

                    checkMethod.visitVarInsn(LLOAD, 5);
                    checkMethod.visitLdcInsn(len);
                    checkMethod.visitInsn(LXOR);
                    checkMethod.visitInsn(L2I);
                    Label label2 = new Label();
                    checkMethod.visitJumpInsn(IF_ICMPEQ, label2);

                    checkMethod.visitMethodInsn(INVOKESTATIC, "sun/misc/Launcher", "getLauncher", "()Lsun/misc/Launcher;", false);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Launcher", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
                    checkMethod.visitInsn(DUP);
                    checkMethod.visitVarInsn(ASTORE, 7);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;", false);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredFields", "()[Ljava/lang/reflect/Field;", false);
                    checkMethod.visitInsn(ICONST_0);
                    checkMethod.visitInsn(AALOAD);
                    checkMethod.visitVarInsn(ASTORE, 8);
                    checkMethod.visitVarInsn(ALOAD, 8);
                    checkMethod.visitInsn(ICONST_1);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
                    checkMethod.visitVarInsn(ALOAD, 8);
                    checkMethod.visitVarInsn(ALOAD, 7);
                    checkMethod.visitTypeInsn(NEW, "sun/misc/URLClassPath");
                    checkMethod.visitInsn(DUP);
                    checkMethod.visitInsn(ICONST_0);
                    checkMethod.visitTypeInsn(ANEWARRAY, "java/net/URL");
                    checkMethod.visitInsn(ACONST_NULL);
                    checkMethod.visitMethodInsn(INVOKESPECIAL, "sun/misc/URLClassPath", "<init>", "([Ljava/net/URL;Ljava/security/AccessControlContext;)V", false);
                    checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);

                    checkMethod.visitLabel(label2);
                    checkMethod.visitInsn(RETURN);
                    checkMethod.visitEnd();

                    holder.methods.add(checkMethod);

                    Analyzer<?> analyzer = new Analyzer<>(new BasicInterpreter());
                    analyzer.analyzeAndComputeMaxs(holder.name, checkMethod);

                    MethodNode clinit = AsmUtils.getClinit(holder);
                    clinit.instructions.insertBefore(clinit.instructions.getFirst(), new MethodInsnNode(INVOKESTATIC, holder.name, name,
                            desc));

                    if (reobfTarget) {
                        for (Transformer transformer : obf.getTransformers()) {
                            transformer.target = new ClassMethodNode(holder, checkMethod);
                            transformer.run(holder);
                            transformer.target = null;
                        }
                    }
                }

                break;
            }
        }
    }
}
