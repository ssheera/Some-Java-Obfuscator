package com.cheatbreaker.obf;

import com.cheatbreaker.obf.transformer.misc.ChecksumTransformer;
import com.cheatbreaker.obf.transformer.misc.InlinerTransformer;
import com.cheatbreaker.obf.transformer.methods.ProxyTransformer;
import com.cheatbreaker.obf.transformer.Transformer;
import com.cheatbreaker.obf.utils.asm.FixedClassWriter;
import com.cheatbreaker.obf.utils.configuration.file.YamlConfiguration;
import com.cheatbreaker.obf.utils.tree.ClassTree;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class Obf implements Opcodes {

    private final Map<String, ClassTree> hierachy = new HashMap<>();

    private Manifest manifest;

    @Getter
    private static Obf instance;

    private final ThreadLocalRandom random;
    private final List<ClassNode> classes = new ArrayList<>();
    private final List<ClassNode> libs = new ArrayList<>(65525);
    private final List<Transformer> transformers = new ArrayList<>();
    private final List<ClassNode> newClasses = new ArrayList<>();
    private final YamlConfiguration config;
    private final HashMap<String, byte[]> resources = new HashMap<>();
    private final HashMap<String, byte[]> generated = new HashMap<>();

    public List<Transformer> getTransformers() {
        return transformers;
    }

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
        if (!lib) manifest = inputJar.getManifest();
        for (Enumeration<JarEntry> iter = inputJar.entries(); iter.hasMoreElements(); ) {
            JarEntry entry = iter.nextElement();
            if (entry.isDirectory()) continue;
            try (InputStream in = inputJar.getInputStream(entry)) {
                byte[] bytes = IOUtils.toByteArray(in);
                if (entry.getName().endsWith(".class")) {
                    ClassReader reader = new ClassReader(bytes);
                    ClassNode classNode = new ClassNode();
                    reader.accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                    if (lib) libs.add(classNode); else classes.add(classNode);
                } else {
                    if (!lib) resources.put(entry.getName(), bytes);
                }
            }
        }
    }

    private List<File> walkFolder(File folder) {
        List<File> files = new ArrayList<>();
        if (!folder.isDirectory()) {
            if (folder.getName().endsWith(".jar"))
                files.add(folder);
            return files;
        }
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                files.addAll(walkFolder(file));
            } else {
                if (file.getName().endsWith(".jar"))
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

    public YamlConfiguration getConfig() {
        return config;
    }

    public Obf(YamlConfiguration configuration) throws Exception {

        this.config = configuration;

        File inputFile = new File(config.getString("input"));
        File outputFile = new File(config.getString("output"));
        List<File> libs = config.getStringList("libs").stream().map(File::new).collect(Collectors.toList());

        instance = this;
        loadJavaRuntime();

        LinkedList<Thread> libraryThreads = new LinkedList<>();

        System.out.println("Loading libraries...");

        for (String library : libraries) {
            libraryThreads.add(new Thread(() -> {
                try {
                    loadJar(new File(library), true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }

        for (File folder : libs) {
            for (File lib : walkFolder(folder)) {
                if (lib.getName().startsWith("rt")) {
                    continue;
                }
                libraryThreads.add(new Thread(() -> {
                    try {
                        loadJar(lib, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }));
            }
        }

        for (Thread libraryThread : libraryThreads) {
            libraryThread.start();
        }

        for (Thread libraryThread : libraryThreads) {
            libraryThread.join();
        }

        System.out.println("Reading jar...");

        loadJar(inputFile, false);

        random = ThreadLocalRandom.current();

        transformers.add(new InlinerTransformer(this));
        transformers.add(new ProxyTransformer(this));
        transformers.add(new ChecksumTransformer(this));

        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(outputFile))) {

            System.out.println("Transforming classes...");
            for (Transformer transformer : transformers) {
                System.out.println("Running " + transformer.getClass().getSimpleName() + "...");
                classes.forEach((transformer::run));
            }

            for (ClassNode classNode : classes) {
                classNode.sourceFile = null;
                classNode.sourceDebug = null;
                classNode.innerClasses.clear();
                for (MethodNode method : classNode.methods)
                    method.localVariables = null;
                Collections.shuffle(classNode.methods);
                Collections.shuffle(classNode.fields);
            }

            for (Transformer transformer : transformers) {
                transformer.runAfter();
            }

            System.out.println("Writing classes...");

            for (ClassNode classNode : classes) {

                byte[] b = generated.getOrDefault(classNode.name, null);

                if (b == null) {
                    FixedClassWriter writer = new FixedClassWriter(this, ClassWriter.COMPUTE_FRAMES);
                    try {
                        classNode.accept(writer);
                        b = writer.toByteArray();
                    } catch (Exception ex) {
                        System.out.println("Failed to compute frames for class: " + classNode.name + ", " + ex.getMessage());
                        writer = new FixedClassWriter(this, ClassWriter.COMPUTE_MAXS);
                        classNode.accept(writer);
                        b = writer.toByteArray();
                    }
                }

                if (b != null) {
                    out.putNextEntry(new JarEntry(classNode.name + ".class"));
                    out.write(b);
                }
            }

            System.out.println("Writing generated classes...");
            for (ClassNode classNode : newClasses) {
                FixedClassWriter writer = new FixedClassWriter(this, ClassWriter.COMPUTE_FRAMES);
                classNode.accept(writer);
                out.putNextEntry(new JarEntry(classNode.name + ".class"));
                out.write(writer.toByteArray());
            }

            System.out.println("Writing resources...");
            resources.forEach((name, data) -> {
                try {
                    out.putNextEntry(new JarEntry(name));
                    out.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            out.close();

            long difference = outputFile.length() - inputFile.length();

            System.out.println("Output Size: " + (100L * difference / inputFile.length()) + "%");
        }
    }


    public ThreadLocalRandom getRandom() {
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
        if (owner == null) return null;
        for (ClassNode classNode : classes) {
            if (classNode.name.equals(owner)) return classNode;
        }
        for (ClassNode classNode : libs) {
            if (classNode == null) continue;
            if (classNode.name.equals(owner)) return classNode;
        }
        return null;
//        throw new NoClassDefFoundError(owner);
    }

    public Manifest getManifest() {
        return manifest;
    }

    public void addGeneratedClass(String name, byte[] b) {
        generated.put(name, b);
    }
}
