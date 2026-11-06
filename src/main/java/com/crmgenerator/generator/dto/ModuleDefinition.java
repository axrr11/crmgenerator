package com.crmgenerator.generator.dto;

import com.crmgenerator.generator.enums.ModuleType;
import com.crmgenerator.generator.support.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ModuleDefinition(
        String name,
        String slug,
        String table,
        String entityClass,
        ModuleType type,
        List<FieldDefinition> fields,
        String kanbanField,
        boolean hasOwner,
        boolean hasActivities,
        String description,
        String metricsSourceEntity,
        String metricsSourceSlug,
        List<Map<String, String>> metrics
) {
    @SuppressWarnings("unchecked")
    public static ModuleDefinition fromRequest(Map<String, Object> data) {
        String name = String.valueOf(data.getOrDefault("name", "")).trim();
        String slugRaw = data.get("slug") != null ? String.valueOf(data.get("slug")) : name;
        String slug = Identifier.table(slugRaw);
        String table = "crm_" + slug;
        ModuleType type = ModuleType.from(String.valueOf(data.getOrDefault("type", "catalog")));

        List<FieldDefinition> fields = new ArrayList<>();
        Object rawFields = data.get("fields");
        if (rawFields instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    fields.add(FieldDefinition.fromMap((Map<String, Object>) map));
                }
            }
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Debe definir al menos un campo");
        }

        String kanbanField = data.get("kanban_field") != null ? String.valueOf(data.get("kanban_field")) : null;
        if (type == ModuleType.KANBAN && kanbanField == null) {
            kanbanField = fields.stream()
                    .map(FieldDefinition::name)
                    .filter(n -> n.equals("estado") || n.equals("status"))
                    .findFirst()
                    .orElse(fields.get(0).name());
        }

        List<Map<String, String>> metrics = new ArrayList<>();
        Object rawMetrics = data.get("metrics");
        if (rawMetrics instanceof List<?> ml) {
            for (Object item : ml) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("field", String.valueOf(map.get("field") != null ? map.get("field") : "id"));
                    m.put("aggregation", String.valueOf(map.get("aggregation") != null ? map.get("aggregation") : "count"));
                    m.put("label", String.valueOf(map.get("label") != null ? map.get("label") : "KPI"));
                    metrics.add(m);
                }
            }
        }

        return new ModuleDefinition(
                name,
                slug,
                table,
                "Crm" + Identifier.className(name),
                type,
                fields,
                kanbanField,
                bool(data.get("has_owner")),
                data.get("has_activities") == null || bool(data.get("has_activities")),
                data.get("description") != null ? String.valueOf(data.get("description")) : null,
                data.get("metrics_source_entity") != null ? String.valueOf(data.get("metrics_source_entity")) : null,
                data.get("metrics_source_slug") != null ? String.valueOf(data.get("metrics_source_slug")) : null,
                metrics
        );
    }

    private static boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o == null) return false;
        String s = String.valueOf(o);
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
    }
}
