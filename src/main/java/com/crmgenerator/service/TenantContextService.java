package com.crmgenerator.service;

import com.crmgenerator.domain.Tenant;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.repository.TenantRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Service
public class TenantContextService {

    public static final String SESSION_VIEW_TENANT = "VIEW_TENANT_ID";

    private final PermissionService permissions;
    private final TenantRepository tenants;

    public TenantContextService(PermissionService permissions, TenantRepository tenants) {
        this.permissions = permissions;
        this.tenants = tenants;
    }

    public Optional<UserAccount> currentUser() {
        return permissions.currentUser();
    }

    public boolean isPlatformAdmin() {
        return currentUser().map(UserAccount::isPlatformAdmin).orElse(false);
    }

    /**
     * Tenant efectivo:
     * - Dueño / Usuario / Visitante → siempre el de su cuenta (nunca sesión ajena).
     * - Creador → SOLO si eligió "Entrar" a un negocio (sesión). Sin eso, vacío.
     */
    public Optional<Long> currentTenantId() {
        UserAccount u = currentUser().orElse(null);
        if (u == null) return Optional.empty();

        if (u.isPlatformAdmin()) {
            return Optional.ofNullable(sessionViewTenantId());
        }
        return Optional.ofNullable(u.getTenantId());
    }

    public Optional<Tenant> currentTenant() {
        return currentTenantId().flatMap(tenants::findById);
    }

    public Long requireTenantId() {
        return currentTenantId().orElseThrow(() ->
                new IllegalStateException(isPlatformAdmin()
                        ? "Selecciona un negocio para operar (Negocios → Entrar)."
                        : "Tu usuario no está asociado a ningún negocio."));
    }

    public void assertSameTenant(Long tenantId) {
        if (isPlatformAdmin()) {
            // creator puede operar si está "dentro" de ese negocio
            Long view = currentTenantId().orElse(null);
            if (view != null && tenantId != null && !view.equals(tenantId)) {
                throw new IllegalStateException("Estás viendo otro negocio. Cambia con Negocios → Entrar.");
            }
            return;
        }
        Long mine = currentTenantId().orElse(-1L);
        if (tenantId == null || !mine.equals(tenantId)) {
            throw new IllegalStateException("No tienes acceso a este negocio.");
        }
    }

    public void setViewTenant(Long tenantId) {
        if (!isPlatformAdmin()) {
            throw new IllegalStateException("Solo el Creador puede cambiar de negocio");
        }
        HttpSession session = session();
        if (session == null) return;
        if (tenantId == null) {
            session.removeAttribute(SESSION_VIEW_TENANT);
        } else {
            tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));
            session.setAttribute(SESSION_VIEW_TENANT, tenantId);
        }
    }

    public void clearViewTenant() {
        HttpSession session = session();
        if (session != null) session.removeAttribute(SESSION_VIEW_TENANT);
    }

    private Long sessionViewTenantId() {
        HttpSession session = session();
        if (session == null) return null;
        Object v = session.getAttribute(SESSION_VIEW_TENANT);
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (Exception ignored) { return null; }
        }
        return null;
    }

    private HttpSession session() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null || attrs.getRequest() == null) return null;
            return attrs.getRequest().getSession(true);
        } catch (Exception e) {
            return null;
        }
    }
}
