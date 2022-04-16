package com.cheatbreaker.obf.utils.tree;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class HierarchyUtils {

    private static Obf obf = Obf.getInstance();

    public static String getCommonSuperClass0(String type1, String type2) {
        ClassWrapper first = obf.assureLoaded(type1);
        ClassWrapper second = obf.assureLoaded(type2);
        if (isAssignableFrom(type1, type2))
            return type1;
        else
        if (isAssignableFrom(type2, type1))
            return type2;
        else
        if (Modifier.isInterface(first.access)
                || Modifier.isInterface(second.access))
            return "java/lang/Object";
        else {
            do {
                type1 = first.superName;
                first = obf.assureLoaded(type1);
            } while (!isAssignableFrom(type1, type2));
            return type1;
        }
    }

    public static String getCommonSuperClass1(String type1, String type2) {
        if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2))
            return "java/lang/Object";
        String a = getCommonSuperClass0(type1, type2);
        String b = getCommonSuperClass0(type2, type1);
        if (!"java/lang/Object".equals(a))
            return a;
        if (!"java/lang/Object".equals(b))
            return b;
        ClassWrapper first = obf.assureLoaded(type1);
        ClassWrapper second = obf.assureLoaded(type2);
        return getCommonSuperClass1(first.superName, second.superName);
    }

    public static boolean isAssignableFrom(String type1, String type2) {
        if ("java/lang/Object".equals(type1))
            return true;
        if (type1.equals(type2))
            return true;
        obf.assureLoaded(type1);
        obf.assureLoaded(type2);
        ClassTree firstTree = obf.getClassTree(type1);
        Set<String> allChilds1 = new HashSet<>();
        LinkedList<String> toProcess = new LinkedList<>(firstTree.subClasses);
        while (!toProcess.isEmpty()) {
            String s = toProcess.poll();
            if (allChilds1.add(s)) {
                obf. assureLoaded(s);
                ClassTree tempTree = obf.getClassTree(s);
                toProcess.addAll(tempTree.subClasses);
            }
        }
        return allChilds1.contains(type2);
    }
}
