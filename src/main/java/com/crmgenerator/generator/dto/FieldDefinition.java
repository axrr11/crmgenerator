package com.crmgenerator.generator.dto;

import com.crmgenerator.generator.enums.FieldType;
import com.crmgenerator.generator.support.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record FieldDefinition(
        String name,
        String label,
        FieldType type,
        boolean required,
        boolean unique,
        List<String> options
) {
    public static FieldDefinition fromMap(Map<String, Object> m) {
        String name = Identifier.column(String.valueOf(m.getOrDefault("name", "campo")));
        String label = String.valueOf(m.getOrDefault("label", name));
        FieldType type = FieldType.from(String.valueOf(m.getOrDefault("type", "string")));
        boolean required = bool(m.get("required"));
        boolean unique = bool(m.get("unique"));
        List<String> options = new ArrayList<>();
        Object opts = m.get("options");
        if (opts instanceof List<?> list) {
            for (Object o : list) options.add(String.valueOf(o));
        } else if (opts instanceof String s && !s.isBlank()) {
            for (String part : s.split(",")) options.add(part.trim());
        }
        return new FieldDefinition(name, label, type, required, unique, options);
    }

    private static boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o == null) return false;
        return "1".equals(String.valueOf(o)) || "true".equalsIgnoreCase(String.valueOf(o)) || "on".equalsIgnoreCase(String.valueOf(o));
    }
}
