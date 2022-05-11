package com.cheatbreaker.obf.utils.samples;

import java.io.File;

public class NativeHandler {

    static {
        System.load(new File("native\\x64\\Release\\native.dll").getAbsolutePath());
    }

    /**
     * Decrypts constant pool contents
     *
     *  - Ints
     *  - Longs
     *
     * @param klass The class whose constant pool is going to be decrypted
     */
    public static native void decryptConstantPool(Class<?> klass);

}
