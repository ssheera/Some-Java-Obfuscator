package com.cheatbreaker.obf.transformer.misc;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import com.cheatbreaker.obf.utils.pair.ClassMethodNode;
import lombok.SneakyThrows;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.lang.reflect.Modifier;
import java.util.*;

public class VariableTransformer extends Transformer {

    public VariableTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public String getSection() {
        return "misc.vars";
    }

    @SneakyThrows
    @Override
    protected void visit(ClassWrapper classNode) {
        for (MethodNode method : classNode.methods) {
            ClassMethodNode cmn = new ClassMethodNode(classNode, method);
            if (target == null || target.equals(cmn)) {
                if (method.instructions.size() == 0) continue;
                if (AsmUtils.codeSize(method) * 2 >= 50000) continue;

                Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());

                Frame<BasicValue>[] frames;

                try {
                    frames = analyzer.analyzeAndComputeMaxs(classNode.name, method);
                } catch (Exception ex) {
                    error("Failed to analyze method %s ", ex.getMessage());
                    continue;
                }

                int nonLocals = 0;
                if (!Modifier.isStatic(method.access)) nonLocals++;

                nonLocals += Arrays.stream(Type.getArgumentTypes(method.desc)).mapToInt(Type::getSize).sum();

                int amt = method.maxLocals - nonLocals;

                int arrayVar = method.maxLocals;

                Map<Integer, Integer> varMap = new HashMap<>();
                LinkedList<Integer> failedVars = new LinkedList<>();
                LinkedList<Integer> potential = new LinkedList<>();

                for (int i = 0; i < amt; i++) {
                    potential.add(i);
                }

                Collections.shuffle(potential);

                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof VarInsnNode) {
                        VarInsnNode var = (VarInsnNode) instruction;

                        if (failedVars.contains(var.var)) continue;

                        if (var.var < nonLocals)
                            continue;

                        boolean load = var.getOpcode() >= ILOAD && var.getOpcode() <= ALOAD;
                        Frame<BasicValue> frame = frames[instruction.index + (load ? 1 : 0)];
                        if (frame == null) {
                            failedVars.add(var.var);
                            continue;
                        }

                        BasicValue value;
                        Type type;

                        if (load) {
                            value = frame.getLocal(var.var);
                            type = value.getType();
                        } else {
                            value = frame.getStack(frame.getStackSize() - 1);
                            type = value.getType();
                        }

//                        if (type.getSize() > 1) {
//                            failedVars.add(var.var);
//                            continue;
//                        }
                        if (value == BasicValue.UNINITIALIZED_VALUE ||
                                type.getSort() == Type.OBJECT ||
                                type.getInternalName().equals("null") ||
                        type.getInternalName().equals("java/lang/Object")) {
                            failedVars.add(var.var);
                            continue;
                        }

                        if (!varMap.containsKey(var.var))
                            varMap.put(var.var, potential.pop());

                        InsnList list = new InsnList();
                        list.add(new VarInsnNode(ALOAD, arrayVar));

                        if (load) {
                            list.add(AsmUtils.pushInt(varMap.get(var.var)));
                            list.add(new InsnNode(AALOAD));
                            AsmUtils.unboxPrimitive(type.getDescriptor(), list);
                        } else {
                            AsmUtils.swap(type, list);
                            list.add(AsmUtils.pushInt(varMap.get(var.var)));
                            AsmUtils.swap(type, list);
                            if (var.getOpcode() != ASTORE) {
                                AsmUtils.boxPrimitive(type.getDescriptor(), list);
                            }
                            list.add(new InsnNode(AASTORE));
                        }
                        method.instructions.insertBefore(instruction, list);
                        method.instructions.remove(instruction);
                    }
                }

                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof IincInsnNode) {
                        IincInsnNode inc = (IincInsnNode) instruction;

                        if (!varMap.containsKey(inc.var))
                            continue;

                        int var = varMap.get(inc.var);
                        InsnList list = new InsnList();

                        list.add(new VarInsnNode(ALOAD, arrayVar));
                        list.add(AsmUtils.pushInt(var));
                        list.add(new InsnNode(DUP2));
                        list.add(new InsnNode(AALOAD));
                        AsmUtils.unboxPrimitive("I", list);
                        list.add(AsmUtils.pushInt(inc.incr));
                        list.add(new InsnNode(IADD));
                        AsmUtils.boxPrimitive("I", list);
                        list.add(new InsnNode(AASTORE));

                        method.instructions.insertBefore(instruction, list);
                        method.instructions.remove(instruction);
                    }
                }

                if (amt > 0) {
                    method.maxLocals++;
                    InsnList start = new InsnList();
                    start.add(AsmUtils.pushInt(amt));
                    start.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                    start.add(new VarInsnNode(ASTORE, arrayVar));
                    method.instructions.insertBefore(method.instructions.getFirst(), start);
                }
            }
        }
    }
}
