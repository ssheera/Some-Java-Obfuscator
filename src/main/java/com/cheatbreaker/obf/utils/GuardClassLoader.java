package com.cheatbreaker.obf.utils;

import java.util.HashMap;

public class GuardClassLoader extends ClassLoader {

    private HashMap<String, byte[]> bytes = new HashMap<>();
    private HashMap<String, Class<?>> classes = new HashMap<>();

    public GuardClassLoader() {
        super(GuardClassLoader.class.getClassLoader());
    }

    public void addClass(String name, byte[] bytes) {
        name = name.replace("/", ".");
        this.bytes.put(name, bytes);
    }

    @Override
    public Class<?> loadClass(String name) {
        name = name.replace("/", ".");
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException e) {
            if (bytes.containsKey(name)) {
                Class<?> klass = defineClass(name, bytes.get(name), 0, bytes.get(name).length);
                bytes.remove(name);
                classes.put(name, klass);
                return klass;
            }
            if (classes.containsKey(name)) {
                return classes.get(name);
            }
        }
        return null;
    }
}
