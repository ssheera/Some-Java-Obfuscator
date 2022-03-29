package com.cheatbreaker.obf.utils.configuration.file;

import com.cheatbreaker.obf.utils.configuration.serialization.ConfigurationSerialization;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class YamlConstructor extends SafeConstructor {
    public YamlConstructor() {
        this.yamlConstructors.put(Tag.MAP, new ConstructCustomObject());
    }

    private class ConstructCustomObject extends ConstructYamlMap {
        private ConstructCustomObject() {
            super();
        }

        public Object construct(Node node) {
            if (node.isTwoStepsConstruction()) {
                throw new YAMLException("Unexpected referential mapping structure. Node: " + node);
            } else {
                Map<?, ?> raw = (Map)super.construct(node);
                if (!raw.containsKey("==")) {
                    return raw;
                } else {
                    Map<String, Object> typed = new LinkedHashMap(raw.size());
                    Iterator var4 = raw.entrySet().iterator();

                    while(var4.hasNext()) {
                        Map.Entry<?, ?> entry = (Map.Entry)var4.next();
                        typed.put(entry.getKey().toString(), entry.getValue());
                    }

                    try {
                        return ConfigurationSerialization.deserializeObject(typed);
                    } catch (IllegalArgumentException var6) {
                        throw new YAMLException("Could not deserialize object", var6);
                    }
                }
            }
        }

        public void construct2ndStep(Node node, Object object) {
            throw new YAMLException("Unexpected referential mapping structure. Node: " + node);
        }
    }
}
