package com.cheatbreaker.obf.transformer.general;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

public class StripTransformer extends Transformer {

    public StripTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public String getSection() {
        return "general.strip";
    }

    @Override
    protected void after() {
        for (ClassWrapper classNode : obf.getClasses()) {
            for (MethodNode method : classNode.methods) {
                method.localVariables = null;
                method.parameters = null;
                method.signature = null;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof LineNumberNode) {
                        method.instructions.remove(instruction);
                    }
                }
            }
            for (FieldNode field : classNode.fields)
                field.signature = null;
            classNode.signature = null;
            classNode.innerClasses.clear();
        }
    }
}
