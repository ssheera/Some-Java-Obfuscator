package com.cheatbreaker.obf.transformer;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.utils.AsmUtils;
import com.cheatbreaker.obf.utils.RandomUtils;
import lombok.SneakyThrows;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Field;

public class FieldTransformer extends Transformer {

    public FieldTransformer(Obf obf) {
        super(obf);
    }

    @Override
    @SneakyThrows
    public void visit(ClassNode classNode) {

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode node = (FieldInsnNode) instruction;

                    boolean get = node.getOpcode() == GETFIELD || node.getOpcode() == GETSTATIC;

                    InsnList list = new InsnList();
                    String desc = node.desc;

                    desc = desc.replace(".", "/");

                    FieldNode fieldNode = AsmUtils.findField(node.owner, node.name, node.desc);

                    if (fieldNode == null) continue;

                    Class<?> klass = Thread.currentThread().getContextClassLoader().loadClass(node.owner);
                    Field f = klass.getDeclaredField(node.name);
                    long offset;

                    switch (node.getOpcode()) {
                        case GETSTATIC:
                            list.add(new InsnNode(ACONST_NULL));
                            list.add(new LdcInsnNode(node.owner.replace("/", ".")));
                            offset = AsmUtils.getUnsafe().staticFieldOffset(f);
                            list.add(new LdcInsnNode(offset));
                            list.add(new MethodInsnNode(INVOKESTATIC,
                                    SpecialTransformer.CLASS_NAME, SpecialTransformer.FIELD_GET_NAME, SpecialTransformer.FIELD_GET_DESC));
                            break;
                        case GETFIELD:
                            list.add(new LdcInsnNode(node.owner.replace("/", ".")));
                            offset = AsmUtils.getUnsafe().objectFieldOffset(f);
                            list.add(new LdcInsnNode(offset));
                            list.add(new MethodInsnNode(INVOKESTATIC,
                                    SpecialTransformer.CLASS_NAME, SpecialTransformer.FIELD_GET_NAME, SpecialTransformer.FIELD_GET_DESC));
                            break;
                        case PUTSTATIC:
                            if ((fieldNode.access & ACC_FINAL) != 0)
                                break;
                            list.add(new InsnNode(ACONST_NULL));
                            list.add(new LdcInsnNode(node.owner.replace("/", ".")));
                            offset = AsmUtils.getUnsafe().staticFieldOffset(f);
                            list.add(new LdcInsnNode(offset));
                            list.add(new InsnNode(ICONST_1));
                            list.add(new MethodInsnNode(INVOKESTATIC,
                                    SpecialTransformer.CLASS_NAME, SpecialTransformer.FIELD_SET_NAME, SpecialTransformer.FIELD_SET_DESC));
                            break;
                        case PUTFIELD:
                            if ((fieldNode.access & ACC_FINAL) != 0)
                                break;
                            AsmUtils.boxPrimitive(desc, list);
                            list.add(new LdcInsnNode(node.owner.replace("/", ".")));
                            offset = AsmUtils.getUnsafe().objectFieldOffset(f);
                            list.add(new LdcInsnNode(offset));
                            list.add(new InsnNode(ICONST_0));
                            list.add(new MethodInsnNode(INVOKESTATIC,
                                    SpecialTransformer.CLASS_NAME, SpecialTransformer.FIELD_SET_NAME, SpecialTransformer.FIELD_SET_DESC));
                            break;
                    }

                    if (list.size() != 0) {

                        if (get) {
                            if (!(desc.startsWith("[L") && desc.endsWith(";"))) {
                                if (!desc.startsWith("L") && !desc.endsWith(";")) {
                                    if (!desc.startsWith("[")) {
                                        AsmUtils.unboxPrimitive(desc, list);
                                    } else {
                                        list.add(new TypeInsnNode(CHECKCAST, desc));
                                        list.add(new TypeInsnNode(CHECKCAST, desc));
                                    }
                                } else {
                                    list.add(new TypeInsnNode(CHECKCAST, desc.substring(1, desc.length() - 1)));
                                }
                            } else {
                                list.add(new TypeInsnNode(CHECKCAST, desc));
                            }
                        }

                        method.instructions.insertBefore(node, list);
                        method.instructions.remove(node);
                    }
                }
            }
        }
    }
}
