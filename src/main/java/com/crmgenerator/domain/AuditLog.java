package com.crmgenerator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(columnList = "action"),
        @Index(columnList = "moduleSlug"),
        @Index(columnList = "userId"),
        @Index(columnList = "createdAt")
})
@Getter @Setter
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** create | update | delete | payment | login */
    @Column(nullable = false, length = 32)
    private String action;
    private String moduleSlug;
    private String moduleName;
    private Long recordId;
    private Long tenantId;
    private Long userId;
    private String userName;
    private String userEmail;
    @Column(length = 500)
    private String summary;
    @Column(length = 2000)
    private String detail;
    private Instant createdAt = Instant.now();
}
