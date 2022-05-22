package com.cheatbreaker.obf.transformer.misc;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import com.cheatbreaker.obf.utils.asm.ContextClassWriter;
import lombok.SneakyThrows;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.jar.Manifest;

/**
 * @author darklol9
 * Made for forge mods, you can edit and make it work on other applications
 */
public class PackerTransformer extends Transformer {

    public PackerTransformer(Obf obf) {
        super(obf);
    }

    // check if array of bytes is java class
    public static boolean isClass(byte[] bytes) {
        boolean res;
        if (bytes == null) {
            return false;
        }
        if (bytes.length < 6) {
            return false;
        }
        res = bytes[0] == (byte) 0xCA && bytes[1] == (byte) 0xFE && bytes[2] == (byte) 0xBA && bytes[3] == (byte) 0xBE;
        return res;
    }

    @Override
    protected void after() {
        int key = random.nextInt();

        ClassWrapper mainClass = null;

        if (config.getBoolean("forge")) {

            AnnotationNode annotation = null;

            LinkedList<MethodNode> events = new LinkedList<>();

            for (ClassWrapper classNode : obf.getClasses()) {
                if (classNode.visibleAnnotations != null) {
                    for (AnnotationNode visibleAnnotation : classNode.visibleAnnotations) {
                        if (visibleAnnotation.desc.contains("net/minecraftforge/fml/common/Mod")) {
                            annotation = visibleAnnotation;
                            classNode.access |= ACC_PUBLIC;
                            classNode.access &= ~ACC_FINAL;
                            mainClass = classNode;
                            break;
                        }
                    }
                }
                for (MethodNode method : classNode.methods) {
                    if (method.visibleAnnotations != null) {
                        for (AnnotationNode visibleAnnotation : new ArrayList<>(method.visibleAnnotations)) {
                            if (visibleAnnotation.desc.equals("Lnet/minecraftforge/fml/common/Mod$EventHandler;")) {
                                events.add(method);
                                method.visibleAnnotations.remove(visibleAnnotation);
                            }
                        }
                    }
                }
            }

            if (annotation == null) {
                error("Could not find Mod annotation");
                return;
            }

            ClassWrapper cw = new ClassWrapper(true);

            String superName = config.getString("class-super-name");
            if (superName.equals("random")) {
                LinkedList<ClassWrapper> compatible = new LinkedList<>();
                for (ClassWrapper lib : obf.getLibs()) {
                    if (!lib.name.startsWith("java/lang")) continue;
                    if (AsmUtils.findMethod(lib, "<clinit>", "()V") != null) continue;
                    if (Modifier.isPublic(lib.access)) {
                        for (MethodNode method : lib.methods) {
                            if (method.name.equals("<init>") && Modifier.isPublic(method.access) &&
                                    method.desc.equals("()V")) {
                                compatible.add(lib);
                            }
                        }
                    }
                }
                Collections.shuffle(compatible, random);
                superName = compatible.getFirst().name;
                log("Using super class %s", superName);
            }

            cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, config.getString("class-name"),
                    null, superName, null);
            cw.visibleAnnotations = new ArrayList<>(1);
            cw.visibleAnnotations.add(annotation);

            FieldVisitor fv = cw.visitField(ACC_PUBLIC | ACC_STATIC, "instance", "L" + mainClass.name + ";", null, null);
            fv.visitEnd();

            MethodVisitor mv;
            insertForgeStub(cw, mainClass, key);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V");
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }

            cw.visitEnd();

            byte[] packed = pack(key);
            obf.getResources().clear();
            obf.addResource(config.getString("resource-name"), packed);

            if (config.getBoolean("reobf")) {
                for (Transformer transformer : obf.getTransformers()) {
                    if (!transformer.canBeIterated) continue;
                    transformer.run(cw);
                }
            }

            for (MethodNode event : events) {

                MethodNode mn = new MethodNode(event.access, event.name, event.desc, null, null);
                mn.visitAnnotation("Lnet/minecraftforge/fml/common/Mod$EventHandler;", true).visitEnd();
                mn.visitCode();

                Type[] args = Type.getArgumentTypes(event.desc);
                if (!Modifier.isStatic(event.access)) {
                    mn.visitFieldInsn(GETSTATIC, cw.name, "instance", "L" + mainClass.name + ";");
                    mn.maxLocals = 1;
                }
                for (Type arg : args) {
                    mn.visitVarInsn(arg.getOpcode(ILOAD), mn.maxLocals);
                    mn.maxLocals += arg.getSize();
                }

                mn.visitMethodInsn(Modifier.isStatic(event.access) ? INVOKESTATIC : INVOKEVIRTUAL, mainClass.name, event.name, event.desc);

                mn.visitInsn(Type.getReturnType(event.desc).getOpcode(IRETURN));

                mn.visitEnd();
                mn.visitMaxs(mn.maxLocals, mn.maxLocals + 1);

                cw.methods.add(mn);

            }

            obf.addClass(cw);

        } else {

            Manifest manifest = obf.getManifest();
            String mainName = manifest.getMainAttributes().getValue("Main-Class").replace('.', '/');
            mainClass = obf.assureLoaded(mainName);
            if (mainClass == null) {
                error("Main class %s not found", mainName);
                return;
            }

        }
    }

    private void insertForgeStub(ClassWrapper cw, ClassWrapper mainClass, int randomKey) {
        MethodNode mv;
        {
            mv = (MethodNode) cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();

            Label label0 = new Label();
            Label label1 = new Label();
            Label label2 = new Label();
            mv.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
            mv.visitLabel(label0);
            mv.visitTypeInsn(NEW, "java/io/ByteArrayInputStream");
            mv.visitInsn(DUP);

            mv.visitLdcInsn(Type.getType("L" + cw.name + ";"));
            mv.visitLdcInsn("/" + config.getString("resource-name"));
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
            mv.visitMethodInsn(INVOKESTATIC, "org/apache/commons/io/IOUtils", "toByteArray", "(Ljava/io/InputStream;)[B", false);

            mv.visitMethodInsn(INVOKESPECIAL, "java/io/ByteArrayInputStream", "<init>", "([B)V", false);
            mv.visitVarInsn(ASTORE, 3);
            mv.visitTypeInsn(NEW, "java/io/DataInputStream");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKESPECIAL, "java/io/DataInputStream", "<init>", "(Ljava/io/InputStream;)V", false);
            mv.visitVarInsn(ASTORE, 4);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
            mv.visitInsn(POP);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
            mv.visitInsn(POP);
            Label label3 = new Label();
            mv.visitLabel(label3);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "available", "()I", false);
            Label label4 = new Label();
            mv.visitJumpInsn(IFLE, label4);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readFloat", "()F", false);
            mv.visitLdcInsn(new Float("4.0"));
            mv.visitInsn(FMUL);
            mv.visitInsn(F2I);
            mv.visitVarInsn(ISTORE, 5);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readUTF", "()Ljava/lang/String;", false);
            mv.visitVarInsn(ASTORE, 6);
            mv.visitVarInsn(ILOAD, 5);
            mv.visitIntInsn(NEWARRAY, T_CHAR);
            mv.visitVarInsn(ASTORE, 7);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ISTORE, 8);
            Label label5 = new Label();
            mv.visitLabel(label5);
            mv.visitVarInsn(ILOAD, 8);
            mv.visitVarInsn(ILOAD, 5);
            Label label6 = new Label();
            mv.visitJumpInsn(IF_ICMPGE, label6);
            mv.visitVarInsn(ALOAD, 7);
            mv.visitVarInsn(ILOAD, 8);
            mv.visitVarInsn(ALOAD, 6);
            mv.visitVarInsn(ILOAD, 8);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
            mv.instructions.add(AsmUtils.pushInt(randomKey));
            mv.visitInsn(IXOR);
            mv.visitVarInsn(ILOAD, 5);
            mv.visitInsn(IXOR);
            mv.visitInsn(I2C);
            mv.visitInsn(CASTORE);
            mv.visitVarInsn(ALOAD, 7);
            mv.visitVarInsn(ILOAD, 8);
            mv.visitInsn(DUP2);
            mv.visitInsn(CALOAD);
            mv.visitInsn(ICONST_2);
            mv.visitInsn(ISHR);
            mv.visitInsn(I2C);
            mv.visitInsn(CASTORE);
            mv.visitIincInsn(8, 1);
            mv.visitJumpInsn(GOTO, label5);
            mv.visitLabel(label6);
            mv.visitTypeInsn(NEW, "java/lang/String");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 7);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
            mv.visitVarInsn(ASTORE, 6);
            mv.visitIntInsn(BIPUSH, 16);
            mv.visitIntInsn(NEWARRAY, T_BYTE);
            mv.visitVarInsn(ASTORE, 8);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitVarInsn(ALOAD, 8);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readFully", "([B)V", false);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
            mv.visitInsn(IXOR);
            mv.visitIntInsn(NEWARRAY, T_BYTE);
            mv.visitVarInsn(ASTORE, 9);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitVarInsn(ALOAD, 9);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readFully", "([B)V", false);
            mv.visitLdcInsn("AES/ECB/PKCS5Padding");
            mv.visitMethodInsn(INVOKESTATIC, "javax/crypto/Cipher", "getInstance", "(Ljava/lang/String;)Ljavax/crypto/Cipher;", false);
            mv.visitVarInsn(ASTORE, 10);
            mv.visitTypeInsn(NEW, "javax/crypto/spec/SecretKeySpec");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 8);
            mv.visitLdcInsn("AES");
            mv.visitMethodInsn(INVOKESPECIAL, "javax/crypto/spec/SecretKeySpec", "<init>", "([BLjava/lang/String;)V", false);
            mv.visitVarInsn(ASTORE, 11);
            mv.visitVarInsn(ALOAD, 10);
            mv.visitInsn(ICONST_2);
            mv.visitVarInsn(ALOAD, 11);
            mv.visitMethodInsn(INVOKEVIRTUAL, "javax/crypto/Cipher", "init", "(ILjava/security/Key;)V", false);
            mv.visitVarInsn(ALOAD, 10);
            mv.visitVarInsn(ALOAD, 9);
            mv.visitMethodInsn(INVOKEVIRTUAL, "javax/crypto/Cipher", "doFinal", "([B)[B", false);
            mv.visitVarInsn(ASTORE, 9);

            mv.visitTypeInsn(NEW, "java/lang/Object");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitVarInsn(ASTORE, 12);

            mv.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/common/Loader", "instance", "()Lnet/minecraftforge/fml/common/Loader;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraftforge/fml/common/Loader", "getModClassLoader", "()Lnet/minecraftforge/fml/common/ModClassLoader;", false);
            mv.visitVarInsn(ASTORE, 12);

            mv.visitLdcInsn(Type.getType("Lnet/minecraftforge/fml/common/ModClassLoader;"));
            mv.visitVarInsn(ALOAD, 12);
            mv.visitTypeInsn(CHECKCAST, "net/minecraftforge/fml/common/ModClassLoader");
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitLdcInsn("mainClassLoader");
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/relauncher/ReflectionHelper", "getPrivateValue", "(Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/String;)Ljava/lang/Object;", false);
            mv.visitTypeInsn(CHECKCAST, "net/minecraft/launchwrapper/LaunchClassLoader");
            mv.visitVarInsn(ASTORE, 12);

            mv.visitLdcInsn(Type.getType("Lnet/minecraft/launchwrapper/LaunchClassLoader;"));
            mv.visitVarInsn(ALOAD, 12);
            mv.visitTypeInsn(CHECKCAST, "net/minecraft/launchwrapper/LaunchClassLoader");
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitLdcInsn("resourceCache");
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/relauncher/ReflectionHelper", "getPrivateValue", "(Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/String;)Ljava/lang/Object;", false);
            mv.visitTypeInsn(CHECKCAST, "java/util/concurrent/ConcurrentHashMap");
            mv.visitInsn(DUP);

            mv.visitVarInsn(ALOAD, 6);//name
            mv.visitVarInsn(ALOAD, 9);//bytes
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/ConcurrentHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitInsn(POP);

            mv.visitLdcInsn(Type.getType("Lnet/minecraft/launchwrapper/LaunchClassLoader;"));
            mv.visitInsn(SWAP);
            mv.visitVarInsn(ALOAD, 12);
            mv.visitTypeInsn(CHECKCAST, "net/minecraft/launchwrapper/LaunchClassLoader");
            mv.visitInsn(SWAP);
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitLdcInsn("resourceCache");
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/relauncher/ReflectionHelper", "setPrivateValue", "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;)V", false);

            mv.visitJumpInsn(GOTO, label3);
            mv.visitLabel(label4);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "close", "()V", false);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayInputStream", "close", "()V", false);
            mv.visitLabel(label1);
            Label label9 = new Label();
            mv.visitJumpInsn(GOTO, label9);
            mv.visitLabel(label2);
            mv.visitVarInsn(ASTORE, 3);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitInsn(ATHROW);
            mv.visitLabel(label9);

            mv.visitTypeInsn(NEW, mainClass.name);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, mainClass.name, "<init>", "()V");
            mv.visitFieldInsn(PUTSTATIC, cw.name, "instance", "L" + mainClass.name + ";");

            mv.visitInsn(RETURN);
            mv.visitMaxs(4, 13);

            mv.visitEnd();
        }
    }

    private void insertNormalStub(ClassWrapper cw, ClassWrapper mainClass, int randomKey) {
        MethodNode mv;
        {
            mv = (MethodNode) cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();

            Label label0 = new Label();
            Label label1 = new Label();
            Label label2 = new Label();
            mv.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
            mv.visitLabel(label0);
            mv.visitTypeInsn(NEW, "java/io/ByteArrayInputStream");
            mv.visitInsn(DUP);

            mv.visitLdcInsn(Type.getType("L" + cw.name + ";"));
            mv.visitLdcInsn("/" + config.getString("resource-name"));
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
            mv.visitMethodInsn(INVOKESTATIC, "org/apache/commons/io/IOUtils", "toByteArray", "(Ljava/io/InputStream;)[B", false);

            mv.visitMethodInsn(INVOKESPECIAL, "java/io/ByteArrayInputStream", "<init>", "([B)V", false);
            mv.visitVarInsn(ASTORE, 3);
            mv.visitTypeInsn(NEW, "java/io/DataInputStream");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKESPECIAL, "java/io/DataInputStream", "<init>", "(Ljava/io/InputStream;)V", false);
            mv.visitVarInsn(ASTORE, 4);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
            mv.visitInsn(POP);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
            mv.visitInsn(POP);
            Label label3 = new Label();
            mv.visitLabel(label3);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "available", "()I", false);
            Label label4 = new Label();
            mv.visitJumpInsn(IFLE, label4);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readFloat", "()F", false);
            mv.visitLdcInsn(new Float("4.0"));
            mv.visitInsn(FMUL);
            mv.visitInsn(F2I);
            mv.visitVarInsn(ISTORE, 5);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readUTF", "()Ljava/lang/String;", false);
            mv.visitVarInsn(ASTORE, 6);
            mv.visitVarInsn(ILOAD, 5);
            mv.visitIntInsn(NEWARRAY, T_CHAR);
            mv.visitVarInsn(ASTORE, 7);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ISTORE, 8);
            Label label5 = new Label();
            mv.visitLabel(label5);
            mv.visitVarInsn(ILOAD, 8);
            mv.visitVarInsn(ILOAD, 5);
            Label label6 = new Label();
            mv.visitJumpInsn(IF_ICMPGE, label6);
            mv.visitVarInsn(ALOAD, 7);
            mv.visitVarInsn(ILOAD, 8);
            mv.visitVarInsn(ALOAD, 6);
            mv.visitVarInsn(ILOAD, 8);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
            mv.instructions.add(AsmUtils.pushInt(randomKey));
            mv.visitInsn(IXOR);
            mv.visitVarInsn(ILOAD, 5);
            mv.visitInsn(IXOR);
            mv.visitInsn(I2C);
            mv.visitInsn(CASTORE);
            mv.visitVarInsn(ALOAD, 7);
            mv.visitVarInsn(ILOAD, 8);
            mv.visitInsn(DUP2);
            mv.visitInsn(CALOAD);
            mv.visitInsn(ICONST_2);
            mv.visitInsn(ISHR);
            mv.visitInsn(I2C);
            mv.visitInsn(CASTORE);
            mv.visitIincInsn(8, 1);
            mv.visitJumpInsn(GOTO, label5);
            mv.visitLabel(label6);
            mv.visitTypeInsn(NEW, "java/lang/String");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 7);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
            mv.visitVarInsn(ASTORE, 6);
            mv.visitIntInsn(BIPUSH, 16);
            mv.visitIntInsn(NEWARRAY, T_BYTE);
            mv.visitVarInsn(ASTORE, 8);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitVarInsn(ALOAD, 8);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readFully", "([B)V", false);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
            mv.visitInsn(IXOR);
            mv.visitIntInsn(NEWARRAY, T_BYTE);
            mv.visitVarInsn(ASTORE, 9);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitVarInsn(ALOAD, 9);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readFully", "([B)V", false);
            mv.visitLdcInsn("AES/ECB/PKCS5Padding");
            mv.visitMethodInsn(INVOKESTATIC, "javax/crypto/Cipher", "getInstance", "(Ljava/lang/String;)Ljavax/crypto/Cipher;", false);
            mv.visitVarInsn(ASTORE, 10);
            mv.visitTypeInsn(NEW, "javax/crypto/spec/SecretKeySpec");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 8);
            mv.visitLdcInsn("AES");
            mv.visitMethodInsn(INVOKESPECIAL, "javax/crypto/spec/SecretKeySpec", "<init>", "([BLjava/lang/String;)V", false);
            mv.visitVarInsn(ASTORE, 11);
            mv.visitVarInsn(ALOAD, 10);
            mv.visitInsn(ICONST_2);
            mv.visitVarInsn(ALOAD, 11);
            mv.visitMethodInsn(INVOKEVIRTUAL, "javax/crypto/Cipher", "init", "(ILjava/security/Key;)V", false);
            mv.visitVarInsn(ALOAD, 10);
            mv.visitVarInsn(ALOAD, 9);
            mv.visitMethodInsn(INVOKEVIRTUAL, "javax/crypto/Cipher", "doFinal", "([B)[B", false);
            mv.visitVarInsn(ASTORE, 9);

            mv.visitTypeInsn(NEW, "java/lang/Object");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitVarInsn(ASTORE, 12);

            mv.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/common/Loader", "instance", "()Lnet/minecraftforge/fml/common/Loader;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraftforge/fml/common/Loader", "getModClassLoader", "()Lnet/minecraftforge/fml/common/ModClassLoader;", false);
            mv.visitVarInsn(ASTORE, 12);

            mv.visitLdcInsn(Type.getType("Lnet/minecraftforge/fml/common/ModClassLoader;"));
            mv.visitVarInsn(ALOAD, 12);
            mv.visitTypeInsn(CHECKCAST, "net/minecraftforge/fml/common/ModClassLoader");
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitLdcInsn("mainClassLoader");
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/relauncher/ReflectionHelper", "getPrivateValue", "(Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/String;)Ljava/lang/Object;", false);
            mv.visitTypeInsn(CHECKCAST, "net/minecraft/launchwrapper/LaunchClassLoader");
            mv.visitVarInsn(ASTORE, 12);

            mv.visitLdcInsn(Type.getType("Lnet/minecraft/launchwrapper/LaunchClassLoader;"));
            mv.visitVarInsn(ALOAD, 12);
            mv.visitTypeInsn(CHECKCAST, "net/minecraft/launchwrapper/LaunchClassLoader");
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitLdcInsn("resourceCache");
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/relauncher/ReflectionHelper", "getPrivateValue", "(Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/String;)Ljava/lang/Object;", false);
            mv.visitTypeInsn(CHECKCAST, "java/util/concurrent/ConcurrentHashMap");
            mv.visitInsn(DUP);

            mv.visitVarInsn(ALOAD, 6);//name
            mv.visitVarInsn(ALOAD, 9);//bytes
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/ConcurrentHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitInsn(POP);

            mv.visitLdcInsn(Type.getType("Lnet/minecraft/launchwrapper/LaunchClassLoader;"));
            mv.visitInsn(SWAP);
            mv.visitVarInsn(ALOAD, 12);
            mv.visitTypeInsn(CHECKCAST, "net/minecraft/launchwrapper/LaunchClassLoader");
            mv.visitInsn(SWAP);
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitLdcInsn("resourceCache");
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/relauncher/ReflectionHelper", "setPrivateValue", "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;)V", false);

            mv.visitJumpInsn(GOTO, label3);
            mv.visitLabel(label4);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "close", "()V", false);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/ByteArrayInputStream", "close", "()V", false);
            mv.visitLabel(label1);
            Label label9 = new Label();
            mv.visitJumpInsn(GOTO, label9);
            mv.visitLabel(label2);
            mv.visitVarInsn(ASTORE, 3);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitInsn(ATHROW);
            mv.visitLabel(label9);

            mv.visitTypeInsn(NEW, mainClass.name);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, mainClass.name, "<init>", "()V");
            mv.visitFieldInsn(PUTSTATIC, cw.name, "instance", "L" + mainClass.name + ";");

            mv.visitInsn(RETURN);
            mv.visitMaxs(4, 13);

            mv.visitEnd();
        }
    }

    @Override
    public String getSection() {
        return "misc.packer";
    }

    @SneakyThrows
    public byte[] pack(int randomKey) {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        Map<String, byte[]> entries = new HashMap<>();

        for (ClassNode classNode : obf.getClasses()) {

            classNode.sourceDebug = null;
            classNode.sourceFile = null;

            ContextClassWriter writer = new ContextClassWriter(ClassWriter.COMPUTE_FRAMES);
            classNode.accept(writer);

            byte[] data = writer.toByteArray();

            entries.put(classNode.name, data);

            obf.addGeneratedClass(classNode.name, new byte[0]);

        }

        entries.putAll(obf.getResources());

        /*
         * Format:
         *  magic
         *  Object[]
         *
         * Object Format:
         *  float nameLen; // 0.25 of name length
         *  utf8 name;
         *  byte[16] key;
         *  int dataLen;
         *  int dataKey;
         *  byte[] data;
         */

        dos.writeInt(0xCAFEBABE);
        dos.writeInt(0x34);

        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {

            byte[] data = entry.getValue();

            char[] name = entry.getKey().replace("/", ".").toCharArray();

            dos.writeFloat(name.length / 4f);
            for (int i = 0; i < name.length; i++) {
                name[i] <<= 2;
                name[i] ^= randomKey ^ name.length;
            }

            for (int i = 0; i < random.nextInt(0, 1600); i++) {
                char[] name2 = new char[name.length + 1];
                System.arraycopy(name, 0, name2, 0, name.length);
                name2[name.length] = (char) random.nextInt(0, 256);
                name = name2;
            }

            dos.writeUTF(new String(name));

            byte[] keyBytes = new byte[16];
            random.nextBytes(keyBytes);

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

            data = cipher.doFinal(data);

            dos.write(keyBytes);

            int l1 = data.length;

            int random = PackerTransformer.this.random.nextInt();
            dos.writeInt(l1 ^ random);
            dos.writeInt(random);

            dos.write(data);

        }

        dos.close();
        bos.close();

        return bos.toByteArray();
    }

    @SneakyThrows
    public void unpack(byte[] bytes, int randomKey) {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bis);
        dis.readInt();
        dis.readInt();
        while (dis.available() > 0) {
            int realLength = (int) (dis.readFloat() * 4);
            String name = dis.readUTF();
            char[] cname = new char[realLength];
            for (int i = 0; i < realLength; i++) {
                cname[i] = (char) (name.charAt(i) ^ randomKey ^ realLength);
                cname[i] >>= 2;
            }
            name = new String(cname);
            byte[] keyBytes = new byte[16];
            dis.readFully(keyBytes);
            byte[] data = new byte[dis.readInt() ^ dis.readInt()];
            dis.readFully(data);

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            data = cipher.doFinal(data);
            // There we go
            if (isClass(data)) {
                System.out.println("Class: " + name);
            } else {
                System.out.println("Resource: " + name);
            }
        }

        dis.close();
        bis.close();
    }
}
