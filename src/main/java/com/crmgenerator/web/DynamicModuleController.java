package com.crmgenerator.web;

import com.crmgenerator.domain.CrmModule;
import com.crmgenerator.domain.CrmPayment;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.service.AuditService;
import com.crmgenerator.service.TenantContextService;
import com.crmgenerator.service.FileStorageService;
import com.crmgenerator.service.DynamicCrudService;
import com.crmgenerator.service.ModuleSearchCacheService;
import com.crmgenerator.service.PaymentService;
import com.crmgenerator.service.PermissionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/crm/{slug}")
public class DynamicModuleController {

    private final DynamicCrudService crud;
    private final ModuleSearchCacheService searchCache;
    private final PaymentService paymentService;
    private final PermissionService permissions;
    private final AuditService audit;
    private final FileStorageService files;
    private final TenantContextService tenantCtx;

    public DynamicModuleController(DynamicCrudService crud,
                                   ModuleSearchCacheService searchCache,
                                   PaymentService paymentService,
                                   PermissionService permissions,
                                   AuditService audit,
                                   FileStorageService files,
                                   TenantContextService tenantCtx) {
        this.crud = crud;
        this.searchCache = searchCache;
        this.paymentService = paymentService;
        this.permissions = permissions;
        this.audit = audit;
        this.files = files;
        this.tenantCtx = tenantCtx;
    }

    @GetMapping
    public String index(@PathVariable String slug,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) String sort,
                        @RequestParam(defaultValue = "desc") String dir,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        if (!permissions.canRead(slug)) {
            return "redirect:/?error=sin_permiso";
        }
        CrmModule mod = mod(slug);
        if ("metrics".equalsIgnoreCase(mod.getType())) {
            if (mod.getMetrics() == null) {
                mod.setMetrics(java.util.List.of());
            }
            model.addAttribute("module", mod);
            model.addAttribute("kpis", crud.aggregateMetrics(mod));
            return "modules/metrics";
        }
        if (mod.getFields() == null) mod.setFields(List.of());
        try {
            model.addAttribute("module", mod);
            model.addAttribute("items", searchCache.list(mod, q, sort, dir, page, 15));
            model.addAttribute("total", searchCache.count(mod, q));
        } catch (Exception e) {
            model.addAttribute("module", mod);
            model.addAttribute("items", List.of());
            model.addAttribute("total", 0L);
            model.addAttribute("error", "Error al cargar registros: " + e.getMessage());
        }
        model.addAttribute("q", q);
        model.addAttribute("page", page);
        model.addAttribute("canCreate", permissions.canCreate(slug));
        model.addAttribute("canUpdate", permissions.canUpdate(slug));
        model.addAttribute("canDelete", permissions.canDelete(slug));
        return "modules/index";
    }

    @GetMapping("/create")
    public String create(@PathVariable String slug, Model model, RedirectAttributes ra) {
        if (!permissions.canCreate(slug)) {
            ra.addFlashAttribute("error", "No tienes permiso para crear registros en este módulo.");
            return "redirect:/crm/" + slug;
        }
        CrmModule mod = mod(slug);
        crud.ensureClienteColumn(mod);
        crud.ensureProductoColumn(mod);
        model.addAttribute("module", mod);
        model.addAttribute("item", new HashMap<String, Object>());
        model.addAttribute("isEdit", false);
        model.addAttribute("canAssignOwner", canAssignOwner());
        model.addAttribute("users", canAssignOwner() ? crud.listUsersLookup(tenantCtx.currentTenantId().orElse(null)) : List.of());
        model.addAttribute("currentUser", permissions.currentUser().orElse(null));
        if ("sales".equalsIgnoreCase(mod.getType())) {
            model.addAttribute("clients", searchCache.clientsLookup(tenantCtx.currentTenantId().orElse(null)));
            model.addAttribute("products", searchCache.productsLookup(tenantCtx.currentTenantId().orElse(null)));
        }
        return "modules/form";
    }

    @PostMapping
    public String store(@PathVariable String slug,
                        @RequestParam Map<String, String> params,
                        @RequestParam(value = "due_dates", required = false) List<String> dueDates,
                        HttpServletRequest request,
                        RedirectAttributes ra) {
        if (!permissions.canCreate(slug)) {
            ra.addFlashAttribute("error", "No tienes permiso para crear.");
            return "redirect:/crm/" + slug;
        }
        CrmModule mod = mod(slug);
        try {
            crud.ensureClienteColumn(mod);
            crud.ensureProductoColumn(mod);
            Map<String, String> data = new HashMap<>(params);
            data.remove("_csrf");
            applyUploads(mod, data, request);
            applyOwnerRules(data, true);

            String payMode = data.remove("pay_mode");
            String installmentsStr = data.remove("installments");
            data.remove("first_due_date");
            data.remove("installment_preview");
            // remove due_dates keys from map if present as single values
            data.keySet().removeIf(k -> k.startsWith("due_dates"));

            long id = crud.create(mod, data);
            searchCache.evictModule(slug);
            if ("clientes".equals(slug) || "productos".equals(slug)) searchCache.evictLookups();

            if ("sales".equalsIgnoreCase(mod.getType()) && "cuotas".equalsIgnoreCase(payMode)) {
                int n = 1;
                try { n = Integer.parseInt(installmentsStr); } catch (Exception ignored) {}
                BigDecimal total = parseDecimal(params.get("total"));
                List<LocalDate> dates = parseDueDates(dueDates, n);
                paymentService.generateInstallments(slug, id, total, n, dates);
                syncSaleTotals(mod, id, total);
            }

            audit.log("create", mod, id, "Creó registro #" + id + " en " + mod.getName(), null);
            ra.addFlashAttribute("success", "Registro creado (#" + id + ")");
            return "redirect:/crm/" + slug + "/" + id;
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/crm/" + slug + "/create";
        }
    }

    @GetMapping("/{id}")
    public String show(@PathVariable String slug, @PathVariable long id, Model model) {
        if (!permissions.canRead(slug)) return "redirect:/?error=sin_permiso";
        CrmModule mod = mod(slug);
        Map<String, Object> item = crud.find(mod, id);
        model.addAttribute("module", mod);
        model.addAttribute("item", item);
        model.addAttribute("canUpdate", permissions.canUpdate(slug));
        model.addAttribute("canDelete", permissions.canDelete(slug));
        if ("sales".equalsIgnoreCase(mod.getType())) {
            model.addAttribute("clientName", crud.findClientName(item.get("cliente_id")));
            List<CrmPayment> pays = paymentService.list(slug, id);
            model.addAttribute("payments", pays);
            BigDecimal paid = paymentService.sumPaid(slug, id);
            BigDecimal total = toBd(item.get("total"));
            if (total.compareTo(BigDecimal.ZERO) == 0) total = paymentService.sumAll(slug, id);
            model.addAttribute("paidSum", paid);
            model.addAttribute("saleTotal", total);
            model.addAttribute("saleBalance", total.subtract(paid).max(BigDecimal.ZERO));
        }
        return "modules/show";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String slug, @PathVariable long id, Model model, RedirectAttributes ra) {
        if (!permissions.canUpdate(slug)) {
            ra.addFlashAttribute("error", "No tienes permiso para editar.");
            return "redirect:/crm/" + slug + "/" + id;
        }
        CrmModule mod = mod(slug);
        crud.ensureClienteColumn(mod);
        crud.ensureProductoColumn(mod);
        model.addAttribute("module", mod);
        model.addAttribute("item", crud.find(mod, id));
        model.addAttribute("isEdit", true);
        model.addAttribute("canAssignOwner", canAssignOwner());
        model.addAttribute("users", canAssignOwner() ? crud.listUsersLookup(tenantCtx.currentTenantId().orElse(null)) : List.of());
        model.addAttribute("currentUser", permissions.currentUser().orElse(null));
        if ("sales".equalsIgnoreCase(mod.getType())) {
            model.addAttribute("clients", searchCache.clientsLookup(tenantCtx.currentTenantId().orElse(null)));
            model.addAttribute("products", searchCache.productsLookup(tenantCtx.currentTenantId().orElse(null)));
        }
        return "modules/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable String slug, @PathVariable long id,
                         @RequestParam Map<String, String> params,
                         HttpServletRequest request,
                         RedirectAttributes ra) {
        if (!permissions.canUpdate(slug)) {
            ra.addFlashAttribute("error", "No tienes permiso para editar.");
            return "redirect:/crm/" + slug + "/" + id;
        }
        CrmModule mod = mod(slug);
        try {
            crud.ensureClienteColumn(mod);
            crud.ensureProductoColumn(mod);
            Map<String, String> data = new HashMap<>(params);
            data.remove("_csrf");
            applyUploads(mod, data, request);
            applyOwnerRules(data, false);
            data.remove("pay_mode");
            data.remove("installments");
            data.remove("first_due_date");
            data.keySet().removeIf(k -> k.startsWith("due_dates"));
            crud.update(mod, id, data);
            searchCache.evictModule(slug);
            if ("clientes".equals(slug) || "productos".equals(slug)) searchCache.evictLookups();
            audit.log("update", mod, id, "Actualizó registro #" + id + " en " + mod.getName(), null);
            ra.addFlashAttribute("success", "Registro actualizado");
            return "redirect:/crm/" + slug + "/" + id;
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/crm/" + slug + "/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String slug, @PathVariable long id, RedirectAttributes ra) {
        if (!permissions.canDelete(slug)) {
            ra.addFlashAttribute("error", "No tienes permiso para eliminar.");
            return "redirect:/crm/" + slug;
        }
        CrmModule mod = mod(slug);
        crud.softDelete(mod, id);
        searchCache.evictModule(slug);
        if ("clientes".equals(slug) || "productos".equals(slug)) searchCache.evictLookups();
        audit.log("delete", mod, id, "Eliminó registro #" + id + " en " + mod.getName(), null);
        ra.addFlashAttribute("success", "Registro eliminado");
        return "redirect:/crm/" + slug;
    }

    @PostMapping("/{id}/payments")
    public String addPayment(@PathVariable String slug, @PathVariable long id,
                             @RequestParam BigDecimal amount,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paid_at,
                             @RequestParam(required = false) String method,
                             @RequestParam(required = false) String note,
                             @RequestParam(required = false) Long payment_id,
                             RedirectAttributes ra) {
        if (!permissions.canUpdate(slug)) {
            ra.addFlashAttribute("error", "No tienes permiso para registrar pagos.");
            return "redirect:/crm/" + slug + "/" + id;
        }
        try {
            paymentService.registerPayment(slug, id, amount, paid_at, method, note, payment_id);
            CrmModule mod = mod(slug);
            if ("sales".equalsIgnoreCase(mod.getType())) {
                Map<String, Object> item = crud.find(mod, id);
                BigDecimal total = toBd(item.get("total"));
                BigDecimal paid = paymentService.sumPaid(slug, id);
                Map<String, String> patch = new HashMap<>();
                patch.put("monto_pagado", paid.toPlainString());
                patch.put("saldo", total.subtract(paid).max(BigDecimal.ZERO).toPlainString());
                if (paid.compareTo(total) >= 0) patch.put("estado", "Pagado");
                else if (paid.compareTo(BigDecimal.ZERO) > 0) patch.put("estado", "Parcial");
                crud.update(mod, id, patch);
                searchCache.evictModule(slug);
            }
            audit.log("payment", mod, id, "Registró pago de " + amount + " en venta #" + id, method);
            ra.addFlashAttribute("success", "Pago registrado");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/crm/" + slug + "/" + id;
    }

    @PostMapping("/{id}/installments")
    public String generateInstallments(@PathVariable String slug, @PathVariable long id,
                                       @RequestParam int installments,
                                       @RequestParam(value = "due_dates", required = false) List<String> dueDates,
                                       RedirectAttributes ra) {
        if (!permissions.canUpdate(slug)) {
            ra.addFlashAttribute("error", "No tienes permiso.");
            return "redirect:/crm/" + slug + "/" + id;
        }
        try {
            CrmModule mod = mod(slug);
            Map<String, Object> item = crud.find(mod, id);
            BigDecimal total = toBd(item.get("total"));
            List<LocalDate> dates = parseDueDates(dueDates, installments);
            paymentService.generateInstallments(slug, id, total, installments, dates);
            syncSaleTotals(mod, id, total);
            searchCache.evictModule(slug);
            audit.log("payment", mod, id, "Generó " + installments + " cuota(s) en venta #" + id, null);
            ra.addFlashAttribute("success", installments + " cuota(s) generadas");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/crm/" + slug + "/" + id;
    }


    private CrmModule mod(String slug) {
        // Siempre resolver por tenant del usuario (salvo creator viendo otro contexto)
        if (!tenantCtx.isPlatformAdmin()) {
            Long tid = tenantCtx.requireTenantId();
            CrmModule m = crud.requireModule(slug, tid);
            if (m.getTenantId() == null || !tid.equals(m.getTenantId())) {
                throw new IllegalStateException("Módulo no pertenece a tu negocio");
            }
            return m;
        }
        Long tid = tenantCtx.currentTenantId().orElse(null);
        if (tid != null) {
            try {
                return crud.requireModule(slug, tid);
            } catch (Exception ignored) {
                // creator puede abrir módulo de otro tenant por slug global solo como fallback
            }
        }
        return crud.requireModule(slug);
    }

    private void applyUploads(CrmModule mod, Map<String, String> data, HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest multi)) return;
        if (mod.getFields() == null) return;
        for (Map<String, Object> f : mod.getFields()) {
            String type = String.valueOf(f.getOrDefault("type", "")).toLowerCase();
            String name = String.valueOf(f.get("name"));
            if (!"image".equals(type)) continue;
            MultipartFile file = multi.getFile(name);
            if (file != null && !file.isEmpty()) {
                String path = files.store(file, mod.getSlug());
                data.put(name, path);
            } else {
                // keep existing on update if no new file
                data.remove(name);
            }
        }
    }

    private boolean canAssignOwner() {
        return permissions.currentUser().map(UserAccount::isFullAccess).orElse(false);
    }

    /** Users: always self. Creator/Owner: may pick another user. */
    private void applyOwnerRules(Map<String, String> data, boolean isCreate) {
        Optional<UserAccount> me = permissions.currentUser();
        if (me.isEmpty()) {
            data.remove("owner_id");
            data.remove("owner_name");
            return;
        }
        UserAccount u = me.get();
        if (u.isFullAccess() && data.get("owner_id") != null && !data.get("owner_id").isBlank()) {
            try {
                long oid = Long.parseLong(data.get("owner_id"));
                crud.listUsersLookup(tenantCtx.currentTenantId().orElse(null)).stream()
                        .filter(x -> oid == ((Number) x.get("id")).longValue())
                        .findFirst()
                        .ifPresent(x -> {
                            data.put("owner_id", String.valueOf(oid));
                            data.put("owner_name", String.valueOf(x.get("name")));
                        });
            } catch (Exception e) {
                data.put("owner_id", String.valueOf(u.getId()));
                data.put("owner_name", u.getName());
            }
        } else {
            // force current user (create or non-privileged update)
            if (isCreate || !u.isFullAccess()) {
                data.put("owner_id", String.valueOf(u.getId()));
                data.put("owner_name", u.getName());
            } else {
                // full access edit without changing owner: leave as posted empty
                data.remove("owner_id");
                data.remove("owner_name");
            }
        }
    }

    private void syncSaleTotals(CrmModule mod, long id, BigDecimal total) {
        BigDecimal paid = paymentService.sumPaid(mod.getSlug(), id);
        Map<String, String> patch = new HashMap<>();
        if (total != null) patch.put("total", total.toPlainString());
        patch.put("monto_pagado", paid.toPlainString());
        if (total != null) patch.put("saldo", total.subtract(paid).max(BigDecimal.ZERO).toPlainString());
        crud.update(mod, id, patch);
    }

    private static List<LocalDate> parseDueDates(List<String> raw, int n) {
        List<LocalDate> dates = new ArrayList<>();
        if (raw != null) {
            for (String s : raw) {
                if (s == null || s.isBlank()) {
                    dates.add(null);
                } else {
                    try { dates.add(LocalDate.parse(s.trim())); }
                    catch (Exception e) { dates.add(null); }
                }
            }
        }
        while (dates.size() < n) dates.add(null);
        return dates.subList(0, n);
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.replace(",", ".").trim()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        try { return new BigDecimal(String.valueOf(o).replace(",", ".")); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}
