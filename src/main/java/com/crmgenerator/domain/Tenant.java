package com.crmgenerator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tenants")
@Getter @Setter
public class Tenant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String slug;
    private String email;
    private String phone;
    /** active | suspended */
    @Column(nullable = false)
    private String status = "active";
    private Instant createdAt = Instant.now();
}
