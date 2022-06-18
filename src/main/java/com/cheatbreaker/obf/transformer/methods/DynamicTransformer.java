package com.cheatbreaker.obf.transformer.methods;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import com.cheatbreaker.obf.utils.asm.NodeAccess;
import com.cheatbreaker.obf.utils.tree.HierarchyUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;

// Was testing something :)
public class DynamicTransformer extends Transformer {

    private static String handlerName;

    public DynamicTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public String getSection() {
        return "methods.dynamic";
    }

    @Override
    protected void visit(ClassWrapper classNode) {

        while (handlerName == null) {
            for (ClassWrapper cn : new ArrayList<>(obf.getClasses())) {
                if (nextBoolean(5)) {
                    handlerName = AsmUtils.parentName(cn.name) + RandomStringUtils.randomAlphabetic(3);
                    if (obf.assureLoaded(handlerName) != null) {
                        handlerName = null;
                    } else {
                        ClassWrapper handlerClass = new ClassWrapper(false);
                        handlerClass.visit(V1_8, ACC_PUBLIC, handlerName, null, "java/lang/Object", null);

                        MethodVisitor methodVisitor = handlerClass.visitMethod(ACC_PUBLIC,
                                "<init>", "()V", null, null);
                        methodVisitor.visitCode();
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
                        methodVisitor.visitInsn(RETURN);
                        methodVisitor.visitEnd();

                        methodVisitor = handlerClass.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_VARARGS,
                                "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
                        methodVisitor.visitCode();
                        Label label0 = new Label();
                        Label label1 = new Label();
                        Label label2 = new Label();
                        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
                        methodVisitor.visitLabel(label0);
                        methodVisitor.visitLineNumber(15, label0);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_3);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_3);
                        methodVisitor.visitInsn(AALOAD);
                        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/invoke/MethodHandle");
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", "()Ljava/lang/String;", false);
                        methodVisitor.visitInsn(AASTORE);
                        Label label3 = new Label();
                        methodVisitor.visitLabel(label3);
                        methodVisitor.visitLineNumber(16, label3);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_2);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_2);
                        methodVisitor.visitInsn(AALOAD);
                        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/invoke/MethodHandle");
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", "()Ljava/lang/String;", false);
                        methodVisitor.visitInsn(AASTORE);
                        Label label4 = new Label();
                        methodVisitor.visitLabel(label4);
                        methodVisitor.visitLineNumber(17, label4);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_4);
                        methodVisitor.visitInsn(AALOAD);
                        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/invoke/MethodHandle");
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_5);
                        methodVisitor.visitInsn(AALOAD);
                        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/invoke/MethodHandle");
                        methodVisitor.visitInsn(ICONST_4);
                        methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                        methodVisitor.visitInsn(DUP);
                        methodVisitor.visitInsn(ICONST_0);
                        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
                        methodVisitor.visitInsn(AASTORE);
                        methodVisitor.visitInsn(DUP);
                        methodVisitor.visitInsn(ICONST_1);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_1);
                        methodVisitor.visitInsn(AALOAD);
                        methodVisitor.visitInsn(AASTORE);
                        methodVisitor.visitInsn(DUP);
                        methodVisitor.visitInsn(ICONST_2);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_2);
                        methodVisitor.visitInsn(AALOAD);
                        methodVisitor.visitInsn(AASTORE);
                        methodVisitor.visitInsn(DUP);
                        methodVisitor.visitInsn(ICONST_3);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_3);
                        methodVisitor.visitInsn(AALOAD);
                        Label label5 = new Label();
                        methodVisitor.visitLabel(label5);
                        methodVisitor.visitLineNumber(18, label5);
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_1);
                        methodVisitor.visitInsn(AALOAD);
                        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Class");
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
                        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
                        methodVisitor.visitInsn(AASTORE);
                        Label label6 = new Label();
                        methodVisitor.visitLabel(label6);
                        methodVisitor.visitLineNumber(17, label6);
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false);
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false);
                        methodVisitor.visitVarInsn(ALOAD, 0);
                        methodVisitor.visitInsn(ICONST_0);
                        methodVisitor.visitInsn(AALOAD);
                        Label label7 = new Label();
                        methodVisitor.visitLabel(label7);
                        methodVisitor.visitLineNumber(18, label7);
                        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                        methodVisitor.visitLabel(label1);
                        methodVisitor.visitLineNumber(17, label1);
                        methodVisitor.visitInsn(ARETURN);
                        methodVisitor.visitLabel(label2);
                        methodVisitor.visitLineNumber(12, label2);
                        methodVisitor.visitVarInsn(ASTORE, 1);
                        Label label8 = new Label();
                        methodVisitor.visitLabel(label8);
                        methodVisitor.visitVarInsn(ALOAD, 1);
                        methodVisitor.visitInsn(ATHROW);
                        Label label9 = new Label();
                        methodVisitor.visitLabel(label9);
                        methodVisitor.visitEnd();

                        obf.getClasses().add(handlerClass);
                    }
                }
            }
        }

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode node = (MethodInsnNode) instruction;

                    ClassWrapper owner = obf.assureLoaded(node.owner);
                    if (owner == null) continue;

                    NodeAccess access = new NodeAccess(owner.access);
                    if (!checkAccess(access, obf.assureLoaded(handlerName), owner)) continue;

                    MethodNode target = AsmUtils.findMethodSuper(owner, node.name, node.desc);
                    if (target == null) continue;

                    access = new NodeAccess(target.access);
                    if (!checkAccess(access, obf.assureLoaded(handlerName), owner)) continue;
                    target.access = access.access;

                    if (node.getOpcode() == INVOKESTATIC) {
                        InsnList list = new InsnList();
                        Type[] args = Type.getArgumentTypes(node.desc);
                        InsnList stack = storeStack(false, args);
                        if (stack == null) continue;
                        list.add(stack);

                        list.add(AsmUtils.pushInt(6));
                        list.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                        list.add(new InsnNode(DUP_X1));
                        list.add(new InsnNode(SWAP));
                        list.add(AsmUtils.pushInt(0));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(new LdcInsnNode(Type.getType("L" + node.owner + ";")));
                        list.add(AsmUtils.pushInt(1));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(methodHandle(H_INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false));
                        list.add(new LdcInsnNode(node.name));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false));
                        list.add(AsmUtils.pushInt(2));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(methodHandle(H_INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false));
                        list.add(new LdcInsnNode(node.desc));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false));
                        list.add(AsmUtils.pushInt(3));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(methodHandle(H_INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false));
                        list.add(AsmUtils.pushInt(4));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(methodHandle(H_INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false));
                        list.add(AsmUtils.pushInt(5));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new MethodInsnNode(INVOKESTATIC, handlerName, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;"));
                        Type returnType = Type.getReturnType(node.desc);
                        if (returnType.equals(Type.VOID_TYPE)) {
                            list.add(new InsnNode(POP));
                        } else {
                            AsmUtils.unboxPrimitive(returnType.getDescriptor(), list);
                        }

                        method.instructions.insert(instruction, list);
                        method.instructions.remove(instruction);
                    }
                    else if (node.getOpcode() == INVOKEVIRTUAL ||
                    node.getOpcode() == INVOKEINTERFACE) {
                        InsnList list = new InsnList();
                        Type[] args = Type.getArgumentTypes(node.desc);

                        InsnList stack = storeStack(true, args);
                        if (stack == null) continue;
                        list.add(stack);

                        list.add(AsmUtils.pushInt(6));
                        list.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                        list.add(new InsnNode(DUP_X1));
                        list.add(new InsnNode(SWAP));
                        list.add(AsmUtils.pushInt(0));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(new LdcInsnNode(Type.getType("L" + node.owner + ";")));
                        list.add(AsmUtils.pushInt(1));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(methodHandle(H_INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false));
                        list.add(new LdcInsnNode(node.name));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false));
                        list.add(AsmUtils.pushInt(2));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(methodHandle(H_INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false));
                        list.add(new LdcInsnNode(node.desc));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "bindTo", "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false));
                        list.add(AsmUtils.pushInt(3));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(methodHandle(H_INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false));
                        list.add(AsmUtils.pushInt(4));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new InsnNode(DUP));
                        list.add(methodHandle(H_INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false));
                        list.add(AsmUtils.pushInt(5));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(AASTORE));

                        list.add(new MethodInsnNode(INVOKESTATIC, handlerName, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;"));
                        Type returnType = Type.getReturnType(node.desc);
                        if (returnType.equals(Type.VOID_TYPE)) {
                            list.add(new InsnNode(POP));
                        } else {
                            AsmUtils.unboxPrimitive(returnType.getDescriptor(), list);
                        }

                        method.instructions.insert(instruction, list);
                        method.instructions.remove(instruction);
                    }
                }
            }
        }

    }

    private InsnList methodHandle(int opcode, String owner, String name, String desc, boolean itf) {
        InsnList list = new InsnList();
        list.add(new LdcInsnNode(new Handle(opcode, owner, name, desc, itf)));
        return list;
    }

    private boolean checkAccess(NodeAccess access, ClassWrapper classNode, ClassWrapper ownerClass) {

        int acc = access.access;
        if (Modifier.isPublic(acc))
            return true;

        if (!Modifier.isPublic(acc) && classNode.name.equals(ownerClass.name))
            return true;

        if (Modifier.isProtected(acc)) {
            String parent = ownerClass.name;
            if (HierarchyUtils.isAssignableFrom(parent, classNode.name)) {
                return true;
            }
        }

        if (!Modifier.isPrivate(acc) && (ACC_SYNTHETIC & acc) == 0) {
            String pkg1 = AsmUtils.parentName(classNode.name);
            String pkg2 = AsmUtils.parentName(ownerClass.name);
            return pkg1.equals(pkg2);
        }

        return false;
    }

    private InsnList storeStack(boolean virtual, Type[] types) {
        InsnList list = new InsnList();

        Type[] args = new Type[types.length + (virtual ? 1 : 0)];

        for (Type type : types) {
            if (type.getDescriptor().startsWith("[")) {
                String actual = type.getDescriptor().substring(type.getDescriptor().lastIndexOf('[') + 1);
                if (actual.equals("Ljava/lang/Object;")) {
                    return null;
                }
            }
        }

        System.arraycopy(types, 0, args, virtual ? 1 : 0, types.length);

        if (virtual) {
            args[0] = Type.getType("Ljava/lang/Object;");
        }

        list.add(AsmUtils.pushInt(args.length));
        list.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));

        for (int i = args.length - 1; i >= 0; i--) {
            Type arg = args[i];
            InsnList sub = new InsnList();
            if (arg.getSize() > 1) {
                sub.add(new InsnNode(DUP_X2));
                sub.add(new InsnNode(DUP_X2));
                sub.add(new InsnNode(POP));
                sub.add(AsmUtils.pushInt(i));
                sub.add(new InsnNode(DUP_X2));
                sub.add(new InsnNode(POP));
            } else {
                sub.add(new InsnNode(DUP_X1));
                sub.add(new InsnNode(SWAP));
                sub.add(AsmUtils.pushInt(i));
                sub.add(new InsnNode(SWAP));
            }
            AsmUtils.boxPrimitive(arg.getDescriptor(), sub);
            sub.add(new InsnNode(AASTORE));
            list.add(sub);
        }

        return list;
    }
}
