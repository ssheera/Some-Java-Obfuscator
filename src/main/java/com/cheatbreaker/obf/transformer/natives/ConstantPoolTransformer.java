package com.cheatbreaker.obf.transformer.natives;

import com.cheatbreaker.obf.Obf;
import com.cheatbreaker.obf.transformer.Transformer;

public class ConstantPoolTransformer extends Transformer {
    public ConstantPoolTransformer(Obf obf) {
        super(obf);
    }

    @Override
    public String getSection() {
        return "natives.constantpool";
    }
}
