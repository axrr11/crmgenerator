package com.crmgenerator.service;

import com.crmgenerator.domain.CrmModule;
import com.crmgenerator.generator.support.Identifier;
import com.crmgenerator.repository.CrmModuleRepository;
import com.crmgenerator.repository.UserAccountRepository;
import com.crmgenerator.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DynamicCrudService {

    private static final Logger log = LoggerFactory.getLogger(DynamicCrudService.class);
    private static final Pattern SAFE_COL = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Set<String> SKIP_PARAMS = Set.of("_csrf", "slug", "id", "page", "q", "sort", "dir");

    private final JdbcTemplate jdbc;
    private final CrmModuleRepository modules;
    private final UserAccountRepository users;

    public DynamicCrudService(JdbcTemplate jdbc, CrmModuleRepository modules, UserAccountRepository users) {
        this.jdbc = jdbc;
        this.modules = modules;
        this.users = users;
    }

    public List<Map<String, Object>> listUsersLookup() {
        return listUsersLookup(null);
    }

    public List<Map<String, Object>> listUsersLookup(Long tenantId) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<UserAccount> list = tenantId != null ? users.findByTenantIdOrderByNameAsc(tenantId) : users.findAll();
        for (UserAccount u : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("email", u.getEmail());
            out.add(m);
        }
        return out;
    }

    public CrmModule requireModule(String slug) {
        return modules.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado: " + slug));
    }

    public CrmModule requireModule(String slug, Long tenantId) {
        if (tenantId == null) return requireModule(slug);
        return modules.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado: " + slug));
    }

    public List<String> columnNames(CrmModule mod) {
        List<String> cols = new ArrayList<>();
        if (mod.getFields() != null) {
            for (Map<String, Object> f : mod.getFields()) {
                String n = String.valueOf(f.get("name"));
                if (SAFE_COL.matcher(n).matches()) cols.add(n);
            }
        }
        if (mod.isHasOwner()) {
            cols.add("owner_id");
            cols.add("owner_name");
        }
        if ("sales".equalsIgnoreCase(mod.getType())) {
            cols.add("cliente_id");
            cols.add("producto_id");
        }
        return cols;
    }

    public List<Map<String, Object>> listProductsLookup() {
        return listProductsLookup(null);
    }

    public List<Map<String, Object>> listProductsLookup(Long tenantId) {
        try {
            CrmModule products = tenantId != null
                    ? modules.findByTenantIdAndSlug(tenantId, "productos").orElse(null)
                    : modules.findBySlug("productos").orElse(null);
            if (products == null) return List.of();
            String table = safeTable(products.getTableName());
            return jdbc.queryForList(
                    "SELECT id, nombre, precio FROM `" + table + "` WHERE deleted_at IS NULL ORDER BY nombre LIMIT 500");
        } catch (Exception e) {
            log.warn("lookup productos: {}", e.getMessage());
            return List.of();
        }
    }

    public void ensureProductoColumn(CrmModule mod) {
        if (!"sales".equalsIgnoreCase(mod.getType())) return;
        String table = safeTable(mod.getTableName());
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'producto_id'",
                    Integer.class, table);
            if (cnt != null && cnt == 0) {
                jdbc.execute("ALTER TABLE `" + table + "` ADD COLUMN `producto_id` BIGINT NULL");
            }
        } catch (Exception e) {
            log.warn("producto_id column: {}", e.getMessage());
        }
    }


    /** Ensures cliente_id exists on sales tables (for packs created before this feature). */
    public void ensureClienteColumn(CrmModule mod) {
        if (!"sales".equalsIgnoreCase(mod.getType())) return;
        String table = safeTable(mod.getTableName());
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'cliente_id'",
                    Integer.class, table);
            if (cnt != null && cnt == 0) {
                jdbc.execute("ALTER TABLE `" + table + "` ADD COLUMN `cliente_id` BIGINT NULL");
            }
        } catch (Exception e) {
            log.warn("No se pudo asegurar columna cliente_id: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> listClientsLookup() {
        return listClientsLookup(null);
    }

    public List<Map<String, Object>> listClientsLookup(Long tenantId) {
        try {
            CrmModule clients = tenantId != null
                    ? modules.findByTenantIdAndSlug(tenantId, "clientes").orElse(null)
                    : modules.findBySlug("clientes").orElse(null);
            if (clients == null) return List.of();
            String table = safeTable(clients.getTableName());
            return jdbc.queryForList("SELECT id, nombre FROM `" + table + "` WHERE deleted_at IS NULL ORDER BY nombre LIMIT 500");
        } catch (Exception e) {
            log.warn("lookup clientes: {}", e.getMessage());
            return List.of();
        }
    }

    public String findClientName(Object clienteId) {
        if (clienteId == null) return null;
        try {
            CrmModule clients = modules.findBySlug("clientes").orElse(null);
            if (clients == null) return null;
            String table = safeTable(clients.getTableName());
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT nombre FROM `" + table + "` WHERE id = ? AND deleted_at IS NULL", clienteId);
            if (rows.isEmpty()) return "#" + clienteId;
            Object n = rows.get(0).get("nombre");
            return n != null ? String.valueOf(n) : ("#" + clienteId);
        } catch (Exception e) {
            return "#" + clienteId;
        }
    }

    private Map<String, String> fieldTypes(CrmModule mod) {
        Map<String, String> types = new HashMap<>();
        if (mod.getFields() != null) {
            for (Map<String, Object> f : mod.getFields()) {
                types.put(String.valueOf(f.get("name")), String.valueOf(f.getOrDefault("type", "string")).toLowerCase());
            }
        }
        return types;
    }

    private Set<String> requiredFields(CrmModule mod) {
        Set<String> req = new HashSet<>();
        if (mod.getFields() != null) {
            for (Map<String, Object> f : mod.getFields()) {
                Object r = f.get("required");
                boolean isReq = Boolean.TRUE.equals(r) || "true".equalsIgnoreCase(String.valueOf(r)) || Integer.valueOf(1).equals(r);
                if (isReq) req.add(String.valueOf(f.get("name")));
            }
        }
        return req;
    }

    public List<Map<String, Object>> list(CrmModule mod, String q, String sort, String dir, int page, int size) {
        List<String> cols = columnNames(mod);
        String table = safeTable(mod.getTableName());
        ensureSoftDeleteColumn(table);
        StringBuilder sql = new StringBuilder("SELECT * FROM `" + table + "` WHERE deleted_at IS NULL");
        List<Object> args = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String term = "%" + q.trim().substring(0, Math.min(q.trim().length(), 120)) + "%";
            List<String> searchable = cols.stream().limit(4).toList();
            if (!searchable.isEmpty()) {
                sql.append(" AND (");
                for (int i = 0; i < searchable.size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    sql.append("CAST(`").append(searchable.get(i)).append("` AS CHAR) LIKE ?");
                    args.add(term);
                }
                sql.append(")");
            }
        }

        String sortCol = (sort != null && cols.contains(sort)) ? sort : "id";
        String sortDir = "asc".equalsIgnoreCase(dir) ? "ASC" : "DESC";
        sql.append(" ORDER BY `").append(sortCol).append("` ").append(sortDir);
        sql.append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add(Math.max(0, page) * size);

        try {
            return jdbc.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            log.error("LIST failed table={} sql={} err={}", table, sql, e.getMessage());
            throw new IllegalStateException("No se pudo listar «" + mod.getSlug() + "»: " + e.getMessage(), e);
        }
    }

    public long count(CrmModule mod, String q) {
        String table = safeTable(mod.getTableName());
        if (q == null || q.isBlank()) {
            Long c = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "` WHERE deleted_at IS NULL", Long.class);
            return c != null ? c : 0;
        }
        List<String> cols = columnNames(mod).stream().limit(4).toList();
        String term = "%" + q.trim().substring(0, Math.min(q.trim().length(), 120)) + "%";
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM `" + table + "` WHERE deleted_at IS NULL AND (");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("`").append(cols.get(i)).append("` LIKE ?");
            args.add(term);
        }
        sql.append(")");
        Long c = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return c != null ? c : 0;
    }

    public Map<String, Object> find(CrmModule mod, long id) {
        String table = safeTable(mod.getTableName());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM `" + table + "` WHERE id = ? AND deleted_at IS NULL", id);
        if (rows.isEmpty()) throw new NoSuchElementException("Registro no encontrado");
        return rows.get(0);
    }

    public long create(CrmModule mod, Map<String, ?> data) {
        LinkedHashMap<String, Object> filtered = prepareData(mod, data, true);
        if (filtered.isEmpty()) {
            throw new IllegalArgumentException("Sin datos válidos para insertar. Revisa los nombres de los campos.");
        }

        String table = safeTable(mod.getTableName());
        String colList = filtered.keySet().stream().map(c -> "`" + c + "`").collect(Collectors.joining(","));
        String placeholders = filtered.keySet().stream().map(c -> "?").collect(Collectors.joining(","));
        String sql = "INSERT INTO `" + table + "` (" + colList + ") VALUES (" + placeholders + ")";

        log.info("INSERT {} cols={}", table, filtered.keySet());

        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                for (Object v : filtered.values()) {
                    ps.setObject(i++, v);
                }
                return ps;
            }, keys);
        } catch (Exception e) {
            log.error("Error INSERT {}: {}", table, e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "error SQL";
            if (msg.toLowerCase().contains("data truncation") || msg.toLowerCase().contains("out of range")) {
                msg += " — Si el campo es un documento o teléfono, recréalo como tipo Texto (no Número entero).";
            }
            throw new IllegalArgumentException("No se pudo guardar: " + msg, e);
        }

        Number key = keys.getKey();
        if (key == null && keys.getKeys() != null && keys.getKeys().containsKey("id")) {
            key = (Number) keys.getKeys().get("id");
        }
        return key != null ? key.longValue() : 0L;
    }

    public void update(CrmModule mod, long id, Map<String, ?> data) {
        LinkedHashMap<String, Object> filtered = prepareData(mod, data, false);
        if (filtered.isEmpty()) return;

        String table = safeTable(mod.getTableName());
        String sets = filtered.keySet().stream().map(c -> "`" + c + "` = ?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>(filtered.values());
        args.add(id);
        try {
            jdbc.update("UPDATE `" + table + "` SET " + sets + " WHERE id = ? AND deleted_at IS NULL", args.toArray());
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo actualizar: " + e.getMessage(), e);
        }
    }

    public void softDelete(CrmModule mod, long id) {
        String table = safeTable(mod.getTableName());
        jdbc.update("UPDATE `" + table + "` SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public Map<String, Object> aggregateMetrics(CrmModule metricsMod) {
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("chart", List.of());

        String sourceSlug = metricsMod.getMetricsSourceSlug();
        CrmModule source;
        try {
            if (sourceSlug != null && !sourceSlug.isBlank()) {
                if (metricsMod.getTenantId() != null) {
                    source = modules.findByTenantIdAndSlug(metricsMod.getTenantId(), sourceSlug)
                            .orElseThrow(() -> new NoSuchElementException(
                                    "Módulo fuente «" + sourceSlug + "» no encontrado en este negocio"));
                } else {
                    source = requireModule(sourceSlug);
                }
            } else {
                // Métricas sobre su propia tabla (si no es virtual)
                source = metricsMod;
            }

            // Módulo virtual de métricas: debe apuntar a tabla física del source
            if ("metrics".equalsIgnoreCase(source.getType()) && source.getMetricsSourceSlug() != null) {
                throw new IllegalStateException("La fuente de métricas no puede ser otro módulo virtual");
            }

            String table = safeTable(source.getTableName());
            int i = 0;
            if (metricsMod.getMetrics() != null) {
                for (Map<String, String> m : metricsMod.getMetrics()) {
                    String field = m.getOrDefault("field", "id");
                    if (!SAFE_COL.matcher(field).matches()) {
                        kpis.put("kpi_" + i, 0);
                        i++;
                        continue;
                    }
                    String agg = m.getOrDefault("aggregation", "count").toLowerCase();
                    String sql = switch (agg) {
                        case "sum" -> "SELECT COALESCE(SUM(`" + field + "`),0) FROM `" + table + "` WHERE deleted_at IS NULL";
                        case "avg" -> "SELECT COALESCE(AVG(`" + field + "`),0) FROM `" + table + "` WHERE deleted_at IS NULL";
                        case "min" -> "SELECT COALESCE(MIN(`" + field + "`),0) FROM `" + table + "` WHERE deleted_at IS NULL";
                        case "max" -> "SELECT COALESCE(MAX(`" + field + "`),0) FROM `" + table + "` WHERE deleted_at IS NULL";
                        default -> "SELECT COUNT(*) FROM `" + table + "` WHERE deleted_at IS NULL";
                    };
                    try {
                        Object val = jdbc.queryForObject(sql, Object.class);
                        kpis.put("kpi_" + i, val != null ? val : 0);
                    } catch (Exception ex) {
                        log.warn("KPI {} falló en {}: {}", field, table, ex.getMessage());
                        kpis.put("kpi_" + i, 0);
                    }
                    i++;
                }
            }
            try {
                List<Map<String, Object>> chart = jdbc.queryForList(
                        "SELECT DATE(created_at) as period, COUNT(*) as value FROM `" + table +
                                "` WHERE deleted_at IS NULL AND created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                                "GROUP BY DATE(created_at) ORDER BY period");
                kpis.put("chart", chart != null ? chart : List.of());
            } catch (Exception ex) {
                log.warn("Chart metrics falló en {}: {}", table, ex.getMessage());
                kpis.put("chart", List.of());
            }
        } catch (Exception e) {
            log.error("aggregateMetrics: {}", e.getMessage());
            kpis.put("error", e.getMessage());
        }
        return kpis;
    }

    /**
     * Filters request params to allowed columns, coerces types, validates required & non-negative.
     */
    private LinkedHashMap<String, Object> prepareData(CrmModule mod, Map<String, ?> data, boolean forInsert) {
        List<String> allowed = columnNames(mod);
        Map<String, String> types = fieldTypes(mod);
        Set<String> required = requiredFields(mod);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();

        for (String col : allowed) {
            if (!data.containsKey(col)) {
                if (forInsert && required.contains(col)) {
                    throw new IllegalArgumentException("Campo obligatorio faltante: " + col);
                }
                continue;
            }
            Object raw = data.get(col);
            if (raw == null) {
                if (forInsert && required.contains(col)) {
                    throw new IllegalArgumentException("Campo obligatorio vacío: " + col);
                }
                continue;
            }
            String s = String.valueOf(raw).trim();
            if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
                if (forInsert && required.contains(col)) {
                    throw new IllegalArgumentException("Campo obligatorio vacío: " + col);
                }
                // omit optional empty from INSERT so DB default/NULL applies
                continue;
            }

            String type = types.getOrDefault(col, "string");
            if ("cliente_id".equals(col) || "owner_id".equals(col) || "producto_id".equals(col)) {
                type = "integer";
            }
            Object value = coerce(col, type, s);
            out.put(col, value);
        }
        return out;
    }

    private Object coerce(String col, String type, String s) {
        // Documentos / teléfonos largos no deben parsearse como int
        if ("phone".equals(type) || "email".equals(type) || "string".equals(type) || "text".equals(type) || "select".equals(type) || "image".equals(type) || "color".equals(type) || "size".equals(type)) {
            return s;
        }
        try {
            return switch (type) {
                case "integer" -> {
                    // Usar Long: documentos/celulares superan Integer.MAX_VALUE
                    String digits = s.replace(",", "").replace(" ", "").trim();
                    long v = Long.parseLong(digits);
                    if (v < 0) throw new IllegalArgumentException("No se permiten valores negativos: " + col);
                    if (v <= Integer.MAX_VALUE) yield (int) v;
                    yield v; // BIGINT-compatible
                }
                case "decimal" -> {
                    BigDecimal v = new BigDecimal(s.replace(",", ".").replace(" ", ""));
                    if (v.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("No se permiten valores negativos: " + col);
                    }
                    yield v;
                }
                case "boolean" -> "1".equals(s) || "true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
                case "date" -> LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
                case "datetime" -> s.contains("T") ? LocalDateTime.parse(s) : LocalDateTime.parse(s.replace(" ", "T"));
                default -> s;
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Valor inválido en «" + col + "»: " + s + " (tipo " + type + ")");
        }
    }

    private void ensureSoftDeleteColumn(String table) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'deleted_at'",
                    Integer.class, table);
            if (cnt != null && cnt == 0) {
                jdbc.execute("ALTER TABLE `" + table + "` ADD COLUMN `deleted_at` DATETIME NULL");
            }
        } catch (Exception e) {
            log.warn("ensureSoftDeleteColumn {}: {}", table, e.getMessage());
        }
    }

    private String safeTable(String table) {
        if (table == null || !table.startsWith("crm_")) {
            throw new IllegalArgumentException("Tabla no permitida");
        }
        String rest = table.substring(4).replaceAll("^t[0-9]+_", "");
        Identifier.table(rest);
        return table;
    }
}
