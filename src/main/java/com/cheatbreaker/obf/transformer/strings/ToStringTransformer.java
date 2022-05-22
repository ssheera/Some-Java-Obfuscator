package com.cheatbreaker.obf.transformer.strings;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.*;

import java.util.Arrays;

public class ToStringTransformer extends Transformer {
    public ToStringTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public String getSection() {
        return "strings.tostring";
    }

    @Override
    public void visit(ClassWrapper classNode) {

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode && ((LdcInsnNode) instruction).cst instanceof String) {

                    String string = (String) ((LdcInsnNode) instruction).cst;
                    ClassWrapper cn = new ClassWrapper(true);
                    cn.visit(V1_8, ACC_FINAL | ACC_SUPER, AsmUtils.parentName(classNode.name) + RandomStringUtils.randomAlphabetic(5), null, "java/lang/Object", null);

                    MethodVisitor mn = cn.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
                    mn.visitCode();

                    char[] keys = new char[string.length()];
                    char[] chars = new char[string.length()];

                    for (int i = 0; i < keys.length; i++) {
                        keys[i] = (char) (string.charAt(i) ^ Arrays.hashCode(chars));
                        chars[i] = string.charAt(i);
                    }

                    mn.visitLdcInsn(keys.length);
                    mn.visitIntInsn(NEWARRAY, T_CHAR);
                    mn.visitVarInsn(ASTORE, 1);
                    Label l = new Label();
                    mn.visitLabel(l);
                    for (int i = 0; i < keys.length; i++) {
                        mn.visitVarInsn(ALOAD, 1);
                        mn.visitInsn(DUP);
                        mn.visitLdcInsn(i);
                        mn.visitLdcInsn((int) keys[i]);
                        mn.visitVarInsn(ALOAD, 1);
                        mn.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "hashCode", "([C)I", false);
                        mn.visitInsn(I2C);
                        mn.visitInsn(I2C);
                        mn.visitInsn(IXOR);
                        mn.visitInsn(CASTORE);
                        mn.visitVarInsn(ASTORE, 1);
                    }

                    mn.visitTypeInsn(NEW, "java/lang/String");
                    mn.visitInsn(DUP);
                    mn.visitVarInsn(ALOAD, 1);
                    mn.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);

                    mn.visitInsn(ARETURN);
                    mn.visitEnd();

                    mn = cn.visitMethod(0, "<init>", "()V", null, null);
                    mn.visitCode();
                    mn.visitVarInsn(ALOAD, 0);
                    mn.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mn.visitInsn(RETURN);
                    cn.visitOuterClass(classNode.name, method.name, method.desc);
                    cn.visitEnd();
                    obf.addClass(cn);

                    classNode.innerClasses.add(new InnerClassNode(cn.name, null, null, ACC_STATIC));

                    InsnList list = new InsnList();
                    list.add(new TypeInsnNode(NEW, cn.name));
                    list.add(new InsnNode(DUP));
                    list.add(new MethodInsnNode(INVOKESPECIAL, cn.name, "<init>", "()V", false));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, cn.name, "toString", "()Ljava/lang/String;", false));

                    method.instructions.insertBefore(instruction, list);
                    method.instructions.remove(instruction);
                }
            }
        }
    }
}
