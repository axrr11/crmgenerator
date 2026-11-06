package com.crmgenerator.generator;

import com.crmgenerator.domain.CrmModule;
import com.crmgenerator.generator.dto.FieldDefinition;
import com.crmgenerator.generator.dto.ModuleDefinition;
import com.crmgenerator.generator.enums.FieldType;
import com.crmgenerator.generator.enums.ModuleType;
import com.crmgenerator.generator.support.Identifier;
import com.crmgenerator.repository.CrmModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.LinkedHashMap;

/**
 * Creates physical tables via JDBC and registers module metadata.
 * Runtime CRUD uses metadata + JdbcTemplate (no hot-reload of Java classes needed).
 */
@Service
public class ModuleGeneratorEngine {

    private static final Logger log = LoggerFactory.getLogger(ModuleGeneratorEngine.class);

    private final JdbcTemplate jdbc;
    private final CrmModuleRepository modules;

    public ModuleGeneratorEngine(JdbcTemplate jdbc, CrmModuleRepository modules) {
        this.jdbc = jdbc;
        this.modules = modules;
    }

    @Transactional
    public Map<String, Object> generate(ModuleDefinition def) {
        return generate(def, null);
    }

    @Transactional
    public Map<String, Object> generate(ModuleDefinition def, Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId es obligatorio para generar módulos");
        }
        if (modules.existsByTenantIdAndSlug(tenantId, def.slug())) {
            throw new IllegalArgumentException("Ya existe un módulo con slug: " + def.slug() + " en este negocio");
        }

        // Tabla aislada por tenant: crm_t{tenantId}_{slug}
        ModuleDefinition scoped = withTenantTable(def, tenantId);

        if (scoped.type() != ModuleType.METRICS || scoped.metricsSourceEntity() == null) {
            createTable(scoped);
        }

        CrmModule mod = persistMeta(scoped, tenantId);

        CrmModule companion = null;
        if (scoped.type() == ModuleType.SALES) {
            try {
                ModuleDefinition metrics = buildSalesMetricsCompanion(scoped);
                metrics = withTenantTable(metrics, tenantId);
                if (!modules.existsByTenantIdAndSlug(tenantId, metrics.slug())) {
                    companion = persistMeta(metrics, tenantId);
                }
            } catch (Exception e) {
                log.warn("No se pudo crear companion de métricas: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", mod);
        result.put("companion", companion);
        return result;
    }

    private void createTable(ModuleDefinition def) {
        String table = def.table();
        // Validate table name strictly
        String check = table.startsWith("crm_") ? table.substring(4) : table;
        Identifier.table(check.replaceAll("^t[0-9]+_", ""));

        StringBuilder ddl = new StringBuilder();
        // Siempre partir de tabla vacía al crear desde el constructor
        forceDropTable(table);
        ddl.append("CREATE TABLE `").append(table).append("` (");
        ddl.append("`id` BIGINT AUTO_INCREMENT PRIMARY KEY,");

        for (FieldDefinition f : def.fields()) {
            ddl.append("`").append(f.name()).append("` ").append(f.type().sqlType());
            if (f.required()) ddl.append(" NOT NULL");
            else ddl.append(" NULL");
            if (f.unique()) ddl.append(" UNIQUE");
            ddl.append(",");
        }

        if (def.hasOwner()) {
            ddl.append("`owner_id` BIGINT NULL,");
            ddl.append("`owner_name` VARCHAR(120) NULL,");
        }
        if (def.type() == ModuleType.SALES) {
            ddl.append("`cliente_id` BIGINT NULL,");
            ddl.append("`producto_id` BIGINT NULL,");
        }
        ddl.append("`tenant_id` BIGINT NULL,");
        ddl.append("`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,");
        ddl.append("`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,");
        ddl.append("`deleted_at` DATETIME NULL");
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbc.execute(ddl.toString());
        log.info("Tabla creada: {}", table);
    }

    private ModuleDefinition withTenantTable(ModuleDefinition def, Long tenantId) {
        String table = "crm_t" + tenantId + "_" + def.slug();
        return new ModuleDefinition(
                def.name(), def.slug(), table, def.entityClass(), def.type(), def.fields(),
                def.kanbanField(), def.hasOwner(), def.hasActivities(), def.description(),
                def.metricsSourceEntity(), def.metricsSourceSlug(), def.metrics()
        );
    }

    private CrmModule persistMeta(ModuleDefinition def, Long tenantId) {
        CrmModule m = new CrmModule();
        m.setTenantId(tenantId);
        m.setName(def.name());
        m.setSlug(def.slug());
        m.setTableName(def.table());
        m.setType(def.type().name().toLowerCase());
        m.setDescription(def.description());
        List<Map<String, Object>> fields = new ArrayList<>();
        for (FieldDefinition f : def.fields()) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("name", f.name());
            fm.put("label", f.label());
            fm.put("type", f.type().name().toLowerCase());
            fm.put("required", f.required());
            fm.put("unique", f.unique());
            fm.put("options", f.options());
            fields.add(fm);
        }
        m.setFields(fields);
        m.setMetrics(def.metrics());
        m.setKanbanField(def.kanbanField());
        m.setHasOwner(def.hasOwner());
        m.setHasActivities(def.hasActivities());
        m.setMetricsSourceSlug(def.metricsSourceSlug());
        return modules.save(m);
    }

    private ModuleDefinition buildSalesMetricsCompanion(ModuleDefinition sales) {
        List<Map<String, String>> metrics = new ArrayList<>();
        metrics.add(metric("id", "count", "Nº de ventas"));

        Optional<FieldDefinition> total = sales.fields().stream()
                .filter(f -> f.type() == FieldType.DECIMAL || f.type() == FieldType.INTEGER)
                .filter(f -> List.of("total", "deuda", "monto", "importe", "monto_total").contains(f.name()))
                .findFirst()
                .or(() -> sales.fields().stream()
                        .filter(f -> f.type() == FieldType.DECIMAL || f.type() == FieldType.INTEGER)
                        .findFirst());

        total.ifPresent(f -> {
            metrics.add(metric(f.name(), "sum", "Suma " + f.label()));
            metrics.add(metric(f.name(), "avg", "Promedio " + f.label()));
        });

        sales.fields().stream()
                .filter(f -> List.of("monto_pagado", "abonado", "pagado").contains(f.name()))
                .findFirst()
                .ifPresent(f -> metrics.add(metric(f.name(), "sum", "Total cobrado")));

        sales.fields().stream()
                .filter(f -> List.of("saldo", "pendiente").contains(f.name()))
                .findFirst()
                .ifPresent(f -> metrics.add(metric(f.name(), "sum", "Saldo pendiente")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Métricas · " + sales.name());
        data.put("slug", "metricas-" + sales.slug());
        data.put("type", "metrics");
        data.put("description", "Indicadores del módulo " + sales.name());
        data.put("fields", List.of(Map.of("name", "nombre", "label", "Nombre", "type", "string")));
        data.put("metrics", metrics);
        data.put("has_owner", false);
        data.put("has_activities", false);
        data.put("metrics_source_entity", sales.entityClass());
        data.put("metrics_source_slug", sales.slug());
        return ModuleDefinition.fromRequest(data);
    }

    private static Map<String, String> metric(String field, String aggregation, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("aggregation", aggregation);
        m.put("label", label);
        return m;
    }

    /**
     * Drops physical table (if any) and removes meta record.
     * Also removes companion metrics modules that reference this slug.
     */
    @Transactional
    public void deleteModule(String slug) {
        deleteModule(slug, null);
    }

    @Transactional
    public void deleteModule(String slug, Long tenantId) {
        CrmModule mod = (tenantId != null)
                ? modules.findByTenantIdAndSlug(tenantId, slug)
                    .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado: " + slug))
                : modules.findBySlug(slug)
                    .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado: " + slug));

        // Companions metrics first (meta only; virtual)
        List<CrmModule> companions = modules.findAll().stream()
                .filter(m -> slug.equals(m.getMetricsSourceSlug()))
                .toList();
        for (CrmModule c : companions) {
            modules.delete(c);
            log.info("Companion métricas eliminado: {}", c.getSlug());
        }

        // Always force-drop physical table when it is a crm_* table
        boolean virtualMetrics = "metrics".equalsIgnoreCase(mod.getType()) && mod.getMetricsSourceSlug() != null;
        if (!virtualMetrics && mod.getTableName() != null) {
            String table = mod.getTableName().replace("`", "").trim();
            if (table.startsWith("crm_")) {
                forceDropTable(table);
                log.info("Tabla eliminada: {}", table);
            }
        }

        // Clean related payments / activities for this module
        try {
            jdbc.update("DELETE FROM crm_payments WHERE subject_type = ?", "module:" + slug);
        } catch (Exception ignored) {}
        try {
            jdbc.update("DELETE FROM crm_activities WHERE subject_type = ?", "module:" + slug);
        } catch (Exception ignored) {}

        modules.delete(mod);
        log.info("Módulo eliminado: {}", slug);
    }


    /**
     * DROP real de la tabla. Verifica que ya no exista.
     */
    public void forceDropTable(String table) {
        if (table == null || !table.startsWith("crm_")) {
            throw new IllegalArgumentException("Tabla no permitida: " + table);
        }

        try {
            jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
            jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
            jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo eliminar la tabla «" + table + "»: " + e.getMessage(), e);
        }
        Integer still = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        if (still != null && still > 0) {
            throw new IllegalStateException("La tabla «" + table + "» sigue existiendo tras el DROP.");
        }
    }

    @Transactional
    public CrmModule updateModuleMeta(String slug, String name, String description) {
        return updateModuleMeta(slug, null, name, description);
    }

    @Transactional
    public CrmModule updateModuleMeta(String slug, Long tenantId, String name, String description) {
        CrmModule mod = (tenantId != null)
                ? modules.findByTenantIdAndSlug(tenantId, slug)
                    .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado: " + slug))
                : modules.findBySlug(slug)
                    .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado: " + slug));
        if (name != null && !name.isBlank()) {
            mod.setName(name.trim());
        }
        mod.setDescription(description != null ? description.trim() : null);
        return modules.save(mod);
    }

    @Transactional
    public void addField(String slug, String name, String label, String type, boolean required) {
        addField(slug, null, name, label, type, required);
    }

    @Transactional
    public void addField(String slug, Long tenantId, String name, String label, String type, boolean required) {
        CrmModule mod = (tenantId != null)
                ? modules.findByTenantIdAndSlug(tenantId, slug)
                    .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado: " + slug))
                : modules.findBySlug(slug)
                    .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado: " + slug));
        String col = Identifier.column(name);
        String sqlType = switch (type.toLowerCase()) {
            case "integer" -> "BIGINT";
            case "decimal" -> "DECIMAL(12,2)";
            case "boolean" -> "BOOLEAN";
            case "date" -> "DATE";
            case "datetime" -> "DATETIME";
            case "text" -> "TEXT";
            case "color", "size", "image", "select", "phone", "email", "string" -> "VARCHAR(255)";
            default -> "VARCHAR(255)";
        };
        boolean virtual = "metrics".equalsIgnoreCase(mod.getType()) && mod.getMetricsSourceSlug() != null;
        if (!virtual) {
            String nullability = required ? "NOT NULL" : "NULL";
            String defaultClause = required ? " DEFAULT " + ("integer".equalsIgnoreCase(type) || "decimal".equalsIgnoreCase(type) ? "0" : ("boolean".equalsIgnoreCase(type) ? "0" : "''")) : "";
            jdbc.execute("ALTER TABLE `" + mod.getTableName() + "` ADD COLUMN `" + col + "` " + sqlType + " " + nullability + defaultClause);
        }
        List<Map<String, Object>> fields = mod.getFields() != null ? new ArrayList<>(mod.getFields()) : new ArrayList<>();
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("name", col);
        fm.put("label", label != null && !label.isBlank() ? label : col);
        fm.put("type", type.toLowerCase());
        fm.put("required", required);
        fm.put("options", List.of());
        fields.add(fm);
        mod.setFields(fields);
        modules.save(mod);
        log.info("Campo {} agregado a {}", col, slug);
    }
}

