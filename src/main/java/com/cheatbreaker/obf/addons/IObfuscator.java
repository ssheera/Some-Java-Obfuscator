package com.cheatbreaker.obf.addons;

import lombok.SneakyThrows;

import java.io.File;
import java.util.List;

public interface IObfuscator {

    @SneakyThrows
    void transform(File input, File output, List<File> includes);
}
