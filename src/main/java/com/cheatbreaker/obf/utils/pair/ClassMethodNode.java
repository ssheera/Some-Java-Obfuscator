package com.cheatbreaker.obf.utils.pair;

import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import org.objectweb.asm.tree.MethodNode;

public class ClassMethodNode {

    private final ClassWrapper classNode;
    private final MethodNode methodNode;

    public ClassMethodNode(ClassWrapper classNode, MethodNode methodNode) {
        this.classNode = classNode;
        this.methodNode = methodNode;
    }

    public ClassWrapper getClassWrapper() {
        return classNode;
    }

    public MethodNode getMethodNode() {
        return methodNode;
    }

    @Override
    public String toString() {
        return classNode.name + "." + methodNode.name + methodNode.desc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
