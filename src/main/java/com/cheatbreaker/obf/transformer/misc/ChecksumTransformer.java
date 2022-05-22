package com.cheatbreaker.obf.transformer.misc;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import com.cheatbreaker.obf.utils.asm.ContextClassWriter;
import com.cheatbreaker.obf.utils.pair.ClassMethodNode;
import lombok.SneakyThrows;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChecksumTransformer extends Transformer {

    public ChecksumTransformer(Obf obf) {
        super(obf);

        this.targets = config.getStringList("targets");
        this.reobfTarget = config.getBoolean("reobf", true);
        this.holders = config.getStringList("holders");
        this.randomHolders = config.getBoolean("randomHolders", false);

        if (randomHolders) {
            this.holders = new ArrayList<>();
            for (ClassWrapper classNode : obf.getClasses()) {
                if (targets.contains(classNode.name)) {
                    continue;
                }
                holders.add(classNode.name);
            }
        }

    }

    private final List<String> targets;
    private List<String> holders;
    private final boolean reobfTarget;
    private final boolean randomHolders;

    @Override
    public String getSection() {
        return "misc.checksum";
    }

    @SneakyThrows
    @Override
    protected void after() {

        if (targets.isEmpty() || holders.isEmpty()) return;

        if (!randomHolders) {
            for (String target : targets) {
                for (String holder : holders) {
                    if (target.equals(holder)) {
                        error("Target and holder are the same: %s",  target);
                    }
                }
            }
        }

//        log("Targets: %s", targets);
//        log("Holders: %s", randomHolders ? "All" : holders);

        List<ClassWrapper> classNodes = new ArrayList<>(obf.getClasses());
        Collections.shuffle(classNodes);

        boolean applied = false;

        for (ClassWrapper classNode : classNodes) {
            if (targets.contains(classNode.name)) {

                Collections.shuffle(holders);
                String holderName = holders.get(0);

                byte[] b;
                ContextClassWriter writer = new ContextClassWriter(ClassWriter.COMPUTE_FRAMES);
                classNode.accept(writer);
                b = writer.toByteArray();

                obf.addGeneratedClass(classNode.name, b);

                ClassWrapper holder = obf.assureLoaded(holderName);
                if (holder == null) {
                    error("Holder class not found: %s", holderName);
                } else {
                    log("Applying checksum to %s inside of %s", classNode.name, holderName);
                    applied = true;
                    MethodNode checkMethod;
                    String name = config.getString("methodName");
                    String desc = "()V";
                    checkMethod = new MethodNode(ACC_PRIVATE | ACC_SYNTHETIC | ACC_STATIC, name, desc, null, null);
                    checkMethod.visitCode();

                    Label label2 = new Label();
                    Label label3 = new Label();

                    if (config.getBoolean("reduced", false) || obf.isTransformerEnabled(PackerTransformer.class)) {

                        log("Reduced checksum check due to packer");

                        checkMethod.visitLdcInsn(Type.getType("L" + holder.name + ";"));
                        String s = "/" + classNode.name + ".class";
                        checkMethod.visitLdcInsn(s);

                        checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
                        checkMethod.visitJumpInsn(IFNONNULL, label3);
                        checkMethod.visitJumpInsn(GOTO, label2);

                    } else {

                        int r1 = random.nextInt();
                        int r2 = random.nextInt();

                        int real = r1;

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ByteArrayInputStream bais = new ByteArrayInputStream(b);

                        byte[] byArray = new byte[4096];
                        while (true) {
                            int n = bais.read(byArray, 0, byArray.length);
                            real ^= r2 ^ baos.size();
                            if (n == -1) break;
                            baos.write(byArray, 0, n);
                        }

                        int bStart = writer.offsetCPStart;
                        int bEnd = writer.offsetMethodEnd;

                        byte[] b2 = new byte[bEnd - bStart];
                        System.arraycopy(b, bStart, b2, 0, b2.length);

                        int hash = Arrays.hashCode(b2) ^ real;

                        log("Hash: %d Start: %d End: %d", hash, bStart, bEnd);

                        checkMethod.visitLdcInsn(r1);
                        checkMethod.visitVarInsn(ISTORE, 5);

                        checkMethod.visitLdcInsn(Type.getType("L" + holder.name + ";"));
                        String s = "/" + classNode.name + ".class";
                        checkMethod.visitLdcInsn(s);

                        checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
                        checkMethod.visitVarInsn(ASTORE, 1);

                        checkMethod.visitVarInsn(ALOAD, 1);
                        checkMethod.visitJumpInsn(IFNULL, label3);

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
                        checkMethod.visitInsn(ICONST_0);
                        checkMethod.visitVarInsn(ALOAD, 3);
                        checkMethod.visitInsn(ARRAYLENGTH);
                        checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/io/InputStream", "read", "([BII)I", false);

                        checkMethod.visitVarInsn(ILOAD, 5);
                        checkMethod.visitLdcInsn(r2);
                        checkMethod.visitVarInsn(ALOAD, 2);
                        checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "size", "()I", false);
                        checkMethod.visitInsn(IXOR);
                        checkMethod.visitInsn(IXOR);

                        checkMethod.visitVarInsn(ISTORE, 5);

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
                        checkMethod.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "toByteArray", "()[B", false);

                        checkMethod.visitLdcInsn(bEnd - bStart);
                        checkMethod.visitIntInsn(NEWARRAY, T_BYTE);
                        checkMethod.visitVarInsn(ASTORE, 6);

                        checkMethod.visitLdcInsn(bStart);
                        checkMethod.visitVarInsn(ALOAD, 6);
                        checkMethod.visitInsn(ICONST_0);
                        checkMethod.visitVarInsn(ALOAD, 6);
                        checkMethod.visitInsn(ARRAYLENGTH);
                        checkMethod.visitMethodInsn(INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);

                        checkMethod.visitVarInsn(ALOAD, 6);

                        checkMethod.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "hashCode", "([B)I", false);

                        checkMethod.visitVarInsn(ILOAD, 5);
                        checkMethod.visitLdcInsn(hash);
                        checkMethod.visitInsn(IXOR);

                        checkMethod.visitJumpInsn(IF_ICMPEQ, label2);

                    }

                    checkMethod.visitLabel(label3);

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
//                            boolean old = transformer.enabled;
//                            transformer.enabled = true;
                            if (!transformer.canBeIterated) continue;
                            transformer.target = new ClassMethodNode(holder, checkMethod);
                            transformer.run(holder);
                            transformer.target = null;
//                            transformer.enabled = old;
                        }
                    }
                }

                break;
            }
        }

        if (!applied) {
            error("Could not find any targets and holders");
        }
    }
}
