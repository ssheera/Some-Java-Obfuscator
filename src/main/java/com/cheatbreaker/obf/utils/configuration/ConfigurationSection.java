package com.cheatbreaker.obf.utils.configuration;


import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ConfigurationSection {
    Set<String> getKeys(boolean var1);

    Map<String, Object> getValues(boolean var1);

    boolean contains(String var1);

    boolean isSet(String var1);

    String getCurrentPath();

    String getName();

    Configuration getRoot();

    ConfigurationSection getParent();

    Object get(String var1);

    Object get(String var1, Object var2);

    void set(String var1, Object var2);

    ConfigurationSection createSection(String var1);

    ConfigurationSection createSection(String var1, Map<?, ?> var2);

    String getString(String var1);

    String getString(String var1, String var2);

    boolean isString(String var1);

    int getInt(String var1);

    int getInt(String var1, int var2);

    boolean isInt(String var1);

    boolean getBoolean(String var1);

    boolean getBoolean(String var1, boolean var2);

    boolean isBoolean(String var1);

    double getDouble(String var1);

    double getDouble(String var1, double var2);

    boolean isDouble(String var1);

    float getFloat(String var1);

    float getFloat(String var1, float var2);

    boolean isFloat(String var1);

    long getLong(String var1);

    long getLong(String var1, long var2);

    boolean isLong(String var1);

    List<?> getList(String var1);

    List<?> getList(String var1, List<?> var2);

    boolean isList(String var1);

    List<String> getStringList(String var1);

    List<Integer> getIntegerList(String var1);

    List<Boolean> getBooleanList(String var1);

    List<Double> getDoubleList(String var1);

    List<Float> getFloatList(String var1);

    List<Long> getLongList(String var1);

    List<Byte> getByteList(String var1);

    List<Character> getCharacterList(String var1);

    List<Short> getShortList(String var1);

    List<Map<?, ?>> getMapList(String var1);

    ConfigurationSection getConfigurationSection(String var1);

    boolean isConfigurationSection(String var1);

    ConfigurationSection getDefaultSection();

    void addDefault(String var1, Object var2);
}