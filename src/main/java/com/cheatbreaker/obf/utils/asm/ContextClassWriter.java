package com.cheatbreaker.obf.utils.asm;

import com.cheatbreaker.obf.utils.tree.HierarchyUtils;
import org.objectweb.asm.ClassWriter;

import java.util.List;

public class ContextClassWriter extends ClassWriter {

    public ContextClassWriter(int flags) {
        super(null, flags);
    }

    public ContextClassWriter(int flags, boolean b, List<String> l) {
        super(null, flags, b, l);
    }

    protected String getCommonSuperClass(String type1, String type2) {
        return HierarchyUtils.getCommonSuperClass1(type1, type2);
    }


}
