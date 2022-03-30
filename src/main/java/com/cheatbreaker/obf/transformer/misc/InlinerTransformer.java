package com.cheatbreaker.obf.transformer.misc;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.pair.ClassMethodNode;
import lombok.SneakyThrows;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.lang.reflect.Modifier;
import java.util.*;

public class InlinerTransformer extends Transformer {

    public LinkedList<ClassMethodNode> inlinedMethods = new LinkedList<>();

    private int maxPasses = 0;

    @Override
    public String getSection() {
        return "misc.inliner";
    }

    public InlinerTransformer(Obf obf) {
        super(obf);
        this.maxPasses = config.getInt("maxPasses", 5);
    }

    private final Map<ClassMethodNode, Integer> passes = new HashMap<>();

    @Override
    public void visit(ClassNode classNode) {

        while (true) {
            boolean change = false;

            for (MethodNode method : classNode.methods) {
                change = change || visitMethod(classNode, method);
            }

            if (!change) break;
        }
    }

    @SneakyThrows
    public boolean visitMethod(ClassNode classNode, MethodNode method) {
        boolean change = false;
        if (target == null || target.equals(new ClassMethodNode(classNode, method))) {
            ClassMethodNode cmn = new ClassMethodNode(classNode, method);
            if (excluded.contains(cmn.toString())) return false;
            passes.put(cmn, passes.getOrDefault(cmn, 0) + 1);
            if (passes.get(cmn) > maxPasses) return false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode) {
                    if (instruction.getOpcode() != INVOKESPECIAL &&
                            instruction.getOpcode() != INVOKEINTERFACE) {
                        MethodInsnNode node = (MethodInsnNode) instruction;
                        ClassNode owner = obf.assureLoaded(node.owner);
                        if (owner == null) continue;
                        MethodNode target = AsmUtils.findMethod(owner, node.name, node.desc);
                        if (target != null && canInline(classNode, target)) {
                            if (AsmUtils.codeSize(target) + AsmUtils.codeSize(method) >= AsmUtils.MAX_INSTRUCTIONS)
                                continue;

                            List<TryCatchBlockNode> cachedTrys = new ArrayList<>(method.tryCatchBlocks);
                            AbstractInsnNode[] cachedInsns = method.instructions.toArray();

                            try {
                                inline(target, method, node);
                                Analyzer<?> analyzer = new Analyzer<>(new BasicInterpreter());
                                analyzer.analyzeAndComputeMaxs(classNode.name, method);
                                change = true;
                            } catch (Exception ex) {
                                // Failed to inline
                                error("Failed to inline method %s.%s%s", node.owner, node.name, node.desc);
                                method.tryCatchBlocks = cachedTrys;
                                method.instructions.clear();
                                for (AbstractInsnNode cachedInsn : cachedInsns) {
                                    method.instructions.add(cachedInsn);
                                }
                            }

//                            ClassMethodNode pair = new ClassMethodNode(owner, target);
//                            if (!inlinedMethods.contains(pair) && obf.getClasses().contains(owner))
//                                inlinedMethods.add(pair);
                        }
                    }
                }
            }
        }

        return change;
    }

//    @Override
//    protected void after() {
//        LinkedList<ClassMethodNode> toRemove = new LinkedList<>(inlinedMethods);
//        for (ClassNode classNode : obf.getClasses()) {
//            for (MethodNode method : classNode.methods) {
//                for (AbstractInsnNode instruction : method.instructions) {
//                    if (instruction instanceof MethodInsnNode) {
//                        ClassNode owner = obf.assureLoaded(((MethodInsnNode) instruction).owner);
//                        if (owner == null) continue;
//                        MethodNode target = AsmUtils.findMethod(owner, ((MethodInsnNode) instruction).name, ((MethodInsnNode) instruction).desc);
//                        if (target == null) continue;
//                        ClassMethodNode classMethodNode = new ClassMethodNode(owner, target);
//                        for (ClassMethodNode inlinedMethod : inlinedMethods) {
//                            if (inlinedMethod.equals(classMethodNode)) {
//                                toRemove.remove(inlinedMethod);
//                                break;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        for (ClassMethodNode classMethodNode : toRemove) {
//            classMethodNode.getClassNode().methods.remove(classMethodNode.getMethodNode());
//        }
//    }

    public boolean canInline(ClassNode classNode, MethodNode method) {
        if (method.instructions.size() <= 0) return false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode) {
                ClassNode ownerClass = obf.assureLoaded(((FieldInsnNode) instruction).owner);
                if (ownerClass == null) return false;
                if (!Modifier.isPublic(ownerClass.access)) return false;
                FieldNode field = AsmUtils.findField(ownerClass, ((FieldInsnNode) instruction).name, ((FieldInsnNode) instruction).desc);
                if (field == null) return false;
                if (!checkAccess(field.access, classNode, ownerClass)) return false;
            } else if (instruction instanceof TypeInsnNode) {
                String type = ((TypeInsnNode) instruction).desc;
                ClassNode ownerClass = obf.assureLoaded(type);
                if (ownerClass == null) return false;
                if (!Modifier.isPublic(ownerClass.access)) return false;
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode node = (MethodInsnNode) instruction;
                String owner = node.owner;
                String name = node.name;
                String desc = node.desc;
                ClassNode ownerClass = obf.assureLoaded(owner);
                if (ownerClass == null) return false;
                if (!Modifier.isPublic(ownerClass.access)) return false;
                MethodNode methodNode = AsmUtils.findMethod(ownerClass, name, desc);
                if (methodNode == null) return false;
                if (!checkAccess(methodNode.access, classNode, ownerClass)) return false;
            }
        }
        return true;
    }

    private boolean checkAccess(int access, ClassNode classNode, ClassNode ownerClass) {
        if (!Modifier.isPublic(access)) {
            if (!classNode.name.equals(ownerClass.name)) {
                ClassNode superOwnerClass = ownerClass;
                boolean found = false;
                while (true) {
                    superOwnerClass = obf.assureLoaded(superOwnerClass.superName);
                    if (superOwnerClass == null) break;
                    if (superOwnerClass.name.equals(classNode.name)) break;
                    if (superOwnerClass.name.equals(ownerClass.name)) {
                        if (Modifier.isProtected(access)) {
                            found = true;
                            break;
                        }
                    }
                }
                return found;
            }
        }
        return true;
    }

    public void inline(MethodNode toInlineMethod, MethodNode targetMethod, AbstractInsnNode target) {

        InsnList toInline = toInlineMethod.instructions;
        InsnList toInlineInto = targetMethod.instructions;

        {

            Map<LabelNode, LabelNode> labels = new HashMap<>();
            for (AbstractInsnNode abstractInsnNode : toInline) {
                if (abstractInsnNode instanceof LabelNode) {
                    LabelNode labelNode = (LabelNode) abstractInsnNode;
                    labels.put(labelNode, new LabelNode());
                }
            }

            for (TryCatchBlockNode tryCatchBlock : toInlineMethod.tryCatchBlocks) {
                TryCatchBlockNode cloned = new TryCatchBlockNode(labels.get(tryCatchBlock.start),
                        labels.get(tryCatchBlock.end), labels.get(tryCatchBlock.handler), tryCatchBlock.type);
                targetMethod.tryCatchBlocks.add(cloned);
            }

            InsnList list = new InsnList();
            for (AbstractInsnNode abstractInsnNode : toInline) {
                list.add(abstractInsnNode.clone(labels));
            }
            toInline = list;
        }

        LabelNode end = new LabelNode();

        InsnList list = new InsnList();

        for (AbstractInsnNode insn : toInline) {
            if (insn instanceof InsnNode) {
                if (insn.getOpcode() >= IRETURN && insn.getOpcode() <= RETURN) {
                    toInline.set(insn, new JumpInsnNode(GOTO, end));
                }
            }
        }

        MethodInsnNode methodInsn = (MethodInsnNode) target;
        int locals = targetMethod.maxLocals;

        Type[] args = Type.getArgumentTypes(methodInsn.desc);

        List<AbstractInsnNode> nodes = new ArrayList<>();

        for (Type arg : args) {
            nodes.add(new VarInsnNode(arg.getOpcode(ISTORE), targetMethod.maxLocals));
            targetMethod.maxLocals += arg.getSize();
        }

        if (methodInsn.getOpcode() != INVOKESTATIC) {
            nodes.add(new VarInsnNode(ASTORE, targetMethod.maxLocals++));
        }

        Collections.reverse(nodes);

        for (AbstractInsnNode node : nodes) {
            list.add(node);
        }

        for (AbstractInsnNode insn : toInline) {
            if (insn instanceof VarInsnNode) {
                VarInsnNode varInsn = (VarInsnNode) insn;
                varInsn.var += locals;
            } else if (insn instanceof IincInsnNode) {
                IincInsnNode iincInsnNode = (IincInsnNode) insn;
                iincInsnNode.var += locals;
            }
        }

        list.add(toInline);

        list.add(end);

        toInlineInto.insert(target, list);
        toInlineInto.remove(target);
    }
}
