package com.crmgenerator.web.admin;

import com.crmgenerator.domain.Tenant;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.repository.CrmModuleRepository;
import com.crmgenerator.repository.TenantRepository;
import com.crmgenerator.repository.UserAccountRepository;
import com.crmgenerator.service.TenantContextService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/tenants")
public class TenantAdminController {

    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final CrmModuleRepository modules;
    private final TenantContextService tenantCtx;

    public TenantAdminController(TenantRepository tenants, UserAccountRepository users,
                                 CrmModuleRepository modules, TenantContextService tenantCtx) {
        this.tenants = tenants;
        this.users = users;
        this.modules = modules;
        this.tenantCtx = tenantCtx;
    }

    @GetMapping
    public String index(Model model) {
        if (!tenantCtx.isPlatformAdmin()) {
            return "redirect:/?error=sin_permiso";
        }
        List<Tenant> all = tenants.findAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tenant t : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenant", t);
            List<UserAccount> tu = users.findByTenantIdOrderByNameAsc(t.getId());
            m.put("users", tu);
            m.put("userCount", tu.size());
            m.put("moduleCount", modules.findByTenantIdOrderByNameAsc(t.getId()).size());
            rows.add(m);
        }
        model.addAttribute("tenantRows", rows);
        model.addAttribute("viewTenantId", tenantCtx.currentTenantId().orElse(null));
        return "admin/tenants";
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        if (!tenantCtx.isPlatformAdmin()) {
            return "redirect:/?error=sin_permiso";
        }
        Tenant t = tenants.findById(id).orElseThrow();
        model.addAttribute("tenant", t);
        model.addAttribute("users", users.findByTenantIdOrderByNameAsc(id));
        model.addAttribute("modules", modules.findByTenantIdOrderByNameAsc(id));
        model.addAttribute("viewTenantId", tenantCtx.currentTenantId().orElse(null));
        return "admin/tenant-detail";
    }

    /** Entrar al contexto de un negocio (ver sus módulos). */
    @PostMapping("/{id}/enter")
    public String enter(@PathVariable Long id, RedirectAttributes ra) {
        if (!tenantCtx.isPlatformAdmin()) {
            return "redirect:/?error=sin_permiso";
        }
        try {
            tenantCtx.setViewTenant(id);
            Tenant t = tenants.findById(id).orElseThrow();
            ra.addFlashAttribute("success", "Ahora estás viendo el negocio «" + t.getName() + "».");
            return "redirect:/";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/tenants";
        }
    }

    @PostMapping("/exit")
    public String exit(RedirectAttributes ra) {
        if (!tenantCtx.isPlatformAdmin()) {
            return "redirect:/";
        }
        tenantCtx.clearViewTenant();
        ra.addFlashAttribute("success", "Saliste del contexto del negocio. Solo panel de administración.");
        return "redirect:/admin/tenants";
    }
}
