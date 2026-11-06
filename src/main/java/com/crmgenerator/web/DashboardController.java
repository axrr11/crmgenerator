package com.crmgenerator.web;

import com.crmgenerator.repository.CrmModuleRepository;
import com.crmgenerator.repository.UserAccountRepository;
import com.crmgenerator.service.AuditService;
import com.crmgenerator.service.TenantContextService;
import com.crmgenerator.service.PermissionService;
import com.crmgenerator.service.DynamicCrudService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final CrmModuleRepository modules;
    private final DynamicCrudService crud;
    private final AuditService audit;
    private final UserAccountRepository users;
    private final TenantContextService tenantCtx;
    private final PermissionService permissionService;

    public DashboardController(CrmModuleRepository modules, DynamicCrudService crud,
                               AuditService audit, UserAccountRepository users,
                               TenantContextService tenantCtx,
                               PermissionService permissionService) {
        this.modules = modules;
        this.crud = crud;
        this.audit = audit;
        this.users = users;
        this.tenantCtx = tenantCtx;
        this.permissionService = permissionService;
    }

    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(required = false) String action,
                        @RequestParam(required = false) String module,
                        @RequestParam(required = false) Long userId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var moduleList = tenantCtx.isPlatformAdmin()
                ? modules.findAllByOrderByNameAsc()
                : modules.findByTenantIdOrderByNameAsc(tenantCtx.currentTenantId().orElse(-1L));
        List<Map<String, Object>> stats = new ArrayList<>();
        moduleList.stream()
                .filter(m -> permissionService.canRead(m.getSlug()))
                .forEach(m -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", m.getName());
            s.put("slug", m.getSlug());
            s.put("type", m.getType());
            try {
                s.put("count", crud.count(m, null));
            } catch (Exception e) {
                s.put("count", 0);
            }
            stats.add(s);
        });
        model.addAttribute("stats", stats);
        model.addAttribute("modules", moduleList.stream().filter(m -> permissionService.canRead(m.getSlug())).toList());
        if (tenantCtx.isPlatformAdmin()) {
            model.addAttribute("users", users.findAll());
        } else {
            model.addAttribute("users", users.findByTenantIdOrderByNameAsc(tenantCtx.currentTenantId().orElse(-1L)));
        }
        Long tenantFilter = tenantCtx.isPlatformAdmin() ? null : tenantCtx.currentTenantId().orElse(-1L);
        model.addAttribute("auditLogs", audit.search(action, module, userId, tenantFilter, from, to, 100));
        model.addAttribute("filterAction", action);
        model.addAttribute("filterModule", module);
        model.addAttribute("filterUserId", userId);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        return "dashboard/index";
    }
}
