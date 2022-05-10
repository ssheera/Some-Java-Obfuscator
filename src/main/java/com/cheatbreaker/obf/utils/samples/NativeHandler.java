package com.cheatbreaker.obf.utils.samples;

public class NativeHandler {

    /**
     * Decrypts constant pool contents
     *
     *  - Integers
     *  - Longs
     *  - Methods
     *  - Fields
     *
     * @param klass The class whose constant pool is going to be decrypted
     */
    public static native void decryptConstantPool(Class<?> klass);

}
