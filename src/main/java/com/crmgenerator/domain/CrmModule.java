package com.crmgenerator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "crm_modules",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_module_tenant_slug", columnNames = {"tenantId", "slug"}),
                @UniqueConstraint(name = "uk_module_tenant_table", columnNames = {"tenantId", "tableName"})
        })
@Getter @Setter
public class CrmModule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long tenantId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String slug;
    @Column(nullable = false)
    private String tableName;
    @Column(nullable = false)
    private String type;
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Map<String, Object>> fields;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Map<String, String>> metrics;
    private String kanbanField;
    private boolean hasOwner;
    private boolean hasActivities = true;
    private String metricsSourceSlug;
    private Instant generatedAt = Instant.now();
}
