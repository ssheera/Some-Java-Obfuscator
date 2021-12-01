/**
 * The MIT License (MIT)
 *
 * Copyright (C) 2018 CheatBreaker, LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cheatbreaker.obf;

import com.cheatbreaker.obf.transformer.*;
import com.cheatbreaker.obf.utils.*;
import com.cheatbreaker.obf.utils.Dictionary;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class Obf {

    private GuardClassLoader classLoader;

    private static Obf instance;

    public static Obf getInstance() {
        return instance;
    }

    private final Map<String, ClassTree> hierachy = new HashMap<>();

    private final Random random;
    private final List<ClassNode> classes = new ArrayList<>();
    private final List<ClassNode> libs = new ArrayList<>();
    private final List<Transformer> transformers = new ArrayList<>();
    private final List<ClassNode> newClasses = new ArrayList<>();
    private final HashMap<String, byte[]> resources = new HashMap<>();

    private final Vector<String> libraries = new Vector<>();

    public void loadJavaRuntime()  {
        String path = System.getProperty("sun.boot.class.path");
        if (path != null) {
            String[] pathFiles = path.split(";");
            for (String lib : pathFiles) {
                if (lib.endsWith(".jar")) {
                    libraries.addElement(lib);
                }
            }
        }
    }

    public void loadJar(File inputFile, boolean lib) throws Exception {
        if (!inputFile.exists()) return;
        JarFile inputJar = new JarFile(inputFile);
        for (Enumeration<JarEntry> iter = inputJar.entries(); iter.hasMoreElements(); ) {
            JarEntry entry = iter.nextElement();
            try (InputStream in = inputJar.getInputStream(entry)) {
                byte[] bytes = IOUtils.toByteArray(in);
                if (entry.getName().endsWith(".class")) {
                    ClassReader reader = new ClassReader(bytes);
                    ClassNode classNode = new ClassNode();
                    reader.accept(classNode, 0);
                    if (lib) libs.add(classNode); else classes.add(classNode);
                    classLoader.addClass(classNode.name, bytes);
                } else {
                    if (!lib) resources.put(entry.getName(), bytes);
                }
            }
        }
    }

    private List<File> walkFolder(File folder) {
        List<File> files = new ArrayList<>();
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                files.addAll(walkFolder(file));
            } else {
                files.add(file);
            }
        }
        return files;
    }

    public void loadHierachy() {
        Set<String> processed = new HashSet<>();
        LinkedList<ClassNode> toLoad = new LinkedList<>(this.classes);
        toLoad.addAll(libs);
        while (!toLoad.isEmpty()) {
            for (ClassNode toProcess : loadHierachy(toLoad.poll())) {
                if (processed.add(toProcess.name)) {
                    toLoad.add(toProcess);
                }
            }
        }
    }

    public ClassTree getClassTree(String classNode) {
        ClassTree tree = hierachy.get(classNode);
        if (tree == null) {
            loadHierachyAll(assureLoaded(classNode));
            return getClassTree(classNode);
        }
        return tree;
    }

    private ClassTree getOrCreateClassTree(String name) {
        return this.hierachy.computeIfAbsent(name, ClassTree::new);
    }

    public List<ClassNode> loadHierachy(ClassNode specificNode) {
        if (specificNode.name.equals("java/lang/Object")) {
            return Collections.emptyList();
        }
        List<ClassNode> toProcess = new ArrayList<>();

        ClassTree thisTree = getOrCreateClassTree(specificNode.name);
        ClassNode superClass;

        superClass = assureLoaded(specificNode.superName);

        if (superClass == null) {
            throw new IllegalArgumentException("Could not load " + specificNode.name);
        }
        ClassTree superTree = getOrCreateClassTree(superClass.name);
        superTree.subClasses.add(specificNode.name);
        thisTree.parentClasses.add(superClass.name);
        toProcess.add(superClass);

        for (String interfaceReference : specificNode.interfaces) {
            ClassNode interfaceNode = assureLoaded(interfaceReference);
            if (interfaceNode == null) {
                throw new IllegalArgumentException("Could not load " + interfaceReference);
            }
            ClassTree interfaceTree = getOrCreateClassTree(interfaceReference);
            interfaceTree.subClasses.add(specificNode.name);
            thisTree.parentClasses.add(interfaceReference);
            toProcess.add(interfaceNode);
        }
        return toProcess;
    }


    public void loadHierachyAll(ClassNode classNode) {
        Set<String> processed = new HashSet<>();
        LinkedList<ClassNode> toLoad = new LinkedList<>();
        toLoad.add(classNode);
        while (!toLoad.isEmpty()) {
            for (ClassNode toProcess : loadHierachy(toLoad.poll())) {
                if (processed.add(toProcess.name)) {
                    toLoad.add(toProcess);
                }
            }
        }
    }


    public Obf(File inputFile, File outputFile, List<File> libs) throws Exception {

        instance = this;
        this.classLoader = new GuardClassLoader();
        Thread.currentThread().setContextClassLoader(this.classLoader);
        loadJavaRuntime();

        for (String library : libraries) {
            System.out.println("Loading runtime: " + library);
            loadJar(new File(library), true);
        }

        for (File folder : libs) {
            for (File lib : walkFolder(folder)) {
                System.out.println("Loading library: " + lib);
                loadJar(lib, true);
            }
        }

        System.out.println("Reading jar...");
        loadJar(inputFile, false);

        random = new Random();

        RandomUtils.setDictionary(Dictionary.KEYWORDS);

        transformers.add(new MethodTransformer(this));
        transformers.add(new SpecialTransformer(this));
        transformers.add(new FieldTransformer(this));
        transformers.add(new StringTransformer(this));

        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(outputFile))) {

            System.out.println("Transforming classes...");
            for (Transformer transformer : transformers) {
                System.out.println("Running " + transformer.getClass().getSimpleName() + "...");
                classes.forEach((transformer::visit));
            }

            for (Transformer transformer : transformers) {
                transformer.after();
            }

            for (ClassNode classNode : classes) {
                classNode.innerClasses.clear();
            }

            System.out.println("Writing classes...");
            for (ClassNode classNode : classes) {
                GuardClassWriter writer = new GuardClassWriter(this, ClassWriter.COMPUTE_FRAMES);
                classNode.accept(writer);
                out.putNextEntry(new JarEntry(classNode.name + ".class"));
                out.write(writer.toByteArray());
            }

            System.out.println("Writing generated classes...");
            for (ClassNode classNode : newClasses) {
                GuardClassWriter writer = new GuardClassWriter(this, ClassWriter.COMPUTE_FRAMES);
                classNode.accept(writer);
                out.putNextEntry(new JarEntry(classNode.name + ".class"));
                out.write(writer.toByteArray());
            }

            System.out.println("Writing resources...");
            resources.forEach((name, data) -> {
                try {
                    out.putNextEntry(new JarEntry(name));
                    out.write(data);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            long difference = outputFile.length() - inputFile.length();

            System.out.println("Output Size: " + (100L * difference / inputFile.length()) + "%");
        }
    }

    public Random getRandom() {
        return random;
    }

    public List<ClassNode> getClasses() {
        return classes;
    }

    public List<ClassNode> getLibs() {
        return libs;
    }

    public List<ClassNode> getNewClasses() {
        return newClasses;
    }

    public void addNewClass(ClassNode classNode) {
        newClasses.add(classNode);
    }

    public ClassNode assureLoaded(String owner) {
        for (ClassNode classNode : classes) {
            if (classNode.name.equals(owner)) return classNode;
        }
        for (ClassNode classNode : libs) {
            if (classNode.name.equals(owner)) return classNode;
        }
        throw new NoClassDefFoundError(owner);
    }
}
