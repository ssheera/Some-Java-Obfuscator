package com.cheatbreaker.obf.transformer.natives;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.FileInputStream;

public class ConstantPoolTransformer extends Transformer {
    public ConstantPoolTransformer(Obf obf) {
        super(obf);
        canBeIterated = false;
    }

    @Override
    public String getSection() {
        return "natives.constantpool";
    }

    @SneakyThrows
    @Override
    protected void after() {
        if (!loadedNative) {
            File file = new File("target\\classes\\com\\cheatbreaker\\obf\\utils\\samples\\NativeHandler.class");
            byte[] b = IOUtils.toByteArray(new FileInputStream(file));
            ClassReader cr = new ClassReader(b);
            ClassWrapper cw = new ClassWrapper(false);
            cr.accept(cw, ClassReader.SKIP_DEBUG);
            cw.name = "vm/NativeHandler";
            obf.addNewClass(cw);
            loadedNative = true;
        }

        for (ClassWrapper classNode : obf.getClasses()) {
            transform(classNode);
        }

    }

    void transform(ClassWrapper classNode) {
        InsnList list = new InsnList();
        list.add(new LdcInsnNode(Type.getType("L" + classNode.name + ";")));
        list.add(new MethodInsnNode(INVOKESTATIC, "vm/NativeHandler", "decryptConstantPool", "(Ljava/lang/Class;)V", false));

        AsmUtils.getClinit(classNode).instructions.insert(list);

        int key = classNode.name.hashCode();

        for (MethodNode method : classNode.methods) {
            // Convert numbers to load constants
            for (AbstractInsnNode instruction : method.instructions) {
                if (AsmUtils.isPushInt(instruction)) {
                    method.instructions.set(instruction, new LdcInsnNode(AsmUtils.getPushedInt(instruction)));
                } else if (AsmUtils.isPushLong(instruction)) {
                    method.instructions.set(instruction, new LdcInsnNode(AsmUtils.getPushedLong(instruction)));
                } else if (instruction.getOpcode() >= FCONST_0 && instruction.getOpcode() <= FCONST_2) {
                    method.instructions.set(instruction, new LdcInsnNode(instruction.getOpcode() - 11.f));
                } else if (instruction.getOpcode() >= DCONST_0 && instruction.getOpcode() <= DCONST_1) {
                    method.instructions.set(instruction, new LdcInsnNode(instruction.getOpcode() - 14.d));
                }
            }
        }

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode) {
                    if (AsmUtils.isPushInt(instruction)) {
                        int value = AsmUtils.getPushedInt(instruction);
                        method.instructions.set(instruction, new LdcInsnNode(value ^ key));
                    } else if (AsmUtils.isPushLong(instruction)) {
                        long value = AsmUtils.getPushedLong(instruction);
                        method.instructions.set(instruction, new LdcInsnNode(value ^ ((long) key << 4)));
                    } else {
                        Object value = ((LdcInsnNode) instruction).cst;
                        if (value instanceof Float) {
                            float f = (float) value;
                            method.instructions.set(instruction, new LdcInsnNode(Math.pow(f, 3)));
                        } else if (value instanceof Double) {
                            double d = (double) value;
                            method.instructions.set(instruction, new LdcInsnNode(Math.pow(d, 3)));
                        }
                    }
                }
            }
        }
    }
}
