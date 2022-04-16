package com.cheatbreaker.obf.utils.asm;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.utils.tree.ClassTree;
import com.cheatbreaker.obf.utils.tree.HierarchyUtils;
import org.objectweb.asm.ClassWriter;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class ContextClassWriter extends ClassWriter {


    public ContextClassWriter(int flags) {
        super(flags);
    }

    protected String getCommonSuperClass(String type1, String type2) {
        return HierarchyUtils.getCommonSuperClass1(type1, type2);
    }


}
