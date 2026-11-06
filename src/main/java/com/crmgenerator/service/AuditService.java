package com.crmgenerator.service;

import com.crmgenerator.domain.AuditLog;
import com.crmgenerator.domain.CrmModule;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository logs;
    private final PermissionService permissions;

    public AuditService(AuditLogRepository logs, PermissionService permissions) {
        this.logs = logs;
        this.permissions = permissions;
    }

    @Transactional
    public void log(String action, CrmModule mod, Long recordId, String summary, String detail) {
        AuditLog a = new AuditLog();
        a.setAction(action);
        if (mod != null) {
            a.setModuleSlug(mod.getSlug());
            a.setModuleName(mod.getName());
        }
        a.setRecordId(recordId);
        a.setSummary(summary);
        a.setDetail(detail);
        permissions.currentUser().ifPresent(u -> {
            a.setUserId(u.getId());
            a.setUserName(u.getName());
            a.setUserEmail(u.getEmail());
            a.setTenantId(u.getTenantId());
        });
        logs.save(a);
    }

    public List<AuditLog> search(String action, String moduleSlug, Long userId,
                                 LocalDate from, LocalDate to, int limit) {
        return search(action, moduleSlug, userId, null, from, to, limit);
    }

    public List<AuditLog> search(String action, String moduleSlug, Long userId, Long tenantId,
                                 LocalDate from, LocalDate to, int limit) {
        Instant fromI = from != null ? from.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant toI = to != null ? to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        return logs.search(
                blank(action),
                blank(moduleSlug),
                userId,
                tenantId,
                fromI,
                toI,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 200))
        );
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
