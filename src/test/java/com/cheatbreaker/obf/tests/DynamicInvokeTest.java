package com.cheatbreaker.obf.tests;

import com.cheatbreaker.obf.utils.samples.DynamicInvoke;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class DynamicInvokeTest {

    @SneakyThrows
    @Test
    void test() {

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        DynamicInvoke.invoke(new Object[] { 10 },
                DynamicInvokeTest.class,
                lookup.findStatic(String.class, "valueOf", MethodType.methodType(String.class, Object.class)).bindTo("test2"),
                lookup.findStatic(String.class, "valueOf", MethodType.methodType(String.class, Object.class)).bindTo("(I)V"),
                lookup.findVirtual(MethodHandle.class, "invokeWithArguments", MethodType.methodType(Object.class, Object[].class)),
                lookup.findVirtual(MethodHandles.Lookup.class, "findStatic", MethodType.methodType(MethodHandle.class, Class.class, String.class, MethodType.class)));
    }

    public static void test2(int i) {
        System.out.println(i);
    }
}
