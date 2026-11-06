package com.crmgenerator.service;

import com.crmgenerator.domain.CrmModule;
import com.crmgenerator.generator.ModuleGeneratorEngine;
import com.crmgenerator.generator.dto.ModuleDefinition;
import com.crmgenerator.repository.CrmModuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Service
public class ModuleBackupService {

    private static final Logger log = LoggerFactory.getLogger(ModuleBackupService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    private final Path backupsRoot;
    private final JdbcTemplate jdbc;
    private final CrmModuleRepository modules;
    private final ModuleGeneratorEngine engine;
    private final TenantContextService tenantCtx;
    private final ObjectMapper mapper;

    public ModuleBackupService(@Value("${crm.backups-dir:backups}") String backupsDir,
                               JdbcTemplate jdbc,
                               CrmModuleRepository modules,
                               ModuleGeneratorEngine engine,
                               TenantContextService tenantCtx) throws IOException {
        this.backupsRoot = Paths.get(backupsDir).toAbsolutePath().normalize();
        Files.createDirectories(this.backupsRoot);
        this.jdbc = jdbc;
        this.modules = modules;
        this.engine = engine;
        this.tenantCtx = tenantCtx;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private Path tenantDir(Long tenantId) throws IOException {
        Path dir = backupsRoot.resolve("tenant_" + tenantId);
        Files.createDirectories(dir);
        return dir;
    }

    public List<Map<String, Object>> listBackups() throws IOException {
        Long tenantId = tenantCtx.requireTenantId();
        // ÚNICAMENTE carpeta del negocio actual (nunca el root ni otros tenant_*)
        Path dir = tenantDir(tenantId);
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.zip")) {
            for (Path p : stream) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fileName", p.getFileName().toString());
                m.put("size", Files.size(p));
                m.put("modified", Files.getLastModifiedTime(p).toInstant());
                boolean belongs = true;
                try (ZipFile zip = new ZipFile(p.toFile())) {
                    ZipEntry meta = zip.getEntry("module.json");
                    if (meta != null) {
                        try (InputStream in = zip.getInputStream(meta)) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> mod = mapper.readValue(in, Map.class);
                            Object tid = mod.get("tenantId");
                            if (tid != null) {
                                long bakTid = Long.parseLong(String.valueOf(tid));
                                if (bakTid != tenantId.longValue()) {
                                    belongs = false; // no mostrar backups de otro negocio
                                }
                            }
                            m.put("slug", mod.get("slug"));
                            m.put("name", mod.get("name"));
                            m.put("type", mod.get("type"));
                            m.put("tableName", mod.get("tableName"));
                            m.put("tenantId", mod.get("tenantId"));
                        }
                    }
                    if (belongs) {
                        ZipEntry rows = zip.getEntry("rows.json");
                        if (rows != null) {
                            try (InputStream in = zip.getInputStream(rows)) {
                                List<?> list = mapper.readValue(in, List.class);
                                m.put("rowCount", list.size());
                            }
                        }
                    }
                } catch (Exception e) {
                    m.put("error", e.getMessage());
                }
                if (belongs) {
                    out.add(m);
                }
            }
        }
        out.sort((a, b) -> String.valueOf(b.get("fileName")).compareTo(String.valueOf(a.get("fileName"))));
        return out;
    }

    public Path createBackup(String slug) throws IOException {
        Long tenantId = tenantCtx.requireTenantId();
        CrmModule mod = modules.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado: " + slug));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", mod.getName());
        meta.put("slug", mod.getSlug());
        meta.put("tableName", mod.getTableName());
        meta.put("type", mod.getType());
        meta.put("description", mod.getDescription());
        meta.put("fields", mod.getFields());
        meta.put("metrics", mod.getMetrics());
        meta.put("kanbanField", mod.getKanbanField());
        meta.put("hasOwner", mod.isHasOwner());
        meta.put("hasActivities", mod.isHasActivities());
        meta.put("metricsSourceSlug", mod.getMetricsSourceSlug());
        meta.put("tenantId", tenantId);
        meta.put("backedUpAt", Instant.now().toString());

        List<Map<String, Object>> rows = List.of();
        boolean virtual = "metrics".equalsIgnoreCase(mod.getType()) && mod.getMetricsSourceSlug() != null;
        if (!virtual && mod.getTableName() != null) {
            try {
                rows = jdbc.queryForList("SELECT * FROM `" + mod.getTableName() + "`");
            } catch (Exception e) {
                log.warn("Backup rows {}: {}", mod.getTableName(), e.getMessage());
                rows = List.of();
            }
        }

        String fileName = mod.getSlug() + "_" + TS.format(Instant.now()) + ".zip";
        Path target = tenantDir(tenantId).resolve(fileName);

        try (OutputStream fos = Files.newOutputStream(target);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putJson(zos, "module.json", meta);
            putJson(zos, "rows.json", rows);
            putJson(zos, "backup-info.json", Map.of(
                    "version", 1,
                    "tenantId", tenantId,
                    "createdAt", Instant.now().toString(),
                    "rowCount", rows.size()
            ));
        }
        log.info("Backup tenant={} file={} rows={}", tenantId, target, rows.size());
        return target;
    }

    public void deleteBackup(String fileName) throws IOException {
        Long tenantId = tenantCtx.requireTenantId();
        Path p = safeBackupPath(tenantId, fileName);
        Files.deleteIfExists(p);
    }

    @Transactional
    public String restoreBackup(String fileName) throws Exception {
        Long tenantId = tenantCtx.requireTenantId();
        Path p = safeBackupPath(tenantId, fileName);
        if (!Files.exists(p)) throw new IllegalArgumentException("Backup no encontrado en tu negocio");

        Map<String, Object> meta;
        List<Map<String, Object>> rows;
        try (ZipFile zip = new ZipFile(p.toFile())) {
            meta = readJson(zip, "module.json");
            rows = readJsonList(zip, "rows.json");
        }

        // Seguridad: el backup debe ser de este tenant
        Object bakTenant = meta.get("tenantId");
        if (bakTenant != null) {
            long bakTid = Long.parseLong(String.valueOf(bakTenant));
            if (bakTid != tenantId.longValue()) {
                throw new IllegalStateException("Este backup pertenece a otro negocio.");
            }
        }

        String slug = String.valueOf(meta.get("slug"));
        String name = String.valueOf(meta.get("name"));
        if (modules.existsByTenantIdAndSlug(tenantId, slug)) {
            throw new IllegalStateException(
                    "Ya existe un módulo con el slug «" + slug + "» en este negocio. Elimínalo antes de restaurar.");
        }
        boolean sameName = modules.findByTenantIdOrderByNameAsc(tenantId).stream()
                .anyMatch(m -> m.getName() != null && m.getName().equalsIgnoreCase(name));
        if (sameName) {
            throw new IllegalStateException(
                    "Ya existe un módulo llamado «" + name + "» en este negocio. Elimínalo antes de restaurar.");
        }

        Map<String, Object> req = new LinkedHashMap<>(meta);
        req.put("has_owner", Boolean.TRUE.equals(meta.get("hasOwner")) || "true".equalsIgnoreCase(String.valueOf(meta.get("hasOwner"))));
        req.put("has_activities", meta.get("hasActivities") == null || Boolean.TRUE.equals(meta.get("hasActivities")));
        ModuleDefinition def = ModuleDefinition.fromRequest(req);
        engine.generate(def, tenantId);

        String table = modules.findByTenantIdAndSlug(tenantId, slug)
                .map(CrmModule::getTableName)
                .orElse(String.valueOf(meta.get("tableName")));
        if (table != null && table.startsWith("crm_") && rows != null && !rows.isEmpty()) {
            for (Map<String, Object> row : rows) {
                insertRow(table, row);
            }
        }
        return slug;
    }

    private void insertRow(String table, Map<String, Object> row) {
        if (row == null || row.isEmpty()) return;
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String col = e.getKey();
            if (col == null || !col.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) continue;
            cols.add(col);
            vals.add(e.getValue());
        }
        if (cols.isEmpty()) return;
        String colList = cols.stream().map(c -> "`" + c + "`").reduce((a, b) -> a + "," + b).orElse("");
        String placeholders = cols.stream().map(c -> "?").reduce((a, b) -> a + "," + b).orElse("");
        String sql = "INSERT INTO `" + table + "` (" + colList + ") VALUES (" + placeholders + ")";
        try {
            jdbc.update(sql, vals.toArray());
        } catch (Exception ex) {
            log.warn("Restore row skip: {}", ex.getMessage());
        }
    }

    private Path safeBackupPath(Long tenantId, String fileName) throws IOException {
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")
                || !fileName.endsWith(".zip") || !fileName.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Nombre de backup inválido");
        }
        Path dir = tenantDir(tenantId).normalize();
        Path p = dir.resolve(fileName).normalize();
        if (!p.startsWith(dir)) {
            throw new IllegalArgumentException("Ruta de backup inválida");
        }
        return p;
    }

    private void putJson(ZipOutputStream zos, String name, Object data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(ZipFile zip, String entry) throws IOException {
        ZipEntry e = zip.getEntry(entry);
        if (e == null) throw new IllegalArgumentException("Backup incompleto: falta " + entry);
        try (InputStream in = zip.getInputStream(e)) {
            return mapper.readValue(in, Map.class);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readJsonList(ZipFile zip, String entry) throws IOException {
        ZipEntry e = zip.getEntry(entry);
        if (e == null) return List.of();
        try (InputStream in = zip.getInputStream(e)) {
            return mapper.readValue(in, List.class);
        }
    }
}
