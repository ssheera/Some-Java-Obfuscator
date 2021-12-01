package com.cheatbreaker.obf.transformer;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.utils.AsmUtils;
import com.cheatbreaker.obf.utils.RandomUtils;
import lombok.SneakyThrows;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import sun.misc.Unsafe;

public class SpecialTransformer extends Transformer {

    public static String UNSAFE_NAME = RandomUtils.randomString(10);
    public static String FIELD_GET_NAME = RandomUtils.randomString(10);
    public static String FIELD_GET_DESC = "(Ljava/lang/Object;Ljava/lang/String;J)Ljava/lang/Object;";
    public static String FIELD_SET_NAME = RandomUtils.randomString(10);
    public static String FIELD_SET_DESC = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;JZ)V";
    public static String CLASS_NAME = RandomUtils.randomString(10);

    public SpecialTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public void visit(ClassNode classNode) {

    }

    private void visitSetBoostrap(ClassNode classNode) {
        MethodVisitor methodVisitor;
        methodVisitor = classNode.visitMethod(ACC_PUBLIC | ACC_STATIC, FIELD_SET_NAME, FIELD_SET_DESC, null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(147, label0);
        methodVisitor.visitMethodInsn(INVOKESTATIC, CLASS_NAME, UNSAFE_NAME, "()Lsun/misc/Unsafe;", false);
        methodVisitor.visitVarInsn(ASTORE, 6);
        Label label3 = new Label();
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(148, label3);
        methodVisitor.visitVarInsn(ILOAD, 5);
        Label label4 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label4);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(149, label5);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        methodVisitor.visitVarInsn(LLOAD, 3);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "putObject", "(Ljava/lang/Object;JLjava/lang/Object;)V", false);
        methodVisitor.visitJumpInsn(GOTO, label1);
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(151, label4);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(LLOAD, 3);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "putObject", "(Ljava/lang/Object;JLjava/lang/Object;)V", false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(145, label1);
        Label label6 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label6);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitVarInsn(ASTORE, 6);
        Label label7 = new Label();
        methodVisitor.visitLabel(label7);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(153, label6);
        methodVisitor.visitInsn(RETURN);
        Label label8 = new Label();
        methodVisitor.visitLabel(label8);
        methodVisitor.visitMaxs(5, 7);
        methodVisitor.visitEnd();
    }

    private void visitGetBoostrap(ClassNode classNode) {
        MethodVisitor methodVisitor;
        methodVisitor = classNode.visitMethod(ACC_PUBLIC | ACC_STATIC, FIELD_GET_NAME, FIELD_GET_DESC, null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
        Label label3 = new Label();
        Label label4 = new Label();
        methodVisitor.visitTryCatchBlock(label3, label4, label2, "java/lang/Throwable");
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(137, label0);
        methodVisitor.visitMethodInsn(INVOKESTATIC, CLASS_NAME, UNSAFE_NAME, "()Lsun/misc/Unsafe;", false);
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(138, label5);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitJumpInsn(IFNULL, label3);
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(139, label6);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(LLOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "getObject", "(Ljava/lang/Object;J)Ljava/lang/Object;", false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(141, label3);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        methodVisitor.visitVarInsn(LLOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "getObject", "(Ljava/lang/Object;J)Ljava/lang/Object;", false);
        methodVisitor.visitLabel(label4);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(135, label2);
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label7 = new Label();
        methodVisitor.visitLabel(label7);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitInsn(ATHROW);
        Label label8 = new Label();
        methodVisitor.visitLabel(label8);
        methodVisitor.visitMaxs(4, 5);
        methodVisitor.visitEnd();
    }

    @SneakyThrows
    private static Object getField(Object caller, String className, long offset) {
        Unsafe unsafe = AsmUtils.getUnsafe();
        if (caller != null) {
            return unsafe.getObject(caller, offset);
        } else {
            return unsafe.getObject(Class.forName(className), offset);
        }
    }

    @SneakyThrows
    private static void putField(Object value, Object caller, String className, long offset, boolean isStatic) {
        Unsafe unsafe = AsmUtils.getUnsafe();
        if (isStatic) {
            unsafe.putObject(Class.forName(className), offset, value);
        } else {
            unsafe.putObject(value, offset, caller);
        }
    }

    private void visitUnsafeGetter(ClassNode classNode) {
        MethodVisitor methodVisitor;
        methodVisitor = classNode.visitMethod(ACC_PUBLIC | ACC_STATIC, UNSAFE_NAME, "()Lsun/misc/Unsafe;", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(48, label0);
        methodVisitor.visitLdcInsn(Type.getType("Lsun/misc/Unsafe;"));
        methodVisitor.visitLdcInsn("theUnsafe");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        methodVisitor.visitVarInsn(ASTORE, 0);
        Label label3 = new Label();
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(49, label3);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
        Label label4 = new Label();
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(50, label4);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        methodVisitor.visitTypeInsn(CHECKCAST, "sun/misc/Unsafe");
        methodVisitor.visitLabel(label1);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(51, label2);
        methodVisitor.visitVarInsn(ASTORE, 0);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(52, label5);
        methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
        methodVisitor.visitInsn(ATHROW);
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitMaxs(3, 1);
        methodVisitor.visitEnd();
    }

    @Override
    public void after() {
        ClassNode classNode = new ClassNode();
        classNode.visit(V1_8, ACC_PUBLIC | ACC_SUPER, CLASS_NAME, null, "java/lang/Object", null);
        classNode.visitSource(null, null);
        visitUnsafeGetter(classNode);
        visitGetBoostrap(classNode);
        visitSetBoostrap(classNode);
        obf.addNewClass(classNode);
    }
}
