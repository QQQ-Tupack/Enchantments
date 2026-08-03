package me.enchantments.utils;

import org.bukkit.configuration.ConfigurationSection;
import java.util.Map;

public final class TriggerConfig {

    private final String mechanic;
    private final Map<String, Object> params;

    public TriggerConfig(String mechanic, Map<String, Object> params) {
        this.mechanic = mechanic;
        this.params = params;
    }

    public static TriggerConfig from(ConfigurationSection section) {
        if (section == null) return null;
        String mechanic = section.getString("mechanic", "");
        Map<String, Object> params = section.getValues(false);
        params.remove("mechanic");
        return new TriggerConfig(mechanic, params);
    }

    public static TriggerConfig from(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return null;
        String mechanic = String.valueOf(map.get("mechanic"));
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        map.forEach((k, v) -> params.put(String.valueOf(k), v));
        params.remove("mechanic");
        return new TriggerConfig(mechanic, params);
    }

    public String mechanic() {
        return mechanic;
    }

    public Map<String, Object> params() {
        return params;
    }

    public int getInt(String key, int defaultValue) {
        Object o = getNestedValue(key);
        return o instanceof Number n ? n.intValue() : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        Object o = getNestedValue(key);
        return o instanceof Number n ? n.doubleValue() : defaultValue;
    }

    public String getString(String key, String defaultValue) {
        Object o = getNestedValue(key);
        return o != null ? String.valueOf(o) : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object o = getNestedValue(key);
        return o instanceof Boolean b ? b : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<Map<?, ?>> getMapList(String key) {
        Object o = getNestedValue(key);
        if (o instanceof java.util.List<?> list) {
            return (java.util.List<Map<?, ?>>) (java.util.List<?>) list;
        }
        return java.util.List.of();
    }

    public java.util.List<String> getStringList(String key) {
        Object o = getNestedValue(key);
        if (o instanceof java.util.List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return java.util.List.of();
    }

    private Object getNestedValue(String key) {
        String[] parts = key.split("\\.");
        Object current = params;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
