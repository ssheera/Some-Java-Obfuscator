package com.cheatbreaker.obf.addons.skidfuscator;

import com.cheatbreaker.obf.addons.IObfuscator;

import java.io.File;
import java.util.List;

public class SkidfuscatorAddon implements IObfuscator {

    @Override
    public void transform(File input, File output, List<File> includes) {
        StringBuilder args = new StringBuilder();
        args.append("-ph ")
                .append("-li=")
                .append(includes.toString(), 1, includes.toString().length() - 1)
                .append(input.getAbsolutePath())
                .append(" -o=")
                .append(output.getAbsolutePath());

    }
}
