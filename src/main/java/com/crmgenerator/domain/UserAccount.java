package com.crmgenerator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter @Setter
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** null solo para rol creator (plataforma) */
    private Long tenantId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;
    /**
     * creator | owner | user | visitor
     */
    @Column(nullable = false)
    private String role = "user";
    private Instant createdAt = Instant.now();

    /** Acceso total dentro de su tenant (owner) o en toda la plataforma (creator). */
    public boolean isFullAccess() {
        String r = role == null ? "" : role.toLowerCase();
        return r.equals("creator") || r.equals("owner") || r.equals("admin");
    }

    public boolean isPlatformAdmin() {
        String r = role == null ? "" : role.toLowerCase();
        return r.equals("creator") || r.equals("admin");
    }

    public boolean isReadOnlyRole() {
        String r = role == null ? "" : role.toLowerCase();
        return r.equals("visitor") || r.equals("viewer");
    }
}
