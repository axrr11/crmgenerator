package com.crmgenerator.web.admin;

import com.crmgenerator.generator.ModuleGeneratorEngine;
import com.crmgenerator.generator.dto.ModuleDefinition;
import com.crmgenerator.generator.enums.FieldType;
import com.crmgenerator.generator.enums.ModuleType;
import com.crmgenerator.repository.CrmModuleRepository;
import com.crmgenerator.service.TemplatePackService;
import com.crmgenerator.service.ModuleSearchCacheService;
import com.crmgenerator.service.TenantContextService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/modules")
public class ModuleBuilderController {

    private final ModuleGeneratorEngine engine;
    private final CrmModuleRepository modules;
    private final TemplatePackService packs;
    private final ModuleSearchCacheService searchCache;
    private final TenantContextService tenantCtx;

    public ModuleBuilderController(ModuleGeneratorEngine engine, CrmModuleRepository modules,
                                   TemplatePackService packs, ModuleSearchCacheService searchCache,
                                   TenantContextService tenantCtx) {
        this.engine = engine;
        this.modules = modules;
        this.packs = packs;
        this.searchCache = searchCache;
        this.tenantCtx = tenantCtx;
    }

    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("fieldTypes", FieldType.values());
        model.addAttribute("moduleTypes", ModuleType.values());
        model.addAttribute("existingModules", modules.findAll());
        model.addAttribute("packs", packs.listPackCards());
        return "admin/builder";
    }

    @PostMapping("/pack/crm-basico")
    public String installPack(RedirectAttributes ra) {
        try {
            Map<String, List<String>> result = packs.installCrmBasico();
            String msg = "Pack instalado.";
            if (!result.get("created").isEmpty()) {
                msg += " Creados: " + String.join(", ", result.get("created")) + ".";
            }
            if (!result.get("skipped").isEmpty()) {
                msg += " Ya existían: " + String.join(", ", result.get("skipped")) + ".";
            }
            ra.addFlashAttribute("success", msg);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al instalar pack: " + e.getMessage());
        }
        return "redirect:/admin/modules/create";
    }

    @PostMapping("/pack/one")
    public String installOne(@RequestParam String pack_id, RedirectAttributes ra) {
        try {
            String name = packs.installOne(pack_id);
            searchCache.evictLookups();
            ra.addFlashAttribute("success", "Módulo «" + name + "» creado. Ya puedes cargar registros.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/modules/create";
    }

    @PostMapping
    public String store(@RequestParam Map<String, String> params,
                        @RequestParam(value = "fields_name", required = false) List<String> names,
                        @RequestParam(value = "fields_label", required = false) List<String> labels,
                        @RequestParam(value = "fields_type", required = false) List<String> types,
                        @RequestParam(value = "fields_required", required = false) List<String> requiredFlags,
                        @RequestParam(value = "fields_options", required = false) List<String> options,
                        RedirectAttributes ra) {
        try {
            Map<String, Object> data = new LinkedHashMap<>(params);
            List<Map<String, Object>> fields = new ArrayList<>();
            if (names != null) {
                for (int i = 0; i < names.size(); i++) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", names.get(i));
                    f.put("label", labels != null && labels.size() > i ? labels.get(i) : names.get(i));
                    f.put("type", types != null && types.size() > i ? types.get(i) : "string");
                    boolean req = requiredFlags != null && requiredFlags.contains(String.valueOf(i));
                    f.put("required", req);
                    if (options != null && options.size() > i && options.get(i) != null && !options.get(i).isBlank()) {
                        f.put("options", Arrays.asList(options.get(i).split("\\s*,\\s*")));
                    }
                    fields.add(f);
                }
            }
            data.put("fields", fields);
            data.put("has_owner", params.containsKey("has_owner"));
            data.put("has_activities", params.containsKey("has_activities"));

            ModuleDefinition def = ModuleDefinition.fromRequest(data);
            Long tenantId = tenantCtx.requireTenantId();
            Map<String, Object> result = engine.generate(def, tenantId);
            searchCache.evictModule(def.slug());
            searchCache.evictLookups();
            String msg = "Módulo «" + def.name() + "» listo.";
            if (result.get("companion") != null) {
                msg += " También se crearon sus indicadores.";
            }
            msg += " Puedes empezar a cargar datos.";
            ra.addFlashAttribute("success", msg);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/modules/create";
    }
}
