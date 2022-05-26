package vm;

import java.io.File;

public class NativeHandler {

    static {
        System.load(new File("native.dll").getAbsolutePath());
    }

    /**
     * Decrypts constant pool contents
     * <p>
     * - Ints
     * - Longs
     *
     * @param klass The class whose constant pool is going to be decrypted
     */
    public static native void decryptConstantPool(Class<?> klass);

    /**
     * Replaces the bytecode
     *
     * @param klass    The class which holds the method
     * @param method   Method getting its bytecode replaced
     * @param bytecode New bytecode
     */
    public static native void transformMethod(Class<?> klass, String method, int[] bytecode);

    public static native int[] raw_bytes(Class<?> var0, String var1);

}
