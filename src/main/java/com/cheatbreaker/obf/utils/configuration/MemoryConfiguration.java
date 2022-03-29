package com.cheatbreaker.obf.utils.configuration;


import org.apache.commons.lang3.Validate;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class MemoryConfiguration extends MemorySection implements Configuration {
    protected Configuration defaults;
    protected MemoryConfigurationOptions options;

    public MemoryConfiguration() {
    }

    public MemoryConfiguration(Configuration defaults) {
        this.defaults = defaults;
    }

    public void addDefault(String path, Object value) {
        Validate.notNull(path, "Path may not be null");
        if (this.defaults == null) {
            this.defaults = new MemoryConfiguration();
        }

        this.defaults.set(path, value);
    }

    public void addDefaults(Map<String, Object> defaults) {
        Validate.notNull(defaults, "Defaults may not be null");
        Iterator var2 = defaults.entrySet().iterator();

        while(var2.hasNext()) {
            Entry<String, Object> entry = (Entry)var2.next();
            this.addDefault((String)entry.getKey(), entry.getValue());
        }

    }

    public void addDefaults(Configuration defaults) {
        Validate.notNull(defaults, "Defaults may not be null");
        this.addDefaults(defaults.getValues(true));
    }

    public void setDefaults(Configuration defaults) {
        Validate.notNull(defaults, "Defaults may not be null");
        this.defaults = defaults;
    }

    public Configuration getDefaults() {
        return this.defaults;
    }

    public ConfigurationSection getParent() {
        return null;
    }

    public MemoryConfigurationOptions options() {
        if (this.options == null) {
            this.options = new MemoryConfigurationOptions(this);
        }

        return this.options;
    }
}
