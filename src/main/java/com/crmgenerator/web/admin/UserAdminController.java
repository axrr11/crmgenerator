package com.crmgenerator.web.admin;

import com.crmgenerator.domain.CrmModule;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.domain.UserModulePermission;
import com.crmgenerator.repository.CrmModuleRepository;
import com.crmgenerator.repository.UserAccountRepository;
import com.crmgenerator.repository.UserModulePermissionRepository;
import com.crmgenerator.service.TenantContextService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final CrmModuleRepository modules;
    private final UserModulePermissionRepository permissions;
    private final TenantContextService tenantCtx;

    public UserAdminController(UserAccountRepository users, PasswordEncoder encoder,
                               CrmModuleRepository modules, UserModulePermissionRepository permissions,
                               TenantContextService tenantCtx) {
        this.users = users;
        this.encoder = encoder;
        this.modules = modules;
        this.permissions = permissions;
        this.tenantCtx = tenantCtx;
    }

    @GetMapping
    public String index(Model model) {
        if (tenantCtx.isPlatformAdmin()) {
            model.addAttribute("users", users.findAll());
        } else {
            Long tid = tenantCtx.requireTenantId();
            model.addAttribute("users", users.findByTenantIdOrderByNameAsc(tid));
        }
        return "admin/users";
    }

    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("user", new UserAccount());
        model.addAttribute("allModules", modulesForCurrentTenant());
        model.addAttribute("permMap", Map.of());
        return "admin/user-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        UserAccount u = users.findById(id).orElseThrow();
        assertCanManage(u);
        model.addAttribute("user", u);
        model.addAttribute("allModules", modulesForCurrentTenant());
        Map<String, UserModulePermission> permMap = new HashMap<>();
        for (UserModulePermission p : permissions.findByUserId(id)) {
            permMap.put(p.getModuleSlug(), p);
        }
        model.addAttribute("permMap", permMap);
        return "admin/user-form";
    }

    @PostMapping
    @Transactional
    public String store(@RequestParam String name,
                        @RequestParam String email,
                        @RequestParam(required = false) String password,
                        @RequestParam String role,
                        @RequestParam(required = false) List<String> module_slug,
                        @RequestParam(required = false) List<String> can_read,
                        @RequestParam(required = false) List<String> can_create,
                        @RequestParam(required = false) List<String> can_update,
                        @RequestParam(required = false) List<String> can_delete,
                        @RequestParam(required = false) Long id,
                        RedirectAttributes ra) {
        role = normalizeRole(role);
        // Solo creator puede crear otros creator; dueño no
        if ("creator".equals(role) && !tenantCtx.isPlatformAdmin()) {
            ra.addFlashAttribute("error", "No puedes asignar el rol Creador.");
            return "redirect:/admin/users";
        }

        Long tenantId = tenantCtx.requireTenantId();
        UserAccount u;
        if (id != null) {
            u = users.findById(id).orElseThrow();
            assertCanManage(u);
            u.setName(name);
            u.setEmail(email);
            u.setRole(role);
            if (password != null && !password.isBlank()) {
                u.setPassword(encoder.encode(password));
            }
        } else {
            if (users.existsByEmail(email)) {
                ra.addFlashAttribute("error", "Email ya registrado");
                return "redirect:/admin/users/create";
            }
            if (password == null || password.isBlank()) {
                ra.addFlashAttribute("error", "La contraseña es obligatoria");
                return "redirect:/admin/users/create";
            }
            u = new UserAccount();
            u.setName(name);
            u.setEmail(email);
            u.setPassword(encoder.encode(password));
            u.setRole(role);
            u.setTenantId(tenantId);
        }
        // Never move users across tenants from this form
        if (!tenantCtx.isPlatformAdmin()) {
            u.setTenantId(tenantId);
        } else if (u.getTenantId() == null) {
            u.setTenantId(tenantId);
        }
        users.save(u);

        permissions.deleteByUserId(u.getId());
        if (!u.isFullAccess() && module_slug != null) {
            Set<String> read = can_read != null ? new HashSet<>(can_read) : Set.of();
            Set<String> create = can_create != null ? new HashSet<>(can_create) : Set.of();
            Set<String> update = can_update != null ? new HashSet<>(can_update) : Set.of();
            Set<String> delete = can_delete != null ? new HashSet<>(can_delete) : Set.of();
            for (String slug : module_slug) {
                UserModulePermission p = new UserModulePermission();
                p.setUserId(u.getId());
                p.setModuleSlug(slug);
                if (u.isReadOnlyRole()) {
                    p.setCanRead(true);
                    p.setCanCreate(false);
                    p.setCanUpdate(false);
                    p.setCanDelete(false);
                } else {
                    p.setCanRead(read.contains(slug) || create.contains(slug) || update.contains(slug) || delete.contains(slug));
                    p.setCanCreate(create.contains(slug));
                    p.setCanUpdate(update.contains(slug));
                    p.setCanDelete(delete.contains(slug));
                    if (!p.isCanRead() && (p.isCanCreate() || p.isCanUpdate() || p.isCanDelete())) {
                        p.setCanRead(true);
                    }
                }
                permissions.save(p);
            }
        }

        ra.addFlashAttribute("success", id != null ? "Usuario actualizado" : "Usuario creado");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            UserAccount u = users.findById(id).orElseThrow();
            assertCanManage(u);
            if ("admin@crm.local".equalsIgnoreCase(u.getEmail())) {
                throw new IllegalArgumentException("No se puede eliminar el usuario principal del sistema.");
            }
            permissions.deleteByUserId(id);
            users.deleteById(id);
            ra.addFlashAttribute("success", "Usuario eliminado");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    private List<CrmModule> modulesForCurrentTenant() {
        if (tenantCtx.isPlatformAdmin()) {
            return modules.findAllByOrderByNameAsc();
        }
        return modules.findByTenantIdOrderByNameAsc(tenantCtx.requireTenantId());
    }

    private void assertCanManage(UserAccount u) {
        if (tenantCtx.isPlatformAdmin()) return;
        Long tid = tenantCtx.requireTenantId();
        if (u.getTenantId() == null || !tid.equals(u.getTenantId())) {
            throw new IllegalStateException("No puedes administrar usuarios de otro negocio.");
        }
    }

    private String normalizeRole(String role) {
        if (role == null) return "user";
        return switch (role.toLowerCase()) {
            case "creator", "creador", "admin" -> "creator";
            case "owner", "dueño", "dueno" -> "owner";
            case "visitor", "visitante", "viewer" -> "visitor";
            default -> "user";
        };
    }
}
