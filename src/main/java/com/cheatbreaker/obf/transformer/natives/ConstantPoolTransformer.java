package com.cheatbreaker.obf.transformer.natives;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import lombok.SneakyThrows;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

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

        for (ClassWrapper classNode : obf.getClasses()) {
            if (classNode.name.equals("vm/NativeHandler")) continue;
            transform(classNode);
        }

    }

    void transform(ClassWrapper classNode) {
        InsnList list = new InsnList();
        list.add(new LdcInsnNode(Type.getType("L" + classNode.name + ";")));
        list.add(new MethodInsnNode(INVOKESTATIC, "vm/NativeHandler", "decryptConstantPool", "(Ljava/lang/Class;)V", false));

        AsmUtils.getClinit(classNode).instructions.insert(list);

        int key = classNode.name.hashCode();

        for (int i = 0; i < 2; i++) {
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (AsmUtils.isPushInt(instruction)) {
                        int value = AsmUtils.getPushedInt(instruction);
                        InsnList list2 = new InsnList();
                        int r1 = random.nextInt();
                        list2.add(new LdcInsnNode(r1));
                        list2.add(new LdcInsnNode(value ^ r1));
                        list2.add(new InsnNode(IXOR));
                        method.instructions.insert(instruction, list2);
                        method.instructions.remove(instruction);
                    } else if (AsmUtils.isPushLong(instruction)) {
                        long value = AsmUtils.getPushedLong(instruction);
                        InsnList list2 = new InsnList();
                        long r1 = random.nextLong();
                        list2.add(new LdcInsnNode(r1));
                        list2.add(new LdcInsnNode(value ^ r1));
                        list2.add(new InsnNode(LXOR));
                        method.instructions.insert(instruction, list2);
                        method.instructions.remove(instruction);
                    }
                }
            }
        }

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (AsmUtils.isPushInt(instruction)) {
                    int value = AsmUtils.getPushedInt(instruction);
                    value -= (key << 2);
                    value ^= key;
                    method.instructions.set(instruction, new LdcInsnNode(value));
                } else if (AsmUtils.isPushLong(instruction)) {
                    long value = AsmUtils.getPushedLong(instruction);
                    value ^= ((long) key << 4);
                    value -= key;
                    method.instructions.set(instruction, new LdcInsnNode(value));
                } else if (instruction instanceof LdcInsnNode) {
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
