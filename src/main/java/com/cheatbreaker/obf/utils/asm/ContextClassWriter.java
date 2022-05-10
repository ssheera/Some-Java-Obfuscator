package com.cheatbreaker.obf.utils.asm;

import com.cheatbreaker.obf.utils.tree.HierarchyUtils;
import org.objectweb.asm.ClassWriter;

public class ContextClassWriter extends ClassWriter {

    public ContextClassWriter(int flags) {
        super(null, flags);
    }

    protected String getCommonSuperClass(String type1, String type2) {
        return HierarchyUtils.getCommonSuperClass1(type1, type2);
    }


}
