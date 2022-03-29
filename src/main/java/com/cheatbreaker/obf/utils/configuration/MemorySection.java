package com.cheatbreaker.obf.utils.configuration;


import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class MemorySection implements ConfigurationSection {
    protected final Map<String, Object> map = new LinkedHashMap();
    private final Configuration root;
    private final ConfigurationSection parent;
    private final String path;
    private final String fullPath;

    protected MemorySection() {
        if (!(this instanceof Configuration)) {
            throw new IllegalStateException("Cannot construct a root MemorySection when not a Configuration");
        } else {
            this.path = "";
            this.fullPath = "";
            this.parent = null;
            this.root = (Configuration)this;
        }
    }

    protected MemorySection(ConfigurationSection parent, String path) {
        Validate.notNull(parent, "Parent cannot be null");
        Validate.notNull(path, "Path cannot be null");
        this.path = path;
        this.parent = parent;
        this.root = parent.getRoot();
        Validate.notNull(this.root, "Path cannot be orphaned");
        this.fullPath = createPath(parent, path);
    }

    public Set<String> getKeys(boolean deep) {
        Set<String> result = new LinkedHashSet();
        Configuration root = this.getRoot();
        if (root != null && root.options().copyDefaults()) {
            ConfigurationSection defaults = this.getDefaultSection();
            if (defaults != null) {
                result.addAll(defaults.getKeys(deep));
            }
        }

        this.mapChildrenKeys(result, this, deep);
        return result;
    }

    public Map<String, Object> getValues(boolean deep) {
        Map<String, Object> result = new LinkedHashMap();
        Configuration root = this.getRoot();
        if (root != null && root.options().copyDefaults()) {
            ConfigurationSection defaults = this.getDefaultSection();
            if (defaults != null) {
                result.putAll(defaults.getValues(deep));
            }
        }

        this.mapChildrenValues(result, this, deep);
        return result;
    }

    public boolean contains(String path) {
        return this.get(path) != null;
    }

    public boolean isSet(String path) {
        Configuration root = this.getRoot();
        if (root == null) {
            return false;
        } else if (root.options().copyDefaults()) {
            return this.contains(path);
        } else {
            return this.get(path, (Object)null) != null;
        }
    }

    public String getCurrentPath() {
        return this.fullPath;
    }

    public String getName() {
        return this.path;
    }

    public Configuration getRoot() {
        return this.root;
    }

    public ConfigurationSection getParent() {
        return this.parent;
    }

    public void addDefault(String path, Object value) {
        Validate.notNull(path, "Path cannot be null");
        Configuration root = this.getRoot();
        if (root == null) {
            throw new IllegalStateException("Cannot add default without root");
        } else if (root == this) {
            throw new UnsupportedOperationException("Unsupported addDefault(String, Object) implementation");
        } else {
            root.addDefault(createPath(this, path), value);
        }
    }

    public ConfigurationSection getDefaultSection() {
        Configuration root = this.getRoot();
        Configuration defaults = root == null ? null : root.getDefaults();
        return defaults != null && defaults.isConfigurationSection(this.getCurrentPath()) ? defaults.getConfigurationSection(this.getCurrentPath()) : null;
    }

    public void set(String path, Object value) {
        Validate.notEmpty(path, "Cannot set to an empty path");
        Configuration root = this.getRoot();
        if (root == null) {
            throw new IllegalStateException("Cannot use section without a root");
        } else {
            char separator = root.options().pathSeparator();
            int i1 = -1;
            Object section = this;

            int i2;
            String key;
            while((i1 = path.indexOf(separator, i2 = i1 + 1)) != -1) {
                key = path.substring(i2, i1);
                ConfigurationSection subSection = ((ConfigurationSection)section).getConfigurationSection(key);
                if (subSection == null) {
                    section = ((ConfigurationSection)section).createSection(key);
                } else {
                    section = subSection;
                }
            }

            key = path.substring(i2);
            if (section == this) {
                if (value == null) {
                    this.map.remove(key);
                } else {
                    this.map.put(key, value);
                }
            } else {
                ((ConfigurationSection)section).set(key, value);
            }

        }
    }

    public Object get(String path) {
        return this.get(path, this.getDefault(path));
    }

    public Object get(String path, Object def) {
        Validate.notNull(path, "Path cannot be null");
        if (path.length() == 0) {
            return this;
        } else {
            Configuration root = this.getRoot();
            if (root == null) {
                throw new IllegalStateException("Cannot access section without a root");
            } else {
                char separator = root.options().pathSeparator();
                int i1 = -1;
                Object section = this;

                do {
                    int i2;
                    if ((i1 = path.indexOf(separator, i2 = i1 + 1)) == -1) {
                        String key = path.substring(i2);
                        if (section == this) {
                            Object result = this.map.get(key);
                            return result == null ? def : result;
                        }

                        return ((ConfigurationSection)section).get(key, def);
                    }

                    section = ((ConfigurationSection)section).getConfigurationSection(path.substring(i2, i1));
                } while(section != null);

                return def;
            }
        }
    }

    public ConfigurationSection createSection(String path) {
        Validate.notEmpty(path, "Cannot create section at empty path");
        Configuration root = this.getRoot();
        if (root == null) {
            throw new IllegalStateException("Cannot create section without a root");
        } else {
            char separator = root.options().pathSeparator();
            int i1 = -1;
            Object section = this;

            int i2;
            String key;
            while((i1 = path.indexOf(separator, i2 = i1 + 1)) != -1) {
                key = path.substring(i2, i1);
                ConfigurationSection subSection = ((ConfigurationSection)section).getConfigurationSection(key);
                if (subSection == null) {
                    section = ((ConfigurationSection)section).createSection(key);
                } else {
                    section = subSection;
                }
            }

            key = path.substring(i2);
            if (section == this) {
                ConfigurationSection result = new MemorySection(this, key);
                this.map.put(key, result);
                return result;
            } else {
                return ((ConfigurationSection)section).createSection(key);
            }
        }
    }

    public ConfigurationSection createSection(String path, Map<?, ?> map) {
        ConfigurationSection section = this.createSection(path);
        Iterator var4 = map.entrySet().iterator();

        while(var4.hasNext()) {
            Entry<?, ?> entry = (Entry)var4.next();
            if (entry.getValue() instanceof Map) {
                section.createSection(entry.getKey().toString(), (Map)entry.getValue());
            } else {
                section.set(entry.getKey().toString(), entry.getValue());
            }
        }

        return section;
    }

    public String getString(String path) {
        Object def = this.getDefault(path);
        return this.getString(path, def != null ? def.toString() : null);
    }

    public String getString(String path, String def) {
        Object val = this.get(path, def);
        return val != null ? val.toString() : def;
    }

    public boolean isString(String path) {
        Object val = this.get(path);
        return val instanceof String;
    }

    public int getInt(String path) {
        Object def = this.getDefault(path);
        return this.getInt(path, def instanceof Number ? NumberConversions.toInt(def) : 0);
    }

    public int getInt(String path, int def) {
        Object val = this.get(path, def);
        return val instanceof Number ? NumberConversions.toInt(val) : def;
    }

    public boolean isInt(String path) {
        Object val = this.get(path);
        return val instanceof Integer;
    }

    public boolean getBoolean(String path) {
        Object def = this.getDefault(path);
        return this.getBoolean(path, def instanceof Boolean ? (Boolean)def : false);
    }

    public boolean getBoolean(String path, boolean def) {
        Object val = this.get(path, def);
        return val instanceof Boolean ? (Boolean)val : def;
    }

    public boolean isBoolean(String path) {
        Object val = this.get(path);
        return val instanceof Boolean;
    }

    public double getDouble(String path) {
        Object def = this.getDefault(path);
        return this.getDouble(path, def instanceof Number ? NumberConversions.toDouble(def) : 0.0D);
    }

    public double getDouble(String path, double def) {
        Object val = this.get(path, def);
        return val instanceof Number ? NumberConversions.toDouble(val) : def;
    }

    public boolean isDouble(String path) {
        Object val = this.get(path);
        return val instanceof Double;
    }

    public float getFloat(String path) {
        Object def = this.getDefault(path);
        return this.getFloat(path, def instanceof Float ? NumberConversions.toFloat(def) : 0.0F);
    }

    public float getFloat(String path, float def) {
        Object val = this.get(path, def);
        return val instanceof Float ? NumberConversions.toFloat(val) : def;
    }

    public boolean isFloat(String path) {
        Object val = this.get(path);
        return val instanceof Float;
    }

    public long getLong(String path) {
        Object def = this.getDefault(path);
        return this.getLong(path, def instanceof Number ? NumberConversions.toLong(def) : 0L);
    }

    public long getLong(String path, long def) {
        Object val = this.get(path, def);
        return val instanceof Number ? NumberConversions.toLong(val) : def;
    }

    public boolean isLong(String path) {
        Object val = this.get(path);
        return val instanceof Long;
    }

    public List<?> getList(String path) {
        Object def = this.getDefault(path);
        return this.getList(path, def instanceof List ? (List)def : null);
    }

    public List<?> getList(String path, List<?> def) {
        Object val = this.get(path, def);
        return (List)(val instanceof List ? val : def);
    }

    public boolean isList(String path) {
        Object val = this.get(path);
        return val instanceof List;
    }

    public List<String> getStringList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<String> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(true) {
                Object object;
                do {
                    if (!var4.hasNext()) {
                        return result;
                    }

                    object = var4.next();
                } while(!(object instanceof String) && !this.isPrimitiveWrapper(object));

                result.add(String.valueOf(object));
            }
        }
    }

    public List<Integer> getIntegerList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<Integer> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Integer) {
                    result.add((Integer)object);
                } else if (object instanceof String) {
                    try {
                        result.add(Integer.valueOf((String)object));
                    } catch (Exception var7) {
                    }
                } else if (object instanceof Character) {
                    result.add(Integer.valueOf((Character)object));
                } else if (object instanceof Number) {
                    result.add(((Number)object).intValue());
                }
            }

            return result;
        }
    }

    public List<Boolean> getBooleanList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<Boolean> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Boolean) {
                    result.add((Boolean)object);
                } else if (object instanceof String) {
                    if (Boolean.TRUE.toString().equals(object)) {
                        result.add(true);
                    } else if (Boolean.FALSE.toString().equals(object)) {
                        result.add(false);
                    }
                }
            }

            return result;
        }
    }

    public List<Double> getDoubleList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<Double> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Double) {
                    result.add((Double)object);
                } else if (object instanceof String) {
                    try {
                        result.add(Double.valueOf((String)object));
                    } catch (Exception var7) {
                    }
                } else if (object instanceof Character) {
                    result.add((double)(Character)object);
                } else if (object instanceof Number) {
                    result.add(((Number)object).doubleValue());
                }
            }

            return result;
        }
    }

    public List<Float> getFloatList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<Float> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Float) {
                    result.add((Float)object);
                } else if (object instanceof String) {
                    try {
                        result.add(Float.valueOf((String)object));
                    } catch (Exception var7) {
                    }
                } else if (object instanceof Character) {
                    result.add((float)(Character)object);
                } else if (object instanceof Number) {
                    result.add(((Number)object).floatValue());
                }
            }

            return result;
        }
    }

    public List<Long> getLongList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<Long> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Long) {
                    result.add((Long)object);
                } else if (object instanceof String) {
                    try {
                        result.add(Long.valueOf((String)object));
                    } catch (Exception var7) {
                    }
                } else if (object instanceof Character) {
                    result.add((long)(Character)object);
                } else if (object instanceof Number) {
                    result.add(((Number)object).longValue());
                }
            }

            return result;
        }
    }

    public List<Byte> getByteList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<Byte> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Byte) {
                    result.add((Byte)object);
                } else if (object instanceof String) {
                    try {
                        result.add(Byte.valueOf((String)object));
                    } catch (Exception var7) {
                    }
                } else if (object instanceof Character) {
                    result.add((byte)((Character) object).charValue());
                } else if (object instanceof Number) {
                    result.add(((Number)object).byteValue());
                }
            }

            return result;
        }
    }

    public List<Character> getCharacterList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<Character> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Character) {
                    result.add((Character)object);
                } else if (object instanceof String) {
                    String str = (String)object;
                    if (str.length() == 1) {
                        result.add(str.charAt(0));
                    }
                } else if (object instanceof Number) {
                    result.add((char)((Number)object).intValue());
                }
            }

            return result;
        }
    }

    public List<Short> getShortList(String path) {
        List<?> list = this.getList(path);
        if (list == null) {
            return new ArrayList(0);
        } else {
            List<Short> result = new ArrayList();
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Short) {
                    result.add((Short)object);
                } else if (object instanceof String) {
                    try {
                        result.add(Short.valueOf((String)object));
                    } catch (Exception var7) {
                    }
                } else if (object instanceof Character) {
                    result.add((short) ((Character) object).charValue());
                } else if (object instanceof Number) {
                    result.add(((Number)object).shortValue());
                }
            }

            return result;
        }
    }

    public List<Map<?, ?>> getMapList(String path) {
        List<?> list = this.getList(path);
        List<Map<?, ?>> result = new ArrayList();
        if (list == null) {
            return result;
        } else {
            Iterator var4 = list.iterator();

            while(var4.hasNext()) {
                Object object = var4.next();
                if (object instanceof Map) {
                    result.add((Map)object);
                }
            }

            return result;
        }
    }

    public ConfigurationSection getConfigurationSection(String path) {
        Object val = this.get(path, (Object)null);
        if (val != null) {
            return val instanceof ConfigurationSection ? (ConfigurationSection)val : null;
        } else {
            val = this.get(path, this.getDefault(path));
            return val instanceof ConfigurationSection ? this.createSection(path) : null;
        }
    }

    public boolean isConfigurationSection(String path) {
        Object val = this.get(path);
        return val instanceof ConfigurationSection;
    }

    protected boolean isPrimitiveWrapper(Object input) {
        return input instanceof Integer || input instanceof Boolean || input instanceof Character || input instanceof Byte || input instanceof Short || input instanceof Double || input instanceof Long || input instanceof Float;
    }

    protected Object getDefault(String path) {
        Validate.notNull(path, "Path cannot be null");
        Configuration root = this.getRoot();
        Configuration defaults = root == null ? null : root.getDefaults();
        return defaults == null ? null : defaults.get(createPath(this, path));
    }

    protected void mapChildrenKeys(Set<String> output, ConfigurationSection section, boolean deep) {
        Iterator var5;
        if (section instanceof MemorySection) {
            MemorySection sec = (MemorySection)section;
            var5 = sec.map.entrySet().iterator();

            while(var5.hasNext()) {
                Entry<String, Object> entry = (Entry)var5.next();
                output.add(createPath(section, (String)entry.getKey(), this));
                if (deep && entry.getValue() instanceof ConfigurationSection) {
                    ConfigurationSection subsection = (ConfigurationSection)entry.getValue();
                    this.mapChildrenKeys(output, subsection, deep);
                }
            }
        } else {
            Set<String> keys = section.getKeys(deep);
            var5 = keys.iterator();

            while(var5.hasNext()) {
                String key = (String)var5.next();
                output.add(createPath(section, key, this));
            }
        }

    }

    protected void mapChildrenValues(Map<String, Object> output, ConfigurationSection section, boolean deep) {
        Iterator var5;
        Entry entry;
        if (section instanceof MemorySection) {
            MemorySection sec = (MemorySection)section;
            var5 = sec.map.entrySet().iterator();

            while(var5.hasNext()) {
                entry = (Entry)var5.next();
                output.put(createPath(section, (String)entry.getKey(), this), entry.getValue());
                if (entry.getValue() instanceof ConfigurationSection && deep) {
                    this.mapChildrenValues(output, (ConfigurationSection)entry.getValue(), deep);
                }
            }
        } else {
            Map<String, Object> values = section.getValues(deep);
            var5 = values.entrySet().iterator();

            while(var5.hasNext()) {
                entry = (Entry)var5.next();
                output.put(createPath(section, (String)entry.getKey(), this), entry.getValue());
            }
        }

    }

    public static String createPath(ConfigurationSection section, String key) {
        return createPath(section, key, section == null ? null : section.getRoot());
    }

    public static String createPath(ConfigurationSection section, String key, ConfigurationSection relativeTo) {
        Validate.notNull(section, "Cannot create path without a section");
        Configuration root = section.getRoot();
        if (root == null) {
            throw new IllegalStateException("Cannot create path without a root");
        } else {
            char separator = root.options().pathSeparator();
            StringBuilder builder = new StringBuilder();
            if (section != null) {
                for(ConfigurationSection parent = section; parent != null && parent != relativeTo; parent = parent.getParent()) {
                    if (builder.length() > 0) {
                        builder.insert(0, separator);
                    }

                    builder.insert(0, parent.getName());
                }
            }

            if (key != null && key.length() > 0) {
                if (builder.length() > 0) {
                    builder.append(separator);
                }

                builder.append(key);
            }

            return builder.toString();
        }
    }

    public String toString() {
        Configuration root = this.getRoot();
        return this.getClass().getSimpleName() + "[path='" + this.getCurrentPath() + "', root='" + (root == null ? null : root.getClass().getSimpleName()) + "']";
    }
}
