package com.cheatbreaker.obf.transformer;

import com.cheatbreaker.obf.Obf;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class MethodTransformer extends Transformer {

    public MethodTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public void visit(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode node = (MethodInsnNode) instruction;
                    if (node.getOpcode() == INVOKESPECIAL) {
                        InsnList list = new InsnList();
                        list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;"));
                        list.add(new LdcInsnNode(Type.getType("L" + node.owner.replace("." , "/") + ";")));
                        list.add(new LdcInsnNode(node.desc));
                        list.add(new LdcInsnNode(Type.getType("L" + node.owner.replace("." , "/") + ";")));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;"));
                        list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;"));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findConstructor", "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
                        boolean found = false;
                        AbstractInsnNode prev = node;
                        while (prev.getPrevious() != null) {
                            prev = prev.getPrevious();
                            if (prev.getOpcode() == DUP && prev.getPrevious() instanceof TypeInsnNode) {
                                if (prev.getPrevious().getOpcode() == NEW) {
                                    if (((TypeInsnNode) prev.getPrevious()).desc.equals(node.owner)) {
                                        method.instructions.remove(prev.getPrevious());
                                        method.instructions.insert(prev, list);
                                        method.instructions.remove(prev);
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (!found) {
                            continue;
                        }

                        list = new InsnList();

                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", node.desc
                                .replace(")V", ")Ljava/lang/Object;")));
                        list.add(new TypeInsnNode(CHECKCAST, node.owner));

                        method.instructions.insertBefore(node, list);
                        method.instructions.remove(node);
                    }
                }
            }
        }
    }
}
