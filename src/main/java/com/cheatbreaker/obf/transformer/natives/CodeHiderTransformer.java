package com.cheatbreaker.obf.transformer.natives;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.AsmUtils;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import com.cheatbreaker.obf.utils.asm.ContextClassWriter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

public class CodeHiderTransformer extends Transformer {

    public CodeHiderTransformer(Obf obf) {
        super(obf);
        canBeIterated = false;
    }

    @Override
    public String getSection() {
        return "natives.codehider";
    }

    @SneakyThrows
    @Override
    protected void after() {

        for (ClassWrapper classNode : obf.getClasses()) {
            if (classNode.name.equals("vm/NativeHandler")) continue;
            transform(classNode);
        }
    }

    @SneakyThrows
    public void transform(ClassWrapper classNode) {

//        if (!Modifier.isPublic(classNode.access)) return;

        String _name = classNode.name + RandomStringUtils.randomNumeric(50);
        String name = classNode.name;
        String bytesCallName = "bytesCall$" + RandomStringUtils.randomNumeric(10);

        {
            ContextClassWriter cw = new ContextClassWriter(ClassWriter.COMPUTE_FRAMES);
            classNode.name = _name;

            Map<String, AbstractInsnNode[]> cached = new HashMap<>();

            for (MethodNode method : new ArrayList<>(classNode.methods)) {
                if (!safe(method)) {
                    continue;
                }
                setup(classNode, method, name);
            }

            for (MethodNode method : classNode.methods) {
                if (!method.name.equals("<clinit>")) continue;

                AbstractInsnNode[] instructions = method.instructions.toArray();
                cached.put(method.name + method.desc, instructions);

                LabelNode endLabel = new LabelNode();
                InsnList list = new InsnList();

                list.add(AsmUtils.pushInt((short) random.nextInt()));
                list.add(new InsnNode(ICONST_0));
                list.add(new InsnNode(IXOR));
                list.add(new JumpInsnNode(IFNE, endLabel));

                method.instructions.insert(list);
                method.instructions.add(endLabel);
                method.instructions.add(new InsnNode(RETURN));

            }

            int old = classNode.access;
            classNode.access |= ACC_PUBLIC;

            MethodNode bytesCall = new MethodNode(ACC_PUBLIC | ACC_STATIC,
                    bytesCallName, "(Ljava/lang/Class;Ljava/lang/String;)[I", null, null);
            bytesCall.instructions = new InsnList();
            bytesCall.instructions.add(new VarInsnNode(ALOAD, 0));
            bytesCall.instructions.add(new VarInsnNode(ALOAD, 1));
            bytesCall.instructions.add(new MethodInsnNode(INVOKESTATIC, "vm/NativeHandler", "raw_bytes",
                    "(Ljava/lang/Class;Ljava/lang/String;)[I"));
            bytesCall.instructions.add(new InsnNode(ARETURN));
            bytesCall.maxStack = 10;
            bytesCall.maxLocals = 10;

            classNode.methods.add(bytesCall);

            classNode.accept(cw);

            classNode.access = old;
            classNode.name = name;
            classNode.methods.remove(bytesCall);

            for (MethodNode method : classNode.methods) {
                String id = method.name + method.desc;
                if (cached.containsKey(id)) {
                    method.instructions.clear();
                    Arrays.stream(cached.get(id)).forEach(method.instructions::add);
                }
            }

            obf.getLoader().addClass(_name, cw.toByteArray());

        }

        List<String> excluded = new ArrayList<>();

        for (MethodNode method : new ArrayList<>(classNode.methods)) {
            if (!safe(method)) {
                excluded.add(method.name + method.desc);
                continue;
            }

            log("%s.%s%s", classNode.name, method.name, method.desc);

            int[] bytes;

//            Class<?> klass = obf.getLoader().loadClass(_name.replace('/', '.'), false);
//            bytes = NativeHandler.raw_bytes(klass, method.name + method.desc);
//
            try {
                Class<?> klass = obf.getLoader().loadClass(_name.replace('/', '.'), true);
                bytes = (int[]) klass.getMethod(bytesCallName, Class.class, String.class).invoke(null, klass, method.name + method.desc);
            } catch (Exception ex) {
                ex.printStackTrace();
                excluded.add(method.name + method.desc);
                continue;
            }

            registerMethod(classNode, method,
                    bytes);
        }

        ContextClassWriter cw = new ContextClassWriter(ClassWriter.COMPUTE_FRAMES, true, excluded);
        classNode.accept(cw);

        obf.addGeneratedClass(classNode.name, cw.toByteArray());

    }

    public void setup(ClassWrapper classNode, MethodNode method, String realName) {

        InsnList list = new InsnList();
        list.add(new LdcInsnNode(Type.getType("L" + realName + ";")));
        list.add(new LdcInsnNode(method.name + method.desc));

        list.add(AsmUtils.pushInt((short) (realName.hashCode() + method.name.hashCode() +
                method.desc.hashCode())));
        list.add(new IntInsnNode(NEWARRAY, T_INT));

        list.add(new MethodInsnNode(INVOKESTATIC, "vm/NativeHandler", "transformMethod", "(Ljava/lang/Class;Ljava/lang/String;[I)V", false));

        MethodNode clinit = AsmUtils.getClinit(classNode);

        AbstractInsnNode start = null;

        for (AbstractInsnNode instruction : clinit.instructions) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode node = (MethodInsnNode) instruction;
                if (node.name.equals("decryptConstantPool")) {
                    start = instruction;
                    break;
                }
            }
        }

        if (start != null) clinit.instructions.insert(start, list);
        else clinit.instructions.insert(list);
    }

    public void registerMethod(ClassWrapper classNode, MethodNode method, int[] code) {

        InsnList list = new InsnList();
//        list.add(new LdcInsnNode(Type.getType("L" + classNode.name + ";")));
//        list.add(new LdcInsnNode(method.name + method.desc));

        list.add(AsmUtils.pushInt(code.length));
        list.add(new IntInsnNode(NEWARRAY, T_INT));

        for (int i = 0; i < code.length; i++) {
            list.add(new InsnNode(DUP));
            list.add(AsmUtils.pushInt(i));
            list.add(AsmUtils.pushInt(code[i]));
            list.add(new InsnNode(IASTORE));
        }

        for (AbstractInsnNode instruction : AsmUtils.getClinit(classNode).instructions) {
            if (instruction instanceof IntInsnNode && ((IntInsnNode) instruction).operand == (short) (classNode.name.hashCode() + method.name.hashCode() +
                    method.desc.hashCode())) {
                method.instructions.insert(instruction.getNext(), list);
                method.instructions.remove(instruction.getNext());
                method.instructions.remove(instruction);
                break;
            }
        }

//        list.add(new MethodInsnNode(INVOKESTATIC, "vm/NativeHandler", "transformMethod", "(Ljava/lang/Class;Ljava/lang/String;[I)V", false));

//        String name = String.valueOf(classNode.name.hashCode() + classNode.methods.hashCode());
//
//        for (ClassWrapper cn : obf.getClasses()) {
//            if (cn.name.equals("vm/NativeHandler")) {
//
//                if (cn.methods.stream().noneMatch(m -> m.name.equals(name))) {
//                    MethodNode mn2 = new MethodNode(ACC_STATIC, name, "()V", null, null);
//                    mn2.instructions.add(new InsnNode(RETURN));
//                    cn.methods.add(mn2);
//
//                    MethodNode clinit = AsmUtils.getClinit(cn);
//                    clinit.instructions.insertBefore(clinit.instructions.getLast(), new MethodInsnNode(INVOKESTATIC, "vm/NativeHandler", name,
//                            "()V", false));
//                }
//
//                MethodNode mn = cn.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElse(null);
//                mn.instructions.insert(list);
//
//            }
//        }
    }

    public boolean safe(MethodNode method) {
        if (method.name.equals("<clinit>")) return false;
//        if (method.name.equals("<init>")) return false;
        if (method.instructions.size() == 0) return false;
        if (method.tryCatchBlocks.size() > 0) return false;
        if (!method.exceptions.isEmpty()) return false;
        if ((method.access & ACC_SYNTHETIC) != 0) return false;
        for (AbstractInsnNode instruction : method.instructions) {
//            if (instruction instanceof FieldInsnNode) return false;
//            if (instruction instanceof TypeInsnNode) return false;
//            if (instruction instanceof MethodInsnNode) return false;
//            if (instruction instanceof MultiANewArrayInsnNode) return false;
            if (instruction instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) instruction;
                if (ldc.cst instanceof Handle || ldc.cst instanceof Type) return false;
            }
//            if (instruction instanceof InvokeDynamicInsnNode) return false;
        }
        return true;
    }
}
