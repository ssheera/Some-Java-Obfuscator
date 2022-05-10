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

package com.cheatbreaker.obf.transformer;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.utils.asm.ClassWrapper;
import com.cheatbreaker.obf.utils.configuration.ConfigurationSection;
import com.cheatbreaker.obf.utils.pair.ClassMethodNode;
import org.objectweb.asm.Opcodes;

import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

public abstract class Transformer implements Opcodes {

    protected final Obf obf;
    protected final ThreadLocalRandom random;
    protected final ConfigurationSection config;
    protected int iterations = 1;

    public static boolean loadedNative;

    protected Vector<String> excluded = new Vector<>();
    protected Vector<String> included = new Vector<>();
    public boolean enabled;
    public ClassMethodNode target;
    public boolean canBeIterated = true;

    public abstract String getSection();

    public Transformer(Obf obf) {
        this.obf = obf;
        this.random = obf.getRandom();
        this.config = obf.getConfig().getConfigurationSection(getSection());

        this.enabled = config.getBoolean("enabled", true);
        this.excluded.addAll(config.getStringList("excluded"));
        this.included.addAll(config.getStringList("included"));
        this.iterations = config.getInt("iterations", 1);
    }


    public void run(ClassWrapper classNode) {
        if (!enabled) return;
        for (String s : excluded) {
            if (classNode.name.startsWith(s)) return;
        }
        for (String s : included) {
            if (!classNode.name.startsWith(s)) return;
        }
        for (int i = 0; i < iterations; i++) {
            visit(classNode);
        }
    }

    protected void visit(ClassWrapper classNode) {}

    public void runAfter() {
        if (!enabled) return;
        after();
    }

    protected void after() {}

    protected boolean nextBoolean(int i) {
        boolean ret = random.nextBoolean();
        for (int j = 0; j < i; j++) {
            ret = random.nextBoolean() && ret;
        }
        return ret;
    }

    protected void error(String message, Object... args) {
        System.err.printf("[" + this.getClass().getSimpleName() + "] " + message + "\n", args);
    }

    protected void log(String message, Object... args) {
        System.out.printf("[" + this.getClass().getSimpleName() + "] " + message + "\n", args);
    }

}
