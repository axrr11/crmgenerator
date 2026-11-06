package com.crmgenerator.web;

import com.crmgenerator.domain.CrmModule;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.repository.CrmModuleRepository;
import com.crmgenerator.service.PermissionService;
import com.crmgenerator.service.TenantContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalModelAdvice {
    private final CrmModuleRepository modules;
    private final TenantContextService tenants;
    private final PermissionService permissions;

    public GlobalModelAdvice(CrmModuleRepository modules, TenantContextService tenants, PermissionService permissions) {
        this.modules = modules;
        this.tenants = tenants;
        this.permissions = permissions;
    }

    @ModelAttribute("modulesNav")
    public Object modulesNav() {
        // Creador sin "entrar" a un negocio: no listar módulos de clientes
        if (tenants.isPlatformAdmin() && tenants.currentTenantId().isEmpty()) {
            return List.of();
        }
        List<CrmModule> list = tenants.currentTenantId()
                .map(modules::findByTenantIdOrderByNameAsc)
                .orElseGet(List::of);

        UserAccount user = permissions.currentUser().orElse(null);
        if (user != null && !user.isFullAccess()) {
            list = list.stream()
                    .filter(m -> permissions.canRead(m.getSlug()))
                    .collect(Collectors.toList());
        }
        return list;
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        if (request == null) return "";
        String uri = request.getRequestURI();
        return uri != null ? uri : "";
    }

    @ModelAttribute("currentTenant")
    public Object currentTenant() {
        return tenants.currentTenant().orElse(null);
    }

    @ModelAttribute("currentUserAccount")
    public UserAccount currentUserAccount() {
        return permissions.currentUser().orElse(null);
    }

    @ModelAttribute("isPlatformAdmin")
    public boolean isPlatformAdmin() {
        return tenants.isPlatformAdmin();
    }

    @ModelAttribute("viewingAsTenant")
    public boolean viewingAsTenant() {
        return tenants.isPlatformAdmin() && tenants.currentTenantId().isPresent();
    }
}
