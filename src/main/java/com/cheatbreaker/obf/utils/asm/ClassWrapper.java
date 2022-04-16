package com.cheatbreaker.obf.utils.asm;

import org.objectweb.asm.tree.ClassNode;

public class ClassWrapper extends ClassNode {

    public boolean modify;

    public ClassWrapper(boolean modify) {
        super(589824);
        this.modify = modify;
    }

}
