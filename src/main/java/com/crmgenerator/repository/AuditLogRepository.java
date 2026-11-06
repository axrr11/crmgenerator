package com.crmgenerator.repository;

import com.crmgenerator.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
        select a from AuditLog a
        where (:action is null or :action = '' or a.action = :action)
          and (:moduleSlug is null or :moduleSlug = '' or a.moduleSlug = :moduleSlug)
          and (:userId is null or a.userId = :userId)
          and (:tenantId is null or a.tenantId = :tenantId)
          and (:from is null or a.createdAt >= :from)
          and (:to is null or a.createdAt <= :to)
        order by a.createdAt desc
        """)
    List<AuditLog> search(@Param("action") String action,
                          @Param("moduleSlug") String moduleSlug,
                          @Param("userId") Long userId,
                          @Param("tenantId") Long tenantId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
