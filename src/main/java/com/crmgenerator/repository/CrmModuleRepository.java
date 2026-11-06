package com.crmgenerator.repository;

import com.crmgenerator.domain.CrmModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrmModuleRepository extends JpaRepository<CrmModule, Long> {
    Optional<CrmModule> findBySlug(String slug);
    Optional<CrmModule> findByTenantIdAndSlug(Long tenantId, String slug);
    boolean existsBySlug(String slug);
    boolean existsByTenantIdAndSlug(Long tenantId, String slug);
    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);
    List<CrmModule> findByTenantIdOrderByNameAsc(Long tenantId);
    List<CrmModule> findAllByOrderByNameAsc();
}
