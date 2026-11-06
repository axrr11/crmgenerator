package com.crmgenerator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_module_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "moduleSlug"}))
@Getter @Setter
public class UserModulePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String moduleSlug;
    private boolean canRead = true;
    private boolean canCreate = false;
    private boolean canUpdate = false;
    private boolean canDelete = false;
}
