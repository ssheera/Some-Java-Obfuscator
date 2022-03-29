package com.cheatbreaker.obf.transformer.methods;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.configuration.ConfigurationSection;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.lang.reflect.Modifier;
import java.util.*;

public class ProxyTransformer extends Transformer {

    private boolean redirections;
    private boolean inline;

    @Override
    public String getSection() {
        return "methods.proxy";
    }

    public ProxyTransformer(Obf obf) {
        super(obf);
        redirections = config.getBoolean("redirections", true);
        inline = config.getBoolean("inline-redirection", true);
    }

    @SneakyThrows
    @Override
    public void visit(ClassNode classNode) {

        List<RedirectedCall> proxyCalls = new ArrayList<>();
        Map<RedirectedCall, String> proxyCallName = new HashMap<>();
        List<RedirectedCall> redirectedCalls = new ArrayList<>();

        FieldNode f = new FieldNode(ACC_PRIVATE | ACC_STATIC,
                RandomStringUtils.randomAlphabetic(2), "[Ljava/lang/invoke/MethodHandle;", null, null);

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode node = (MethodInsnNode) instruction;
                    if (node.owner.startsWith("L") && node.owner.endsWith(";") && node.owner.length() > 1) continue;
                    MethodCall mc = new MethodCall(node.name, node.owner, node.desc, node.getOpcode());

                    ClassNode nodeOwner = obf.assureLoaded(node.owner);
                    if (nodeOwner == null) continue;

                    if (!Modifier.isPublic(nodeOwner.access)) continue;

                    if (node.getOpcode() == INVOKESPECIAL) {
                        if (!inline) continue;
                        if (!node.owner.equals(classNode.superName)) {
                            AbstractInsnNode prev = node.getPrevious();
                            while (prev != null) {
                                if (prev instanceof TypeInsnNode) {
                                    if (prev.getNext() instanceof InsnNode) {
                                        if (((TypeInsnNode) prev).desc.equals(node.owner)) {
                                            if (prev.getNext().getOpcode() == DUP) {

                                                if (!redirectedCalls.contains(mc)) redirectedCalls.add(mc);

                                                InsnList list = new InsnList();
                                                int index = redirectedCalls.indexOf(mc);
                                                list.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                                                list.add(AsmUtils.pushInt(index));
                                                list.add(new InsnNode(AALOAD));

                                                method.instructions.remove(prev.getNext());
                                                method.instructions.insertBefore(prev, list);
                                                method.instructions.remove(prev);

                                                list = new InsnList();
                                                Set<String> parents = obf.getClassTree(node.owner).parentClasses;
                                                String parent = parents.toArray()[random.nextInt(parents.size())].toString();
                                                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", node.desc.replace(")V", ")L" + parent + ";"), false));
                                                list.add(new TypeInsnNode(CHECKCAST, node.owner));
                                                method.instructions.insertBefore(node, list);
                                                method.instructions.remove(node);
                                                break;
                                            }
                                        }
                                    }
                                }
                                prev = prev.getPrevious();
                            }
                        }
                    } else if (node.getOpcode() == INVOKESTATIC) {
                        int totalStackSize = Type.getArgumentTypes(node.desc).length;

                        if (totalStackSize == 0) {
                            if (!inline) continue;
                            InsnList list = new InsnList();
                            list.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                            if (!redirectedCalls.contains(mc)) redirectedCalls.add(mc);
                            int index = redirectedCalls.indexOf(mc);
                            list.add(AsmUtils.pushInt(index));
                            list.add(new InsnNode(AALOAD));
                            list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", node.desc, false));

                            method.instructions.insertBefore(node, list);
                            method.instructions.remove(node);
                        } else {
                            if (!redirections) continue;
                            if (!redirectedCalls.contains(mc)) redirectedCalls.add(mc);
                            if (!proxyCalls.contains(mc)) proxyCalls.add(mc);
                            proxyCallName.putIfAbsent(mc, RandomStringUtils.randomAlphabetic(4));
                            String desc = mc.descriptor;
                            Type[] args = Type.getArgumentTypes(desc);
                            Type[] newArgs = new Type[args.length];
                            for (int i = 0; i < args.length; i++) {
                                String d = args[i].getDescriptor();
                                newArgs[i] = d.endsWith(";") && d.startsWith("L") ?
                                        Type.getType("Ljava/lang/Object;") : args[i];
                            }
                            desc = Type.getMethodDescriptor(Type.getReturnType(desc), newArgs);
                            method.instructions.set(node, new MethodInsnNode(INVOKESTATIC, classNode.name, proxyCallName.get(mc), desc));
                        }
                    } else {
                        if (!redirections) continue;
                        if (!redirectedCalls.contains(mc)) redirectedCalls.add(mc);
                        if (!proxyCalls.contains(mc)) proxyCalls.add(mc);
                        proxyCallName.putIfAbsent(mc, RandomStringUtils.randomAlphabetic(4));
                        String desc = mc.descriptor;
                        Type[] args = Type.getArgumentTypes(desc);
                        Type[] newArgs = new Type[args.length];
                        for (int i = 0; i < args.length; i++) {
                            String d = args[i].getDescriptor();
                            newArgs[i] = d.endsWith(";") && d.startsWith("L") ?
                                    Type.getType("Ljava/lang/Object;") : args[i];
                        }
                        desc = Type.getMethodDescriptor(Type.getReturnType(desc), newArgs);
                        desc = desc.replace("(", "(Ljava/lang/Object;");
                        method.instructions.set(node, new MethodInsnNode(INVOKESTATIC, classNode.name, proxyCallName.get(mc), desc));
                    }
                } else if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode node = (FieldInsnNode) instruction;
                    FieldCall fgc = new FieldCall(node.owner, node.name, node.desc, node.getOpcode() == GETFIELD || node.getOpcode() == PUTFIELD,
                            node.getOpcode() == PUTFIELD || node.getOpcode() == PUTSTATIC);

                    if (node.getOpcode() == GETSTATIC) {
                        if (!inline) continue;
                        InsnList list = new InsnList();
                        list.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                        if (!redirectedCalls.contains(fgc)) redirectedCalls.add(fgc);
                        int index = redirectedCalls.indexOf(fgc);
                        list.add(AsmUtils.pushInt(index));
                        list.add(new InsnNode(AALOAD));
                        String desc = "Ljava/lang/Object;";
                        if (!node.desc.startsWith("L") || !node.desc.endsWith(";")) {
                            desc = node.desc;
                        }
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", "()" + desc, false));
                        if (desc.equals("Ljava/lang/Object;")) {
                            list.add(new TypeInsnNode(CHECKCAST, node.desc));
                        }
                        method.instructions.insertBefore(node, list);
                        method.instructions.remove(node);

                    } else if (node.getOpcode() == GETFIELD) {
                        if (!redirections) continue;
                        if (!redirectedCalls.contains(fgc)) redirectedCalls.add(fgc);
                        if (!proxyCalls.contains(fgc)) proxyCalls.add(fgc);
                        proxyCallName.putIfAbsent(fgc, RandomStringUtils.randomAlphabetic(4));
                        String desc = "Ljava/lang/Object;";
                        if (!node.desc.startsWith("L") || !node.desc.endsWith(";")) {
                            desc = node.desc;
                        }
                        InsnList list = new InsnList();
                        list.add(new MethodInsnNode(INVOKESTATIC, classNode.name, proxyCallName.get(fgc), "(Ljava/lang/Object;)" + desc));
                        if (desc.equals("Ljava/lang/Object;")) {
                            list.add(new TypeInsnNode(CHECKCAST, node.desc));
                        }
                        method.instructions.insertBefore(node, list);
                        method.instructions.remove(node);
                    } else {
                        FieldNode fieldNode = AsmUtils.findField(classNode, node.name, node.desc);
                        if (fieldNode == null) continue;

                        if (node.getOpcode() == PUTSTATIC) {
                            if (!inline) continue;

                            if (Modifier.isFinal(fieldNode.access))
                                fieldNode.access &= ~Modifier.FINAL;

                            InsnList list = new InsnList();
                            list.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                            if (!redirectedCalls.contains(fgc)) redirectedCalls.add(fgc);
                            int index = redirectedCalls.indexOf(fgc);
                            list.add(AsmUtils.pushInt(index));
                            list.add(new InsnNode(AALOAD));
                            list.add(new InsnNode(SWAP));
                            method.instructions.insertBefore(node, list);

                            String desc = "Ljava/lang/Object;";
                            if (!node.desc.startsWith("L") || !node.desc.endsWith(";")) {
                                desc = node.desc;
                            }
                            method.instructions.set(node, new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", "(" + desc + ")V", false));
                        } else {
                            if (!redirections) continue;

                            if (Modifier.isFinal(fieldNode.access))
                                fieldNode.access &= ~Modifier.FINAL;

                            if (!redirectedCalls.contains(fgc)) redirectedCalls.add(fgc);
                            if (!proxyCalls.contains(fgc)) proxyCalls.add(fgc);
                            proxyCallName.putIfAbsent(fgc, RandomStringUtils.randomAlphabetic(4));
                            String desc = "Ljava/lang/Object;";
                            if (!node.desc.startsWith("L") || !node.desc.endsWith(";")) {
                                desc = node.desc;
                            }
                            method.instructions.set(node, new MethodInsnNode(INVOKESTATIC, classNode.name, proxyCallName.get(fgc), "(Ljava/lang/Object;" +
                                    desc + ")V"));
                        }
                    }
                }
            }
        }

        if (redirectedCalls.isEmpty()) return;

        classNode.fields.add(f);

        MethodNode clinit = AsmUtils.getClinit(classNode);
        MethodNode initMethod = AsmUtils.createMethod(ACC_PRIVATE | ACC_STATIC, RandomStringUtils.random(10), "()V");

        {
            int kConstructor = random.nextInt(0, 0x7FFFFF);
            int kGetter = kConstructor + random.nextInt(0, 0x7FFFFF);
            int kStaticGetter = kGetter + random.nextInt(0, 0x7FFFFF);
            int kSetter = kStaticGetter + random.nextInt(0, 0x7FFFFF);
            int kStaticSetter = kSetter + random.nextInt(0, 0x7FFFFF);
            int kStatic = kStaticSetter + random.nextInt(0, 0x7FFFFF);
            int kVirtual = kStatic + random.nextInt(0, 0x7FFFFF);

            int lookupVar = initMethod.maxLocals++;
            int classLoaderVar = initMethod.maxLocals++;
            int xorKeys1Var = initMethod.maxLocals++;
            int xorKeys2Var = initMethod.maxLocals++;
            int firstLdcVar = initMethod.maxLocals++;

            int andKey = random.nextInt();

            String methodDataSplitter = RandomStringUtils.randomNumeric(random.nextInt(5, 0x1F));

            int[] xorKeys = new int[redirectedCalls.size() + 1];
            int[] xorKeys2 = new int[redirectedCalls.size() + 1];

            for (int i = 0; i < xorKeys.length; i++) {
                xorKeys[i] = random.nextInt();
                xorKeys2[i] = random.nextInt();
            }

            LabelNode L3 = new LabelNode();
            LabelNode L4 = new LabelNode();
            LabelNode L5 = new LabelNode();
            LabelNode L6 = new LabelNode();
            LabelNode L0 = new LabelNode();
            LabelNode L1 = new LabelNode();
            LabelNode L2 = new LabelNode();
            LabelNode L7 = new LabelNode();
            LabelNode L13 = new LabelNode();
            LabelNode L14 = new LabelNode();
            LabelNode L15 = new LabelNode();
            LabelNode L16 = new LabelNode();
            LabelNode L17 = new LabelNode();
            LabelNode L18 = new LabelNode();

            initMethod.tryCatchBlocks.add(new TryCatchBlockNode(L0, L1, L2, "java/lang/Exception"));
            initMethod.tryCatchBlocks.add(new TryCatchBlockNode(L3, L4, L5, "java/lang/Exception"));

            LabelNode[] labels = new LabelNode[redirectedCalls.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = new LabelNode();
            }

            InsnList start = new InsnList();

            start.add(AsmUtils.pushInt(redirectedCalls.size()));
            start.add(new TypeInsnNode(ANEWARRAY, "java/lang/invoke/MethodHandle"));

            start.add(AsmUtils.pushInt(xorKeys.length));
            start.add(new IntInsnNode(NEWARRAY, T_INT));
            for (int i = 0; i < xorKeys.length; i++) {
                start.add(new InsnNode(DUP));
                start.add(AsmUtils.pushInt(i));
                start.add(AsmUtils.pushInt(xorKeys2[i]));
                start.add(new InsnNode(IASTORE));
            }

            start.add(new InsnNode(DUP));

            start.add(new VarInsnNode(ASTORE, xorKeys2Var));

            start.add(new InsnNode(SWAP));

            start.add(new FieldInsnNode(PUTSTATIC, classNode.name, f.name, f.desc));

            start.add(new InsnNode(POP));

            start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false));

            start.add(AsmUtils.pushInt(xorKeys.length));
            start.add(new IntInsnNode(NEWARRAY, T_INT));
            for (int i = 0; i < xorKeys.length; i++) {
                start.add(new InsnNode(DUP));
                start.add(AsmUtils.pushInt(i));
                start.add(AsmUtils.pushInt(xorKeys[i]));
                start.add(new InsnNode(IASTORE));
            }

            start.add(new VarInsnNode(ASTORE, xorKeys1Var));

            start.add(new VarInsnNode(ASTORE, lookupVar));

            start.add(new LdcInsnNode(Type.getType("L" + classNode.name + ";")));
            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false));
            start.add(new VarInsnNode(ASTORE, classLoaderVar));

            StringBuilder sb = new StringBuilder();
            for (RedirectedCall methodCall : redirectedCalls) {
                char c = (char) (methodCall.hashCode() ^ random.nextInt());
                sb.append(c);
            }

            start.add(new LdcInsnNode(sb.toString()));
            start.add(new VarInsnNode(ASTORE, firstLdcVar));
            start.add(new InsnNode(ICONST_0));
            start.add(new VarInsnNode(ISTORE, firstLdcVar + 1));

            start.add(L6);

            start.add(new InsnNode(ACONST_NULL));
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 2));
            start.add(new InsnNode(ACONST_NULL));
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 3));
            start.add(new InsnNode(ACONST_NULL));
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 10));//class for fields (the type)
            start.add(new InsnNode(ICONST_0));
            start.add(new VarInsnNode(ISTORE, firstLdcVar + 9));
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
            start.add(new IincInsnNode(firstLdcVar + 1, 1));
            start.add(new VarInsnNode(ISTORE, firstLdcVar + 4));

            start.add(L7);

            start.add(new VarInsnNode(ILOAD, firstLdcVar + 4));
            start.add(new VarInsnNode(ALOAD, firstLdcVar));
            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false));
            start.add(new InsnNode(ARRAYLENGTH));
            start.add(new JumpInsnNode(IF_ICMPGE, L3));

            for (int i = 0; i < redirectedCalls.size(); i++) {
                RedirectedCall rCall = redirectedCalls.get(i);

                if (i == 0) {
                    start.add(new VarInsnNode(ILOAD, firstLdcVar + 4));
                    start.add(new JumpInsnNode(IFNE, labels[i]));
                } else {
                    start.add(labels[i - 1]);
                    start.add(new VarInsnNode(ILOAD, firstLdcVar + 4));
                    start.add(AsmUtils.pushInt(i));
                    start.add(new JumpInsnNode(IF_ICMPNE, labels[i]));
                }

                int extraLength1 = random.nextInt(10, 20);
                int extraLength2 = random.nextInt(10, 20);

                start.add(new InsnNode(ICONST_0));
                start.add(new InsnNode(DUP));

                int r = random.nextInt();

                if (rCall instanceof MethodCall) {
                    MethodCall methodCall = (MethodCall) rCall;

                    switch (methodCall.opcode) {
                        case INVOKESPECIAL:
                            start.add(AsmUtils.pushInt(kConstructor ^ (i + 1) ^ r));
                            start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                            start.add(new InsnNode(IXOR));
                            start.add(AsmUtils.pushLong(r));
                            start.add(new InsnNode(L2I));
                            start.add(new InsnNode(IXOR));
                            start.add(new VarInsnNode(ISTORE, firstLdcVar + 9));

                            start.add(new LdcInsnNode(xor(extraLength1, methodCall.owner.replace("/", "."), xorKeys2[i + 1], methodCall.descriptor.hashCode())));
                            start.add(new VarInsnNode(ASTORE, firstLdcVar + 2));
                            start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                            start.add(new InsnNode(SWAP));
                            start.add(AsmUtils.pushInt(methodCall.owner.length()));
                            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
                            start.add(new VarInsnNode(ASTORE, firstLdcVar + 2));

                            start.add(new LdcInsnNode(xor(extraLength2, methodCall.descriptor, xorKeys[i + 1])));
                            start.add(new VarInsnNode(ASTORE, firstLdcVar + 3));
                            start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));

                            start.add(new InsnNode(SWAP));
                            start.add(AsmUtils.pushInt(methodCall.descriptor.length()));
                            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
                            start.add(new VarInsnNode(ASTORE, firstLdcVar + 3));
                            break;
                        case INVOKESTATIC:
                        case INVOKEINTERFACE:
                        case INVOKEVIRTUAL:
                            int key = methodCall.opcode == INVOKESTATIC ? kStatic : kVirtual;
                            start.add(AsmUtils.pushInt(key ^ (i + 1) ^ r));
                            start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                            start.add(new InsnNode(IXOR));
                            start.add(AsmUtils.pushLong(r));
                            start.add(new InsnNode(L2I));
                            start.add(new InsnNode(IXOR));
                            start.add(new VarInsnNode(ISTORE, firstLdcVar + 9));

                            start.add(new LdcInsnNode(xor(extraLength1, methodCall.owner.replace("/", ".") +
                                    methodDataSplitter + methodCall.name, xorKeys2[i + 1], methodCall.descriptor.hashCode())));
                            start.add(new VarInsnNode(ASTORE, firstLdcVar + 2));
                            start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                            start.add(new InsnNode(SWAP));
                            start.add(AsmUtils.pushInt(methodCall.owner.length() + methodDataSplitter.length() + methodCall.name.length()));
                            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
                            start.add(new VarInsnNode(ASTORE, firstLdcVar + 2));

                            start.add(new LdcInsnNode(xor(extraLength2, methodCall.descriptor, xorKeys[i + 1])));
                            start.add(new VarInsnNode(ASTORE, firstLdcVar + 3));
                            start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));

                            start.add(new InsnNode(SWAP));
                            start.add(AsmUtils.pushInt(methodCall.descriptor.length()));
                            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
                            start.add(new VarInsnNode(ASTORE, firstLdcVar + 3));
                            break;
                    }

                    start.add(new JumpInsnNode(GOTO, L3));
                } else if (rCall instanceof FieldCall) {
                    FieldCall fieldCall = (FieldCall) rCall;


                    start.add(AsmUtils.pushInt((fieldCall.virtual ? fieldCall.put ? kSetter : kGetter : fieldCall.put ? kStaticSetter : kStaticGetter) ^
                            (i + 1) ^ r
                    ));
                    start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                    start.add(new InsnNode(IXOR));
                    start.add(AsmUtils.pushLong(r));
                    start.add(new InsnNode(L2I));
                    start.add(new InsnNode(IXOR));
                    start.add(new VarInsnNode(ISTORE, firstLdcVar + 9));

                    start.add(new LdcInsnNode(xor(extraLength1, fieldCall.name, xorKeys2[i + 1], fieldCall.owner.replace("/", ".").hashCode())));
                    start.add(new VarInsnNode(ASTORE, firstLdcVar + 2));
                    start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                    start.add(new InsnNode(SWAP));
                    start.add(AsmUtils.pushInt(fieldCall.name.length()));
                    start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
                    start.add(new VarInsnNode(ASTORE, firstLdcVar + 2));

                    start.add(new LdcInsnNode(xor(extraLength2, fieldCall.owner.replace("/", "."), xorKeys[i + 1])));
                    start.add(new VarInsnNode(ASTORE, firstLdcVar + 3));
                    start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));

                    start.add(new InsnNode(SWAP));
                    start.add(AsmUtils.pushInt(fieldCall.owner.length()));
                    start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
                    start.add(new VarInsnNode(ASTORE, firstLdcVar + 3));

                    start.add(fieldCall.type);
                    start.add(new VarInsnNode(ASTORE, firstLdcVar + 10));

                    start.add(new JumpInsnNode(GOTO, L3));
                }
            }

            start.add(labels[labels.length - 1]);
            start.add(new IincInsnNode(firstLdcVar + 4, 1));
            start.add(new JumpInsnNode(GOTO, L7));

            start.add(L3);
            start.add(AsmUtils.pushInt(andKey));
            start.add(new VarInsnNode(ISTORE, firstLdcVar + 4));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false));
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 5));

            start.add(L0);
            start.add(new InsnNode(ICONST_0));
            start.add(new VarInsnNode(ISTORE, firstLdcVar + 6));

            start.add(L13);
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 6));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 5));
            start.add(new InsnNode(ARRAYLENGTH));
            start.add(new JumpInsnNode(IF_ICMPGE, L1));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 5));
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 6));
            start.add(new InsnNode(DUP2));
            start.add(new InsnNode(CALOAD));

            start.add(new VarInsnNode(ALOAD, xorKeys1Var));
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
            start.add(new InsnNode(IALOAD));

            start.add(new InsnNode(IXOR));
            start.add(new InsnNode(I2C));
            start.add(new InsnNode(CASTORE));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 5));
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 6));
            start.add(new InsnNode(ICONST_1));
            start.add(new InsnNode(IADD));
            start.add(new InsnNode(DUP2));
            start.add(new InsnNode(CALOAD));
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 4));
            start.add(new IincInsnNode(firstLdcVar + 4, 0));
            start.add(AsmUtils.pushInt(Character.MAX_VALUE ^ andKey));
            start.add(new InsnNode(IXOR));
            start.add(new InsnNode(IAND));
            start.add(new InsnNode(I2C));
            start.add(new InsnNode(CASTORE));
            start.add(new IincInsnNode(firstLdcVar + 6, 1));
            start.add(new JumpInsnNode(GOTO, L13));

            start.add(L1);
            start.add(new JumpInsnNode(GOTO, L14));

            start.add(L2);
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 6));
            start.add(new TypeInsnNode(NEW, "java/lang/String"));
            start.add(new InsnNode(DUP));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 5));
            start.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false));
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 3));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false));
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 7));
            start.add(new InsnNode(ICONST_0));
            start.add(new VarInsnNode(ISTORE, firstLdcVar + 8));

            start.add(L15);
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 8));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 7));
            start.add(new InsnNode(ARRAYLENGTH));
            start.add(new JumpInsnNode(IF_ICMPGE, L16));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 7));
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 8));
            start.add(new InsnNode(DUP2));
            start.add(new InsnNode(CALOAD));

            start.add(new VarInsnNode(ALOAD, xorKeys2Var));
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
            start.add(new InsnNode(IALOAD));

            start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
            start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false));

            start.add(new InsnNode(IXOR));
            start.add(new InsnNode(IXOR));
            start.add(new InsnNode(I2C));
            start.add(new InsnNode(CASTORE));
            start.add(new IincInsnNode(firstLdcVar + 8, 1));
            start.add(new JumpInsnNode(GOTO, L15));

            start.add(L16);
            start.add(new TypeInsnNode(NEW, "java/lang/String"));
            start.add(new InsnNode(DUP));
            start.add(new VarInsnNode(ALOAD, firstLdcVar + 7));
            start.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false));
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 2));
            start.add(new JumpInsnNode(GOTO, L4));

            start.add(L14);
            start.add(new JumpInsnNode(GOTO, L3));

            start.add(L4);
            start.add(new JumpInsnNode(GOTO, L17));

            start.add(L5);
            start.add(new VarInsnNode(ASTORE, firstLdcVar + 4));
            start.add(new JumpInsnNode(GOTO, L18));

            start.add(L17);

            int[] switchKeys = new int[]{kConstructor, kGetter, kStaticGetter, kSetter, kStaticSetter, kStatic, kVirtual};
            LabelNode[] switchLabels = new LabelNode[switchKeys.length];
            for (int i = 0; i < switchLabels.length; i++) {
                switchLabels[i] = new LabelNode();
            }
            start.add(new VarInsnNode(ILOAD, firstLdcVar + 9));
            start.add(new LookupSwitchInsnNode(L3, switchKeys, switchLabels));

            for (int i = 0; i < switchLabels.length; i++) {
                start.add(switchLabels[i]);
                switch (i) {
                    case 0:
                        start.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                        start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(ISUB));
                        start.add(new VarInsnNode(ALOAD, lookupVar));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new InsnNode(ICONST_0));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findConstructor", "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false));
                        start.add(new InsnNode(AASTORE));
                        start.add(new JumpInsnNode(GOTO, L6));
                        break;
                    case 1:
                        start.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                        start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(ISUB));
                        start.add(new VarInsnNode(ALOAD, lookupVar));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
                        start.add(new InsnNode(ICONST_0));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 10));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findGetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false));
                        start.add(new InsnNode(AASTORE));
                        start.add(new JumpInsnNode(GOTO, L6));
                        break;
                    case 2:
                        start.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                        start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(ISUB));
                        start.add(new VarInsnNode(ALOAD, lookupVar));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
                        start.add(new InsnNode(ICONST_0));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 10));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStaticGetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false));
                        start.add(new InsnNode(AASTORE));
                        start.add(new JumpInsnNode(GOTO, L6));
                        break;
                    case 3:
                        start.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                        start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(ISUB));
                        start.add(new VarInsnNode(ALOAD, lookupVar));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
                        start.add(new InsnNode(ICONST_0));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 10));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findSetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false));
                        start.add(new InsnNode(AASTORE));
                        start.add(new JumpInsnNode(GOTO, L6));
                        break;
                    case 4:
                        start.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                        start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(ISUB));
                        start.add(new VarInsnNode(ALOAD, lookupVar));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
                        start.add(new InsnNode(ICONST_0));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 10));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStaticSetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false));
                        start.add(new InsnNode(AASTORE));
                        start.add(new JumpInsnNode(GOTO, L6));
                        break;
                    case 5:
                        start.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                        start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(ISUB));
                        start.add(new VarInsnNode(ALOAD, lookupVar));

                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new LdcInsnNode(methodDataSplitter));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false));
                        start.add(new InsnNode(ICONST_0));
                        start.add(new InsnNode(AALOAD));

                        start.add(new InsnNode(ICONST_0));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false));

                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new LdcInsnNode(methodDataSplitter));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(AALOAD));

                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false));
                        start.add(new InsnNode(AASTORE));
                        start.add(new JumpInsnNode(GOTO, L6));
                        break;
                    case 6:
                        start.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                        start.add(new VarInsnNode(ILOAD, firstLdcVar + 1));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(ISUB));
                        start.add(new VarInsnNode(ALOAD, lookupVar));

                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new LdcInsnNode(methodDataSplitter));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false));
                        start.add(new InsnNode(ICONST_0));
                        start.add(new InsnNode(AALOAD));

                        start.add(new InsnNode(ICONST_0));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false));

                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 2));
                        start.add(new LdcInsnNode(methodDataSplitter));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false));
                        start.add(new InsnNode(ICONST_1));
                        start.add(new InsnNode(AALOAD));

                        start.add(new VarInsnNode(ALOAD, firstLdcVar + 3));
                        start.add(new VarInsnNode(ALOAD, classLoaderVar));
                        start.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false));
                        start.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false));
                        start.add(new InsnNode(AASTORE));
                        start.add(new JumpInsnNode(GOTO, L6));
                        break;
                }
            }

            start.add(L18);

            start.add(new InsnNode(RETURN));

            initMethod.instructions.add(start);

            Analyzer<?> analyzer = new Analyzer<>(new BasicInterpreter());
            analyzer.analyzeAndComputeMaxs(classNode.name, initMethod);

        }

        clinit.instructions.insertBefore(clinit.instructions.getFirst(), new MethodInsnNode(INVOKESTATIC, classNode.name, initMethod.name,
                initMethod.desc, false));

        classNode.methods.add(initMethod);

        if (!proxyCalls.isEmpty()) {
            for (RedirectedCall proxyCall : proxyCalls) {
                if (proxyCall instanceof MethodCall) {
                    MethodCall methodCall = (MethodCall) proxyCall;
                    String name = proxyCallName.get(methodCall);
                    int index = redirectedCalls.indexOf(methodCall);
                    String desc = methodCall.descriptor;
                    {
                        Type[] args = Type.getArgumentTypes(desc);
                        Type[] newArgs = new Type[args.length];
                        for (int i = 0; i < args.length; i++) {
                            String d = args[i].getDescriptor();
                            newArgs[i] = d.endsWith(";") && d.startsWith("L") ?
                                    Type.getType("Ljava/lang/Object;") : args[i];
                        }
                        desc = Type.getMethodDescriptor(Type.getReturnType(desc), newArgs);
                    }
                    if (methodCall.opcode != INVOKESTATIC) {
                        desc = desc.replace("(", "(Ljava/lang/Object;");
                    }
                    MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
                    method.instructions = new InsnList();
                    method.instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                    method.instructions.add(new InsnNode(DUP));
                    method.instructions.add(new InsnNode(ARRAYLENGTH));
                    int r = random.nextInt();
                    method.instructions.add(AsmUtils.pushInt(r));
                    method.instructions.add(new InsnNode(IXOR));
                    method.instructions.add(AsmUtils.pushInt(index ^ redirectedCalls.size() ^ r));
                    method.instructions.add(new InsnNode(IXOR));
                    method.instructions.add(new InsnNode(AALOAD));
                    Type[] args = Type.getArgumentTypes(desc);
                    for (Type arg : args) {
                        method.instructions.add(new VarInsnNode(arg.getOpcode(ILOAD), method.maxLocals));
                        method.maxLocals += arg.getSize();
                    }
                    method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", desc));
                    method.instructions.add(new InsnNode(Type.getReturnType(desc).getOpcode(IRETURN)));
                    classNode.methods.add(method);
                } else {
                    if (proxyCall instanceof FieldCall) {
                        FieldCall fieldCall = (FieldCall) proxyCall;
                        String desc = "Ljava/lang/Object;";
                        if (!fieldCall.desc.startsWith("L") || !fieldCall.desc.endsWith(";")) {
                            desc = fieldCall.desc;
                        }
                        String name = proxyCallName.get(fieldCall);
                        int index = redirectedCalls.indexOf(fieldCall);
                        if (!fieldCall.put) {
                            MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, name, "(Ljava/lang/Object;)" + desc, null, null);
                            method.instructions = new InsnList();
                            method.instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                            method.instructions.add(new InsnNode(DUP));
                            method.instructions.add(new InsnNode(ARRAYLENGTH));
                            int r = random.nextInt();
                            method.instructions.add(AsmUtils.pushInt(r));
                            method.instructions.add(new InsnNode(IXOR));
                            method.instructions.add(AsmUtils.pushInt(index ^ redirectedCalls.size() ^ r));
                            method.instructions.add(new InsnNode(IXOR));
                            method.instructions.add(new InsnNode(AALOAD));
                            method.instructions.add(new VarInsnNode(ALOAD, 0));
                            method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", method.desc));
                            method.instructions.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(IRETURN)));
                            classNode.methods.add(method);
                        } else {
                            MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, name, "(Ljava/lang/Object;" + desc + ")V", null, null);
                            method.instructions = new InsnList();
                            method.instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, f.name, f.desc));
                            method.instructions.add(new InsnNode(DUP));
                            method.instructions.add(new InsnNode(ARRAYLENGTH));
                            int r = random.nextInt();
                            method.instructions.add(AsmUtils.pushInt(r));
                            method.instructions.add(new InsnNode(IXOR));
                            method.instructions.add(AsmUtils.pushInt(index ^ redirectedCalls.size() ^ r));
                            method.instructions.add(new InsnNode(IXOR));
                            method.instructions.add(new InsnNode(AALOAD));
                            method.instructions.add(new VarInsnNode(ALOAD, 0));
                            method.instructions.add(new VarInsnNode(Type.getType(desc).getOpcode(ILOAD), 1));
                            method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", method.desc));
                            method.instructions.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(IRETURN)));
                            classNode.methods.add(method);
                        }
                    }
                }
            }
        }
    }

    private String xor(int el, String string, int... keys) {
        char[] chars = string.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            for (int key : keys) {
                chars[i] ^= key;
            }
        }
        for (int i = 0; i < el; i++) {
            char c = chars[random.nextInt(chars.length)];
            chars = ArrayUtils.add(chars, (char) random.nextInt(c - 64, c + 64));
        }
        return new String(chars);
    }

    private static class RedirectedCall {
    }

    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private static class MethodCall extends RedirectedCall {

        @Getter
        int opcode;
        @Getter
        String name;
        @Getter
        String owner;
        @Getter
        String descriptor;

        public MethodCall(String name, String owner, String descriptor, int opcode) {
            this.name = name;
            this.owner = owner;
            this.descriptor = descriptor;
            this.opcode = opcode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodCall that = (MethodCall) o;
            return opcode == that.opcode && name.equals(that.name) && owner.equals(that.owner) && descriptor.equals(that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(opcode, name, owner, descriptor);
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private static class FieldCall extends RedirectedCall {

        @Getter
        String owner;
        @Getter
        String name;
        @Getter
        String desc;
        @Getter
        boolean virtual;
        @Getter
        boolean put;

        @Getter
        InsnList type;

        private FieldCall(String owner, String name, String desc, boolean virtual, boolean put) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.virtual = virtual;
            this.put = put;
            this.type = new InsnList();
            AsmUtils.boxClass(type, Type.getType(desc));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldCall fieldCall = (FieldCall) o;
            return virtual == fieldCall.virtual && put == fieldCall.put && owner.equals(fieldCall.owner) && name.equals(fieldCall.name) && desc.equals(fieldCall.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name, desc, virtual, put);
        }
    }
}
