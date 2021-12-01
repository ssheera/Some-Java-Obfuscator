package com.cheatbreaker.obf.transformer;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.utils.AsmUtils;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

public class StringTransformer extends Transformer {

    enum Method {
        XOR_P_RANDOM_OR_P2,
        XOR_P2_RANDOM_AND_P,
        XOR_P_RANDOM,
        XOR_P2_RANDOM,
        OR_P_P2_XOR_RANDOM,
        ADD_P_RANDOM,
        ADD_P2_RANDOM_OR_P,
        LSHIFT_P2_RANDOM_FMOD_16,
        RSHIFT_P_RANDOM_FMOD_16
    }

    enum RestrictedMethod {
        XOR_P_RANDOM,
        OR_P_RANDOM,
        AND_P_RANDOM_XOR_P,
        SUB_P_RANDOM_OR_P
    }

    public StringTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public void visit(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (AsmUtils.codeSize(method) >= 60000) {
                    System.out.println("Skipping String, method too big: " + method.name);
                    break;
                }
                if (instruction instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) instruction;
                    if (ldc.cst instanceof String) {

                        String s = (String) ldc.cst;
                        long[] keys = new long[s.length()];
                        long[] xorKeys = new long[s.length()];
                        char[] chars = s.toCharArray();

                        RestrictedMethod rMethod = null;
                        HashMap<Integer, Method> methods = new HashMap<>();
                        HashMap<Integer, Long> randomValues = new HashMap<>();

                        for (int i = 0; i < chars.length; i++) {
                            randomValues.put(i, random.nextLong());
                            long rand = randomValues.get(i);
                            keys[i] = chars[i];
                            if (i == 0) {
                                xorKeys[i] = rand;
                            } else {
                                if (i == 1) {
                                    rMethod = RestrictedMethod.values()[random.nextInt(RestrictedMethod.values().length)];
                                    switch (rMethod) {
                                        case XOR_P_RANDOM:
                                            xorKeys[i] = rand ^ xorKeys[0];
                                            break;
                                        case OR_P_RANDOM:
                                            xorKeys[i] = rand | xorKeys[0];
                                            break;
                                        case SUB_P_RANDOM_OR_P:
                                            xorKeys[i] = (xorKeys[0] - rand) | xorKeys[0];
                                            break;
                                        case AND_P_RANDOM_XOR_P:
                                            xorKeys[i] = (rand & xorKeys[0]) ^ xorKeys[0];
                                            break;
                                    }
                                } else {
                                    methods.put(i, Method.values()[random.nextInt(Method.values().length)]);
                                    switch (methods.get(i)) {
                                        case XOR_P_RANDOM_OR_P2:
                                            xorKeys[i] = (rand ^ xorKeys[i - 1]) | xorKeys[i - 2];
                                            break;
                                        case XOR_P2_RANDOM_AND_P:
                                            xorKeys[i] = (rand ^ xorKeys[i - 2]) & xorKeys[i - 1];
                                            break;
                                        case XOR_P_RANDOM:
                                            xorKeys[i] = rand ^ xorKeys[i - 1];
                                            break;
                                        case XOR_P2_RANDOM:
                                            xorKeys[i] = rand ^ xorKeys[i - 2];
                                            break;
                                        case OR_P_P2_XOR_RANDOM:
                                            xorKeys[i] = (xorKeys[i - 2] | xorKeys[i - 1]) ^ rand;
                                            break;
                                        case ADD_P_RANDOM:
                                            xorKeys[i] = xorKeys[i - 1] + rand;
                                            break;
                                        case ADD_P2_RANDOM_OR_P:
                                            xorKeys[i] = (xorKeys[i - 2] + rand) | xorKeys[i - 1];
                                            break;
                                        case LSHIFT_P2_RANDOM_FMOD_16:
                                            xorKeys[i] = rand << (xorKeys[i - 2] % 16);
                                            break;
                                        case RSHIFT_P_RANDOM_FMOD_16:
                                            xorKeys[i] = rand >> (xorKeys[i - 1] % 16);
                                            break;
                                    }
                                }
                            }
                            keys[i] ^= xorKeys[i];
                        }

                        int arrayVar = method.maxLocals++;
                        int xorKeysVar = method.maxLocals++;
                        int loopVar = method.maxLocals++;
                        int stringVar = method.maxLocals++;
                        int charVar = method.maxLocals++;

                        InsnList list = new InsnList();
                        list.add(new VarInsnNode(ASTORE, stringVar));

                        list.add(AsmUtils.pushInt(keys.length));
                        list.add(new IntInsnNode(NEWARRAY, T_LONG));

                        for (int i = 0; i < keys.length; i++) {
                            list.add(new InsnNode(DUP));
                            list.add(AsmUtils.pushInt(i));
                            list.add(AsmUtils.pushLong(keys[i]));
                            list.add(new InsnNode(LASTORE));
                        }

                        list.add(new VarInsnNode(ASTORE, arrayVar));
                        list.add(AsmUtils.pushInt(keys.length));
                        list.add(new IntInsnNode(NEWARRAY, T_LONG));
                        list.add(new VarInsnNode(ASTORE, xorKeysVar));

                        for (int i = 0; i < keys.length; i++) {
                            if (i == 0) {
                                list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                list.add(AsmUtils.pushInt(i));
                                list.add(AsmUtils.pushLong(xorKeys[i]));
                                list.add(new InsnNode(LASTORE));
                            } else if (i == 1) {
                                list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                list.add(AsmUtils.pushInt(i));
                                list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                list.add(AsmUtils.pushInt(0));
                                list.add(new InsnNode(LALOAD));
                                switch (rMethod) {
                                    case XOR_P_RANDOM:
                                        list.add(AsmUtils.pushLong(randomValues.get(i)));
                                        list.add(new InsnNode(LXOR));
                                        break;
                                    case OR_P_RANDOM:
                                        list.add(AsmUtils.pushLong(randomValues.get(i)));
                                        list.add(new InsnNode(LOR));
                                        break;
                                    case AND_P_RANDOM_XOR_P:
                                        list.add(AsmUtils.pushLong(randomValues.get(i)));
                                        list.add(new InsnNode(LAND));
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(0));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(new InsnNode(LXOR));
                                        break;
                                    case SUB_P_RANDOM_OR_P:
                                        list.add(AsmUtils.pushLong(randomValues.get(i)));
                                        list.add(new InsnNode(LSUB));
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(0));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(new InsnNode(LOR));
                                        break;
                                }
                                list.add(new InsnNode(LASTORE));
                            } else {
                                Method m = methods.get(i);
                                long rand = randomValues.get(i);
                                list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                list.add(AsmUtils.pushInt(i));
                                switch (m) {
                                    case XOR_P_RANDOM_OR_P2:
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 1));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new InsnNode(LXOR));
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 2));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(new InsnNode(LOR));
                                        break;
                                    case XOR_P2_RANDOM_AND_P:
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 2));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new InsnNode(LXOR));
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 1));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(new InsnNode(LAND));
                                        break;
                                    case XOR_P_RANDOM:
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 1));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new InsnNode(LXOR));
                                        break;
                                    case XOR_P2_RANDOM:
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 2));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new InsnNode(LXOR));
                                        break;
                                    case OR_P_P2_XOR_RANDOM:
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 1));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 2));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(new InsnNode(LOR));
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new InsnNode(LXOR));
                                        break;
                                    case ADD_P_RANDOM:
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 1));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new InsnNode(LADD));
                                        break;
                                    case ADD_P2_RANDOM_OR_P:
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 2));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new InsnNode(LADD));
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 1));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(new InsnNode(LOR));
                                        break;
                                    case LSHIFT_P2_RANDOM_FMOD_16:
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 2));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(AsmUtils.pushLong(16L));
                                        list.add(new InsnNode(LREM));
                                        list.add(new InsnNode(L2I));
                                        list.add(new InsnNode(LSHL));
                                        break;
                                    case RSHIFT_P_RANDOM_FMOD_16:
                                        list.add(AsmUtils.pushLong(rand));
                                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                                        list.add(AsmUtils.pushInt(i - 1));
                                        list.add(new InsnNode(LALOAD));
                                        list.add(AsmUtils.pushLong(16L));
                                        list.add(new InsnNode(LREM));
                                        list.add(new InsnNode(L2I));
                                        list.add(new InsnNode(LSHR));
                                        break;
                                }
                                list.add(new InsnNode(LASTORE));
                            }
                        }


                        list.add(AsmUtils.pushInt(0));
                        list.add(new VarInsnNode(ISTORE, loopVar));

                        LabelNode start = new LabelNode();
                        LabelNode end = new LabelNode();

                        list.add(start);
                        list.add(new VarInsnNode(ILOAD, loopVar));
                        list.add(new VarInsnNode(ALOAD, arrayVar));
                        list.add(new InsnNode(ARRAYLENGTH));
                        list.add(new JumpInsnNode(IF_ICMPGE, end));
                        list.add(new VarInsnNode(ALOAD, arrayVar));
                        list.add(new VarInsnNode(ILOAD, loopVar));
                        list.add(new InsnNode(LALOAD));
                        list.add(new VarInsnNode(ALOAD, xorKeysVar));
                        list.add(new VarInsnNode(ILOAD, loopVar));
                        list.add(new InsnNode(LALOAD));
                        list.add(new InsnNode(LXOR));
                        list.add(new InsnNode(L2I));
                        list.add(new VarInsnNode(ISTORE, charVar));

                        list.add(new TypeInsnNode(NEW, "java/lang/StringBuilder"));
                        list.add(new InsnNode(DUP));
                        list.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V"));
                        list.add(new VarInsnNode(ALOAD, stringVar));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
                        list.add(new VarInsnNode(ILOAD, charVar));
                        list.add(new InsnNode(I2C));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;"));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;"));
                        list.add(new VarInsnNode(ASTORE, stringVar));
                        list.add(new IincInsnNode(loopVar, 1));
                        list.add(new JumpInsnNode(GOTO, start));

                        list.add(end);
                        list.add(new VarInsnNode(ALOAD, stringVar));

                        method.instructions.insert(ldc, list);
                        ldc.cst = "";
                    }
                }
            }
        }
    }

    @Override
    public void after() {
        for (ClassNode newClass : obf.getNewClasses()) {
            visit(newClass);
        }
    }
}
