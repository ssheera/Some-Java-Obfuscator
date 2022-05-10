package com.cheatbreaker.obf.utils.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class ClassWrapper extends ClassNode {

    public boolean modify;

    public ClassWrapper(boolean modify) {
        super(589824);
        this.modify = modify;
    }

    public ContextClassWriter createWriter() {
        return this.createWriter(ClassWriter.COMPUTE_FRAMES);
    }

    public ContextClassWriter createWriter(int flags) {
        ContextClassWriter cw = new ContextClassWriter(flags);
        this.accept(cw);
        return cw;
    }

}
