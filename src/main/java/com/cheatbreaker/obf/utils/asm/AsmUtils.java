/**
 * The MIT License (MIT)
 *
 * Copyright (C) 2018 CheatBreaker, LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cheatbreaker.obf.utils.asm;

import com.cheatbreaker.obf.Obf;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.CodeSizeEvaluator;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AsmUtils implements Opcodes{

    public static final int MAX_INSTRUCTIONS = 0xFFFF;

    public static boolean isPushInt(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        return (op >= ICONST_M1 && op <= ICONST_5)
                || op == BIPUSH
                || op == SIPUSH
                || (op == LDC && ((LdcInsnNode) insn).cst instanceof Integer);
    }

    public static int getPushedInt(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        if (op >= ICONST_M1 && op <= ICONST_5) {
            return op - ICONST_0;
        }
        if (op == BIPUSH || op == SIPUSH) {
            return ((IntInsnNode) insn).operand;
        }
        if (op == LDC) {
            Object cst = ((LdcInsnNode) insn).cst;
            if (cst instanceof Integer) {
                return (int) cst;
            }
        }
        throw new IllegalArgumentException("insn is not a push int instruction");
    }

    public static MethodNode getClinit(ClassWrapper classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("<clinit>")) {
                return method;
            }
        }
        MethodNode clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.instructions = new InsnList();
        clinit.instructions.add(new InsnNode(RETURN));
        classNode.methods.add(clinit);
        return clinit;
    }

    public static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(ICONST_0 + value);
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(BIPUSH, value);
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(SIPUSH, value);
        }
        return new LdcInsnNode(value);
    }

    public static boolean isPushLong(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        return op == LCONST_0
                || op == LCONST_1
                || (op == LDC && ((LdcInsnNode) insn).cst instanceof Long);
    }

    public static long getPushedLong(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        if (op == LCONST_0) {
            return 0;
        }
        if (op == LCONST_1) {
            return 1;
        }
        if (op == LDC) {
            Object cst = ((LdcInsnNode) insn).cst;
            if (cst instanceof Long) {
                return (long) cst;
            }
        }
        throw new IllegalArgumentException("insn is not a push long instruction");
    }

    public static AbstractInsnNode pushLong(long value) {
        if (value == 0) {
            return new InsnNode(LCONST_0);
        }
        if (value == 1) {
            return new InsnNode(LCONST_1);
        }
        return new LdcInsnNode(value);
    }

    public static int codeSize(MethodNode methodNode) {
        CodeSizeEvaluator evaluator = new CodeSizeEvaluator(null);
        methodNode.accept(evaluator);
        return evaluator.getMaxSize();
    }
    
    public static void unboxPrimitive(String desc, InsnList list) {
        switch (desc) {
            case "I":
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Integer"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
                break;
            case "Z":
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Boolean"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                break;
            case "B":
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Byte"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
                break;
            case "C":
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Character"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
                break;
            case "S":
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Short"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
                break;
            case "J":
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Long"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
                break;
            case "F":
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Float"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
                break;
            case "D":
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Double"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
                break;
        }
    }

    public static void boxPrimitive(String desc, InsnList list) {
        switch (desc) {
            case "I":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                break;
            case "Z":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
                break;
            case "B":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                break;
            case "C":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                break;
            case "S":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                break;
            case "J":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                break;
            case "F":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                break;
            case "D":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                break;
            default:
                list.add(new TypeInsnNode(CHECKCAST, desc));
                break;
        }
    }

    public static void boxPrimitive(String desc, List<AbstractInsnNode> list) {
        switch (desc) {
            case "I":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                break;
            case "Z":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
                break;
            case "B":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                break;
            case "C":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                break;
            case "S":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                break;
            case "J":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                break;
            case "F":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                break;
            case "D":
                list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                break;
            default:
                list.add(new TypeInsnNode(CHECKCAST, desc));
                break;
        }
    }

    private static final Printer printer = new Textifier();
    private static final TraceMethodVisitor methodPrinter = new TraceMethodVisitor(printer);
    public static String print(AbstractInsnNode insnNode) {
        if (insnNode == null) return "null";
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }
    
    public static FieldNode findField(Obf obf, String owner, String name, String desc) {
        ClassWrapper classNode = obf.assureLoaded(owner);
        if (classNode == null) return null;
        return findField(classNode, name, desc);
    }

    public static FieldNode findField(ClassWrapper classNode, String name, String desc) {
        for (FieldNode field : classNode.fields) {
            if (field.name.equals(name) && (desc == null || field.desc.equals(desc))) {
                return field;
            }
        }
        return null;
    }



    public static MethodNode findMethod(Obf obf, String owner, String name, String descriptor) {
        ClassWrapper classNode = obf.assureLoaded(owner);
        if (classNode == null) return null;
        return findMethod(classNode, name, descriptor);
    }

    public static MethodNode findMethod(ClassWrapper classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor))) {
                return method;
            }
        }
        return null;
    }

    public static LabelNode[] getLabels(MethodNode method) {
        List<LabelNode> labels = new ArrayList<>();
        for (AbstractInsnNode insnNode : method.instructions.toArray()) {
            if (insnNode instanceof LabelNode) {
                labels.add((LabelNode) insnNode);
            }
        }
        return labels.toArray(new LabelNode[0]);
    }

    public static InsnList iterate(InsnList instructions, AbstractInsnNode start, AbstractInsnNode end) {
        InsnList list = new InsnList();
        boolean f = false;
        for (AbstractInsnNode instruction : instructions) {
            if (!f && instruction == start) {
                f = true;
            }
            if (f) {
                list.add(instruction);
            }
            if (instruction == end) {
                break;
            }
        }
        return list;
    }

    public static ClassWrapper clone(ClassWrapper classNode) {
        ClassWrapper c = new ClassWrapper(classNode.modify);
        classNode.accept(c);
        return c;
    }

    public static void boxClass(InsnList list, Type type) {
        switch (type.getDescriptor()) {
            case "I":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;"));
                break;
            case "Z":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;"));
                break;
            case "B":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;"));
                break;
            case "C":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;"));
                break;
            case "S":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;"));
                break;
            case "J":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;"));
                break;
            case "F":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;"));
                break;
            case "D":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;"));
                break;
            case "V":
                list.add(new FieldInsnNode(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;"));
                break;
            default:
                list.add(new LdcInsnNode(type));
                break;
        }
    }

    public static MethodNode createMethod(int access, String name, String desc) {
        MethodNode m = new MethodNode(access, name, desc, null, null);
        m.instructions = new InsnList();
        return m;
    }

    public static void boxReturn(Type returnType, InsnList list) {
        Random r = new Random();
        switch (returnType.getOpcode(IRETURN)) {
            case IRETURN:
                list.add(pushInt(r.nextInt()));
                break;
            case LRETURN:
                list.add(pushLong(r.nextLong()));
                break;
            case FRETURN:
                list.add(new LdcInsnNode(r.nextFloat()));
                break;
            case DRETURN:
                list.add(new LdcInsnNode(r.nextDouble()));
                break;
            case ARETURN:
                list.add(new InsnNode(ACONST_NULL));
                break;
            case RETURN:
                break;
            default:
                throw new IllegalArgumentException("Unknown return type: " + returnType);
        }
        list.add(new InsnNode(returnType.getOpcode(IRETURN)));
    }

    public static String parentName(String name) {
        if (name.contains("/")) {
            return name.substring(0, name.lastIndexOf("/") + 1);
        } else {
            return "";
        }
    }

    public static MethodNode findMethodSuper(ClassWrapper owner, String name, String desc) {
        ClassWrapper superWrapper = owner;
        while (superWrapper != null) {
            MethodNode m = AsmUtils.findMethod(superWrapper, name, desc);
            if (m != null) {
                return m;
            }
            if (superWrapper.superName == null || superWrapper.superName.isEmpty()) {
                break;
            }
            superWrapper = Obf.getInstance().assureLoaded(superWrapper.superName);
        }

        return null;
    }

    public static FieldNode findFieldSuper(ClassWrapper ownerClass, String name, String desc) {
        ClassWrapper superWrapper = ownerClass;
        while (superWrapper != null) {
            FieldNode m = AsmUtils.findField(superWrapper, name, desc);
            if (m != null) {
                return m;
            }
            if (superWrapper.superName == null || superWrapper.superName.isEmpty()) {
                break;
            }
            superWrapper = Obf.getInstance().assureLoaded(superWrapper.superName);
        }

        return null;
    }
}
