package com.cheatbreaker.obf.utils.asm;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.utils.tree.ClassTree;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class FixedClassWriter extends ClassWriter {

    private final Obf obf;

    public FixedClassWriter(Obf obf, int flags) {
        super(flags);
        this.obf = obf;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        return getCommonSuperClass1(type1, type2);
    }

    private String getCommonSuperClass1(String type1, String type2) {
        if (type1.equals("java/lang/Object") || type2.equals("java/lang/Object")) {
            return "java/lang/Object";
        }
        try {
            String a = getCommonSuperClass0(type1, type2);
            String b = getCommonSuperClass0(type2, type1);
            if (!a.equals("java/lang/Object")) {
                return a;
            }
            if (!b.equals("java/lang/Object")) {
                return b;
            }
            ClassNode first = obf.assureLoaded(type1);
            ClassNode second = obf.assureLoaded(type2);
            return getCommonSuperClass(first.superName, second.superName);
        } catch (Exception e) {
            return "java/lang/Object";
        }
    }

    private String getCommonSuperClass0(String type1, String type2) {
        ClassNode first = obf.assureLoaded(type1);
        ClassNode second = obf.assureLoaded(type2);
        if (isAssignableFrom(type1, type2)) {
            return type1;
        } else if (isAssignableFrom(type2, type1)) {
            return type2;
        } else if (Modifier.isInterface(first.access) || Modifier.isInterface(second.access)) {
            return "java/lang/Object";
        } else {
            do {
                type1 = first.superName;
                first = obf.assureLoaded(type1);
            } while (!isAssignableFrom(type1, type2));
            return type1;
        }
    }

    private boolean isAssignableFrom(String type1, String type2) {
        if (type1.equals("java/lang/Object")) {
            return true;
        }
        if (type1.equals(type2)) {
            return true;
        }
        ClassTree firstTree = obf.getClassTree(type1);
        Set<String> allChilds1 = new HashSet<>();
        LinkedList<String> toProcess = new LinkedList<>(firstTree.subClasses);
        while (!toProcess.isEmpty()) {
            String s = toProcess.poll();
            if (allChilds1.add(s)) {
                obf.assureLoaded(s);
                ClassTree tempTree = obf.getClassTree(s);
                toProcess.addAll(tempTree.subClasses);
            }
        }
        return allChilds1.contains(type2);
    }

}
