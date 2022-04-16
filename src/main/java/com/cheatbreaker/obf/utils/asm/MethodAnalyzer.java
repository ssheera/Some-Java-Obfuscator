package com.cheatbreaker.obf.utils.asm;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.utils.tree.HierarchyUtils;
import me.coley.analysis.SimAnalyzer;
import me.coley.analysis.SimInterpreter;
import me.coley.analysis.TypeChecker;
import me.coley.analysis.TypeResolver;
import org.objectweb.asm.Type;

public class MethodAnalyzer extends SimAnalyzer {

    private final Obf obf;

    public MethodAnalyzer(SimInterpreter interpreter) {
        super(interpreter);
        this.obf = Obf.getInstance();
    }

    @Override
    protected TypeChecker createTypeChecker() {
        return (parent, child) -> obf.getClassTree(child.getInternalName())
                .parentClasses.stream().anyMatch(n -> n != null && n.equals(parent.getInternalName()));
    }

    @Override
    protected TypeResolver createTypeResolver() {
        return new TypeResolver() {
            @Override
            public Type common(Type type1, Type type2) {
                String common = HierarchyUtils.getCommonSuperClass1(type1.getInternalName(), type2.getInternalName());
                if (common != null)
                    return Type.getObjectType(common);
                return Type.getObjectType("java/lang/Object");
            }

            @Override
            public Type commonException(Type type1, Type type2) {
                String common = HierarchyUtils.getCommonSuperClass1(type1.getInternalName(), type2.getInternalName());
                if (common != null)
                    return Type.getObjectType(common);
                return Type.getObjectType("java/lang/Throwable");
            }
        };
    }

}
