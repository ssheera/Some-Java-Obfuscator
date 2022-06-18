package com.cheatbreaker.obf.utils.samples;

import lombok.SneakyThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class DynamicInvoke {

    @SneakyThrows
    public static Object invoke(Object... v) {
//    public static Object invoke(Object[] args, Class<?> klass, MethodHandle _name, MethodHandle _desc, MethodHandle invoke, MethodHandle type) {
        v[3] = (String) ((MethodHandle) v[3]).invoke();
        v[2] = (String) ((MethodHandle) v[2]).invoke();
        return ((MethodHandle) v[4]).bindTo(((MethodHandle) v[5]).invokeWithArguments(MethodHandles.publicLookup(), v[1], v[2],
                MethodType.fromMethodDescriptorString(v[3].toString(), ((Class<?>) v[1]).getClassLoader()))).invoke(v[0]);
    }

}
