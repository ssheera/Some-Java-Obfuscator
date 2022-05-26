package com.cheatbreaker.obf.utils.loader;

import java.security.SecureClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoaderUtil extends SecureClassLoader {

    Map<String, byte[]> bytes = new ConcurrentHashMap<>();
    Map<String, Class<?>> classes = new ConcurrentHashMap<>();

    public LoaderUtil(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) {

        if (name.startsWith("java.")) {
            try {
                return super.loadClass(name, resolve);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        if (bytes.containsKey(name)) {
            byte[] b = this.bytes.get(name);
            Class<?> klass = this.defineClass(name, b, 0, b.length);
            if (resolve) resolveClass(klass);
            classes.put(name, klass);
            bytes.remove(name);
        }

        if (classes.containsKey(name)) {
            return classes.get(name);
        }

        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public void addClass(String name, byte[] bytes) {
        if (name.startsWith("java/lang")) return;
        this.bytes.put(name.replace("/", "."), bytes);
    }
}
