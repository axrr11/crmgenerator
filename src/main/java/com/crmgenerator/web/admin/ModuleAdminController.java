package com.crmgenerator.web.admin;

import com.crmgenerator.domain.CrmModule;
import com.crmgenerator.generator.ModuleGeneratorEngine;
import com.crmgenerator.generator.enums.FieldType;
import com.crmgenerator.repository.CrmModuleRepository;
import com.crmgenerator.service.ModuleBackupService;
import com.crmgenerator.service.ModuleSearchCacheService;
import com.crmgenerator.service.TenantContextService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/modules")
public class ModuleAdminController {

    private final CrmModuleRepository modules;
    private final ModuleGeneratorEngine engine;
    private final ModuleBackupService backups;
    private final ModuleSearchCacheService searchCache;
    private final TenantContextService tenantCtx;

    public ModuleAdminController(CrmModuleRepository modules, ModuleGeneratorEngine engine,
                                 ModuleBackupService backups, ModuleSearchCacheService searchCache,
                                 TenantContextService tenantCtx) {
        this.modules = modules;
        this.engine = engine;
        this.backups = backups;
        this.searchCache = searchCache;
        this.tenantCtx = tenantCtx;
    }

    @GetMapping
    public String index(Model model) {
        if (tenantCtx.isPlatformAdmin()) {
            model.addAttribute("modules", modules.findAllByOrderByNameAsc());
        } else {
            model.addAttribute("modules", modules.findByTenantIdOrderByNameAsc(tenantCtx.requireTenantId()));
        }
        try {
            model.addAttribute("backups", backups.listBackups());
        } catch (Exception e) {
            model.addAttribute("backups", java.util.List.of());
            // Si no hay tenant de trabajo (creator sin "Entrar"), no mezclar backups
            if (model.getAttribute("error") == null) {
                model.addAttribute("error", "No se pudieron listar backups: " + e.getMessage());
            }
        }
        return "admin/modules";
    }

    @GetMapping("/{slug}/edit")
    public String edit(@PathVariable String slug, Model model) {
        CrmModule mod = resolveModule(slug);
        model.addAttribute("mod", mod);
        model.addAttribute("fieldTypes", FieldType.values());
        return "admin/module-edit";
    }

    @PostMapping("/{slug}/edit")
    public String update(@PathVariable String slug,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         RedirectAttributes ra) {
        try {
            engine.updateModuleMeta(slug, tenantCtx.isPlatformAdmin() ? null : tenantCtx.requireTenantId(), name, description);
            ra.addFlashAttribute("success", "Módulo actualizado.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/modules/" + slug + "/edit";
    }

    @PostMapping("/{slug}/fields")
    public String addField(@PathVariable String slug,
                           @RequestParam String field_name,
                           @RequestParam String field_label,
                           @RequestParam String field_type,
                           @RequestParam(required = false) String field_required,
                           RedirectAttributes ra) {
        try {
            boolean req = field_required != null;
            engine.addField(slug, tenantCtx.isPlatformAdmin() ? null : tenantCtx.requireTenantId(), field_name, field_label, field_type, req);
            ra.addFlashAttribute("success", "Campo «" + field_name + "» agregado.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo agregar el campo: " + e.getMessage());
        }
        return "redirect:/admin/modules/" + slug + "/edit";
    }

    @PostMapping("/{slug}/delete")
    public String delete(@PathVariable String slug,
                         @RequestParam(required = false) String with_backup,
                         RedirectAttributes ra) {
        try {
            assertModuleAccess(slug);
            if (with_backup != null) {
                var path = backups.createBackup(slug);
                ra.addFlashAttribute("success",
                        "Backup creado («" + path.getFileName() + "») y módulo «" + slug + "» eliminado (tabla + datos + metadatos).");
            } else {
                ra.addFlashAttribute("success", "Módulo «" + slug + "» eliminado (tabla + datos + metadatos).");
            }
            engine.deleteModule(slug, tenantCtx.isPlatformAdmin() ? null : tenantCtx.requireTenantId());
            searchCache.evictModule(slug);
            searchCache.evictLookups();
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo eliminar: " + e.getMessage());
        }
        return "redirect:/admin/modules";
    }

    @PostMapping("/{slug}/backup")
    public String backupOnly(@PathVariable String slug, RedirectAttributes ra) {
        try {
            var path = backups.createBackup(slug);
            ra.addFlashAttribute("success", "Backup creado: " + path.getFileName());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo crear backup: " + e.getMessage());
        }
        return "redirect:/admin/modules";
    }

    @PostMapping("/backups/{fileName}/restore")
    public String restore(@PathVariable String fileName, RedirectAttributes ra) {
        try {
            String slug = backups.restoreBackup(fileName);
            searchCache.evictModule(slug);
            searchCache.evictLookups();
            ra.addFlashAttribute("success", "Backup restaurado. Módulo «" + slug + "» recuperado con sus datos.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo restaurar: " + e.getMessage());
        }
        return "redirect:/admin/modules";
    }

    @PostMapping("/backups/{fileName}/delete")
    public String deleteBackup(@PathVariable String fileName, RedirectAttributes ra) {
        try {
            backups.deleteBackup(fileName);
            ra.addFlashAttribute("success", "Backup eliminado: " + fileName);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo eliminar el backup: " + e.getMessage());
        }
        return "redirect:/admin/modules";
    }

    private void assertModuleAccess(String slug) {
        resolveModule(slug);
    }

    private CrmModule resolveModule(String slug) {
        if (!tenantCtx.isPlatformAdmin()) {
            Long tid = tenantCtx.requireTenantId();
            CrmModule mod = modules.findByTenantIdAndSlug(tid, slug)
                    .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado"));
            tenantCtx.assertSameTenant(mod.getTenantId());
            return mod;
        }
        Long tid = tenantCtx.currentTenantId().orElse(null);
        if (tid != null) {
            return modules.findByTenantIdAndSlug(tid, slug)
                    .or(() -> modules.findBySlug(slug))
                    .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado"));
        }
        return modules.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Módulo no encontrado"));
    }
}
