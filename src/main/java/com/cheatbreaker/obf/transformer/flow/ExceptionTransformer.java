package com.cheatbreaker.obf.transformer.flow;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import com.cheatbreaker.obf.utils.pair.ClassMethodNode;
import org.apache.commons.lang3.RandomStringUtils;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.ArrayList;

public class ExceptionTransformer extends Transformer {

    public static String handlerName;

    public ExceptionTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public String getSection() {
        return "flow.exceptions";
    }

    private void visitMethod(ClassWrapper classNode, MethodNode method) {
        ClassMethodNode cmn = new ClassMethodNode(classNode, method);
        if (target == null || target.equals(cmn)) {

            int amt = 0;

            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof VarInsnNode) {
                    if (instruction.getOpcode() >= ISTORE && instruction.getOpcode() <= ASTORE) {
                        amt++;
                    }
                }
            }

            if (AsmUtils.codeSize(method) + (amt * 15) >= AsmUtils.MAX_INSTRUCTIONS) return;

            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());

            Frame<BasicValue>[] frames;

            try {
                frames = analyzer.analyzeAndComputeMaxs(classNode.name, method);
            } catch (Exception ex) {
                error("Failed to analyze method %s ", ex.getMessage());
                return;
            }

            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof VarInsnNode) {

                    VarInsnNode var = (VarInsnNode) instruction;

                    if (instruction.getOpcode() >= ISTORE && instruction.getOpcode() <= ASTORE) {

                        Frame<BasicValue> frame = frames[instruction.index];
                        if (frame == null || frame.getStackSize() > 1)
                            continue;
                        Type type = frame.getStack(frame.getStackSize() - 1).getType();

                        LabelNode start = new LabelNode();
                        LabelNode end = new LabelNode();
                        LabelNode handler = new LabelNode();
                        LabelNode finish = new LabelNode();

                        TryCatchBlockNode tryCatch = new TryCatchBlockNode(start, end, handler, handlerName);

                        if (type == null) {
                            continue;
                        }
                        if (type.getSize() > 1) {
                            continue;
                        }
                        if (type.getSort() == Type.OBJECT || type.getInternalName().equals("null") || type.getInternalName().equals("java/lang/Object")) {
                            continue;
                        }

                        InsnList list = new InsnList();
                        list.add(start);
                        AsmUtils.boxPrimitive(type.getDescriptor(), list);
                        list.add(new TypeInsnNode(NEW, handlerName));
                        list.add(new InsnNode(SWAP));
                        list.add(new InsnNode(DUP2));
                        list.add(new InsnNode(POP));
                        list.add(new InsnNode(SWAP));
                        list.add(new MethodInsnNode(INVOKESPECIAL, handlerName, "<init>", "(Ljava/lang/Object;)V", false));
                        list.add(new InsnNode(ATHROW));
                        list.add(new JumpInsnNode(GOTO, start));
                        list.add(handler);
                        list.add(new FieldInsnNode(GETFIELD, handlerName, "o", "Ljava/lang/Object;"));
                        AsmUtils.unboxPrimitive(type.getDescriptor(), list);
                        list.add(end);
                        list.add(new JumpInsnNode(GOTO, finish));
                        list.add(finish);
                        list.add(instruction.clone(null));

                        method.instructions.insert(instruction, list);
                        method.instructions.remove(instruction);

                        method.tryCatchBlocks.add(tryCatch);

                    }
                }
            }
        }
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
                        handlerClass.visit(V1_8, ACC_PUBLIC, handlerName, null, "java/lang/Throwable", null);
                        FieldVisitor fv = handlerClass.visitField(ACC_PUBLIC, "o", "Ljava/lang/Object;", null, null);
                        fv.visitEnd();
                        MethodVisitor mv = handlerClass.visitMethod(ACC_PUBLIC,
                                "<init>", "(Ljava/lang/Object;)V", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitInsn(DUP);
                        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Throwable", "<init>", "()V", false);
                        mv.visitInsn(SWAP);
                        mv.visitFieldInsn(PUTFIELD, handlerName, "o", "Ljava/lang/Object;");
                        mv.visitInsn(RETURN);
                        mv.visitMaxs(2, 2);
                        mv.visitEnd();
                        obf.getClasses().add(handlerClass);
                    }
                }
            }
        }

        for (MethodNode method : classNode.methods) {

            visitMethod(classNode, method);

//            List<TryCatchBlockNode> cachedTrys = new ArrayList<>(method.tryCatchBlocks);
//            AbstractInsnNode[] cachedInsns = method.instructions.toArray();

//            try {
//
//                visitMethod(classNode, method);
//
//                Analyzer<?> analyzer = new Analyzer<>(new BasicInterpreter());
//                analyzer.analyzeAndComputeMaxs(classNode.name, method);
//
//            } catch (Exception ex) {
//                // Failed to inline
//                error("Failed to obfuscate method %s.%s%s [%s]", classNode.name, method.name, method.desc, ex.getMessage());
////
//                method.tryCatchBlocks = cachedTrys;
//                method.instructions.clear();
//                for (AbstractInsnNode cachedInsn : cachedInsns) {
//                    method.instructions.add(cachedInsn);
//                }
//            }
        }
    }
}
