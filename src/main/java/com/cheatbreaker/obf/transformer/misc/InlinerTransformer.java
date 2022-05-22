package com.cheatbreaker.obf.transformer.misc;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import com.cheatbreaker.obf.utils.asm.NodeAccess;
import com.cheatbreaker.obf.utils.pair.ClassMethodNode;
import lombok.SneakyThrows;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.lang.reflect.Modifier;
import java.util.*;

public class InlinerTransformer extends Transformer {

    private int maxPasses = 0;
    private final boolean removal;
    private final boolean changeAccess;

    private final LinkedList<ClassMethodNode> inlinedMethods = new LinkedList<>();
    private final LinkedList<ClassMethodNode> failed = new LinkedList<>();

    @Override
    public String getSection() {
        return "misc.inliner";
    }

    public InlinerTransformer(Obf obf) {
        super(obf);
        this.maxPasses = config.getInt("maxPasses", 5);
        this.removal = config.getBoolean("remove-unused-methods", false);
        this.changeAccess = true;
    }

    private final Map<ClassMethodNode, Integer> passes = new HashMap<>();

    @Override
    protected void visit(ClassWrapper classNode) {

        passes.remove(target);

        for (MethodNode method : classNode.methods) {
            boolean change = true;
            while (change) {
                change = visitMethod(classNode, method);
            }
        }
    }

    @SneakyThrows
    public boolean visitMethod(ClassWrapper classNode, MethodNode method) {
        boolean change = false;
        ClassMethodNode cmn = new ClassMethodNode(classNode, method);
        if (target == null || target.equals(cmn)) {
            passes.put(cmn, passes.getOrDefault(cmn, 0) + 1);
            if (passes.get(cmn) > maxPasses) return false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode) {
                    if (instruction.getOpcode() != INVOKESPECIAL &&
                            instruction.getOpcode() != INVOKEINTERFACE) {
                        MethodInsnNode node = (MethodInsnNode) instruction;
                        ClassWrapper owner = obf.assureLoaded(node.owner);
                        if (owner == null) continue;
                        MethodNode target = AsmUtils.findMethodSuper(owner, node.name, node.desc);

                        if (target != null) {

                            ClassMethodNode inline = new ClassMethodNode(owner, target);

                            if (failed.contains(inline) || !canInline(classNode, owner, target, false)) {
                                if (!failed.contains(inline)) failed.add(inline);
                                continue;
                            }

                            if (AsmUtils.codeSize(target) + AsmUtils.codeSize(method) >= AsmUtils.MAX_INSTRUCTIONS)
                                continue;

                            List<TryCatchBlockNode> cachedTrys = new ArrayList<>(method.tryCatchBlocks);
                            AbstractInsnNode[] cachedInsns = method.instructions.toArray();
                            int cachedLocals = method.maxLocals;

                            try {

                                inline(target, method, node);

                                Analyzer<?> analyzer = new Analyzer<>(new BasicInterpreter());
                                analyzer.analyzeAndComputeMaxs(classNode.name, method);

                                change = true;

                                ClassMethodNode pair = new ClassMethodNode(owner, target);
                                if (!inlinedMethods.contains(pair) && owner.modify)
                                    inlinedMethods.add(pair);

                            } catch (Exception ex) {
                                // Failed to inline
                                error("Failed to inline method %s.%s%s [%s]", node.owner, node.name, node.desc, ex.getMessage());

                                method.tryCatchBlocks = cachedTrys;
                                method.instructions.clear();
                                for (AbstractInsnNode cachedInsn : cachedInsns) {
                                    method.instructions.add(cachedInsn);
                                }
                                method.maxLocals = cachedLocals;
                            }
                        }
                        //else {
                        //    error("Could not find method %s.%s%s", node.owner, node.name, node.desc);
                        //}
                    }
                }
            }
        }

        return change;
    }

    @Override
    protected void after() {

        for (ClassWrapper classNode : obf.getClasses()) {
            run(classNode);
        }

        if (!removal) return;

        LinkedList<ClassMethodNode> toRemove = new LinkedList<>(inlinedMethods);
        for (ClassWrapper classNode : obf.getClasses()) {
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode) {
                        ClassWrapper owner = obf.assureLoaded(((MethodInsnNode) instruction).owner);
                        if (owner == null) continue;
                        MethodNode target = AsmUtils.findMethodSuper(owner, ((MethodInsnNode) instruction).name, ((MethodInsnNode) instruction).desc);
                        if (target == null) continue;
                        ClassMethodNode classMethodNode = new ClassMethodNode(owner, target);
                        for (ClassMethodNode inlinedMethod : inlinedMethods) {
                            if (inlinedMethod.equals(classMethodNode)) {
                                toRemove.remove(inlinedMethod);
                                log("Removed inlined method %s.%s%s", classNode.name, method.name, method.desc);
                                break;
                            }
                        }
                    }
                }
            }
        }
        for (ClassMethodNode classMethodNode : toRemove) {
            classMethodNode.getClassWrapper().methods.remove(classMethodNode.getMethodNode());
        }
    }

    public boolean canInline(ClassWrapper ctx, ClassWrapper classNode, MethodNode method, boolean debug) {
        if (method.instructions.size() <= 0) return false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode) {
                ClassWrapper ownerClass = obf.assureLoaded(((FieldInsnNode) instruction).owner);
                if (ownerClass == null) {
                    if (debug) {
                        error("[F] Could not find class %s", ((FieldInsnNode) instruction).owner);
                    }
                    return false;
                }
                if (!canAccess(ctx, ownerClass)) {
                    if (debug) {
                        error("Class %s is not accessible from %s", ownerClass.name, ctx.name);
                    }
                    return false;
                }
                FieldNode field = AsmUtils.findFieldSuper(ownerClass, ((FieldInsnNode) instruction).name, ((FieldInsnNode) instruction).desc);
                if (field == null) {
                    if (debug) {
                        error("Could not find field %s.%s%s", ownerClass.name, ((FieldInsnNode) instruction).name, ((FieldInsnNode) instruction).desc);
                    }
                    return false;
                }
                NodeAccess access = new NodeAccess(field.access);
                if (!checkAccess(access, classNode, ownerClass)) {
                    if (debug) {
                        error("Field %s.%s%s is not accessible", ownerClass.name, ((FieldInsnNode) instruction).name, ((FieldInsnNode) instruction).desc);
                    }
                    return false;
                }
                if (!checkAccess(access, ctx, ownerClass)) {
                    if (debug) {
                        error("Field %s.%s%s is not accessible", ownerClass.name, ((FieldInsnNode) instruction).name, ((FieldInsnNode) instruction).desc);
                    }
                    return false;
                }
                field.access = access.access;
            } else if (instruction instanceof TypeInsnNode) {
                String type = ((TypeInsnNode) instruction).desc;
                if (type.startsWith("[")) {
                    if (type.substring(type.lastIndexOf('[') + 1).length() == 1) {
                        continue;
                    }
                } else {
                    if (type.length() == 1) {
                        continue;
                    }
                }
                ClassWrapper ownerClass = obf.assureLoaded(type);
                if (ownerClass == null) {
                    if (debug) {
                        error("[T] Could not find class %s", type);
                    }
                    return false;
                }
                if (!canAccess(ctx, ownerClass)) {
                    if (debug) {
                        error("Class %s is not accessible from %s", ownerClass.name, ctx.name);
                    }
                    return false;
                }
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode node = (MethodInsnNode) instruction;
                String owner = node.owner;
                String name = node.name;
                String desc = node.desc;

                if (node.getOpcode() == INVOKESPECIAL &&
                        !name.equals("<init>") && !owner.equals(ctx.name)) {
                    if (debug) {
                        error("Cannot call super %s.%s%s from %s", owner, name, desc, ctx.name);
                    }
                    return false;
                }

                ClassWrapper ownerClass = obf.assureLoaded(owner);
                if (ownerClass == null) {
                    if (debug) {
                        error("[M] Could not find class %s [%s.%s%s]", owner, owner, name, desc);
                    }
                    return false;
                }

                if (!canAccess(ctx, ownerClass)) {
                    if (debug) {
                        error("Class %s is not accessible from %s", ownerClass.name, ctx.name);
                    }
                    return false;
                }

                MethodNode methodNode = AsmUtils.findMethodSuper(ownerClass, name, desc);
                if (methodNode == null) {
                    if (debug) {
                        error("Could not find method %s.%s%s", owner, name, desc);
                    }
                    return false;
                }
                NodeAccess access = new NodeAccess(methodNode.access);
                if (!checkAccess(access, classNode, ownerClass)) {
                    if (debug) {
                        error("Method %s.%s%s is not accessible", ownerClass.name, name, desc);
                    }
                    return false;
                }
                if (!checkAccess(access, ctx, ownerClass)) {
                    if (debug) {
                        error("Method %s.%s%s is not accessible", ownerClass.name, name, desc);
                    }
                    return false;
                }
                methodNode.access = access.access;
            } else if (instruction instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode node = (InvokeDynamicInsnNode) instruction;
                if (node.bsm.getOwner().contains("LambdaMetafactory")) {
                    if (debug) {
                        error("Cannot inline LambdaMetafactory");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canAccess(ClassWrapper ctx, ClassWrapper ownerClass) {

        if (ctx.name.equals(ownerClass.name)) {
            return true;
        }

        if (Modifier.isPrivate(ownerClass.access)) {
            return false;
        }

        String pkg1 = ctx.name.contains("/") ? ctx.name.substring(0, ctx.name.lastIndexOf('/')) : "";
        String pkg2 = ownerClass.name.contains("/") ? ownerClass.name.substring(0, ownerClass.name.lastIndexOf('/')) : "";

        if (!Modifier.isPublic(ownerClass.access)) {
            if (pkg1.equals(pkg2)) {
                return true;
            }
        }

        return Modifier.isPublic(ownerClass.access);
    }

    private int fixAccess(int access) {
        if (Modifier.isProtected(access)) {
            access &= ~ACC_PROTECTED;
        } else if (Modifier.isPrivate(access)) {
            access &= ~ACC_PRIVATE;
        }
        access |= ACC_PUBLIC;
        return access;
    }

    private boolean checkAccess(NodeAccess access, ClassWrapper classNode, ClassWrapper ownerClass) {
        if (!Modifier.isPublic(access.access) && !ownerClass.modify) {
            if (!classNode.name.equals(ownerClass.name)) {
                ClassWrapper superOwnerClass = ownerClass;
                boolean found = false;
                while (true) {
                    superOwnerClass = obf.assureLoaded(superOwnerClass.superName);
                    if (superOwnerClass == null) break;
                    if (superOwnerClass.name.equals(classNode.name)) break;
                    if (superOwnerClass.name.equals(ownerClass.name)) {
                        if (Modifier.isProtected(access.access)) {
                            found = true;
                            break;
                        }
                    }
                }
                return found;
            }
        } else if (changeAccess && !Modifier.isPublic(access.access) && ownerClass.modify) {
            access.access = fixAccess(access.access);
            return checkAccess(access, classNode, ownerClass);
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

        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();

        InsnList list = new InsnList();

        int retVar = -1;
        Type retType = Type.getReturnType(toInlineMethod.desc);

//        int currentLocals = toInlineMethod.maxLocals;

        for (AbstractInsnNode insn : toInline) {
            if (insn instanceof InsnNode) {
                if (insn.getOpcode() >= IRETURN && insn.getOpcode() <= RETURN) {
                    InsnList list2 = new InsnList();

                    if (insn.getOpcode() != RETURN) {
                        if (retVar == -1) {
                            retVar = toInlineMethod.maxLocals;
                        }
                        list2.add(new VarInsnNode(retType.getOpcode(ISTORE), retVar));
                    }

                    LabelNode label3 = new LabelNode();
                    list2.add(label3);

                    list2.add(new JumpInsnNode(GOTO, end));

                    toInline.insert(insn, list2);
                    toInline.remove(insn);

                }
            }
        }

        list.add(start);

        MethodInsnNode methodInsn = (MethodInsnNode) target;
        int locals = targetMethod.maxLocals;

        Type[] args = Type.getArgumentTypes(methodInsn.desc);

        List<AbstractInsnNode> nodes = new ArrayList<>();

        if (methodInsn.getOpcode() != INVOKESTATIC) {
            nodes.add(new VarInsnNode(ASTORE, targetMethod.maxLocals++));
        }

        for (Type arg : args) {
            nodes.add(new VarInsnNode(arg.getOpcode(ISTORE), targetMethod.maxLocals));
            targetMethod.maxLocals += arg.getSize();
        }

        Collections.reverse(nodes);

        for (AbstractInsnNode node : nodes) {
            list.add(node);
        }

        for (AbstractInsnNode insn : toInline) {
            if (insn instanceof VarInsnNode) {
                VarInsnNode varInsn = (VarInsnNode) insn;
                if (varInsn.var == retVar) {
                    retVar += locals;
                }
                varInsn.var += locals;
            } else if (insn instanceof IincInsnNode) {
                IincInsnNode iincInsnNode = (IincInsnNode) insn;
                iincInsnNode.var += locals;
            }
        }

        list.add(toInline);

        list.add(end);
        if (retVar != -1) {
            list.add(new VarInsnNode(retType.getOpcode(ILOAD), retVar));
        }

        toInlineInto.insert(target, list);
        toInlineInto.remove(target);
    }
}
