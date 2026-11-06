package com.crmgenerator.service;

import com.crmgenerator.generator.ModuleGeneratorEngine;
import com.crmgenerator.generator.dto.ModuleDefinition;
import com.crmgenerator.repository.CrmModuleRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TemplatePackService {

    private final ModuleGeneratorEngine engine;
    private final CrmModuleRepository modules;
    private final TenantContextService tenantCtx;

    public TemplatePackService(ModuleGeneratorEngine engine, CrmModuleRepository modules, TenantContextService tenantCtx) {
        this.engine = engine;
        this.modules = modules;
        this.tenantCtx = tenantCtx;
    }

    public List<Map<String, String>> listPackCards() {
        List<Map<String, String>> cards = new ArrayList<>();
        cards.add(card("clientes", "👥", "Clientes", "Cartera de clientes con email, teléfono y estado."));
        cards.add(card("productos", "📦", "Productos", "Catálogo con precio, stock y categoría."));
        cards.add(card("ventas", "💰", "Ventas", "Ventas con total, pagado, saldo y métricas automáticas."));
        cards.add(card("leads", "🎯", "Oportunidades", "Tablero Kanban: nuevo → en progreso → ganado."));
        cards.add(card("tickets", "🎫", "Tickets / soporte", "Formulario de captura + estados de atención."));
        return cards;
    }

    private Map<String, String> card(String id, String icon, String title, String description) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("icon", icon);
        m.put("title", title);
        m.put("description", description);
        return m;
    }

    public String installOne(String packId) {
        ModuleDefinition def = switch (packId) {
            case "clientes" -> defClientes();
            case "productos" -> defProductos();
            case "ventas" -> defVentas();
            case "leads" -> defLeads();
            case "tickets" -> defTickets();
            default -> throw new IllegalArgumentException("Plantilla desconocida: " + packId);
        };
        if (modules.existsByTenantIdAndSlug(tenantCtx.requireTenantId(), def.slug())) {
            throw new IllegalArgumentException("Ya existe un módulo «" + def.slug() + "». Elimínalo antes o usa otro nombre.");
        }
        engine.generate(def, tenantCtx.requireTenantId());
        return def.name();
    }

    public Map<String, List<String>> installCrmBasico() {
        List<String> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (ModuleDefinition def : List.of(defClientes(), defProductos(), defVentas())) {
            if (modules.existsByTenantIdAndSlug(tenantCtx.requireTenantId(), def.slug())) {
                skipped.add(def.slug());
                continue;
            }
            engine.generate(def, tenantCtx.requireTenantId());
            created.add(def.slug());
        }
        return Map.of("created", created, "skipped", skipped);
    }

    private ModuleDefinition defClientes() {
        return ModuleDefinition.fromRequest(Map.of(
                "name", "Clientes", "slug", "clientes", "type", "catalog",
                "has_owner", true, "has_activities", true,
                "description", "Cartera de clientes",
                "fields", List.of(
                        field("nombre", "Nombre", "string", true, null),
                        field("email", "Email", "email", false, null),
                        field("telefono", "Teléfono", "phone", false, null),
                        field("estado", "Estado", "select", false, List.of("Activo", "Inactivo", "Prospecto"))
                )
        ));
    }

    private ModuleDefinition defProductos() {
        return ModuleDefinition.fromRequest(Map.of(
                "name", "Productos", "slug", "productos", "type", "catalog",
                "has_owner", false, "has_activities", false,
                "description", "Catálogo de productos",
                "fields", List.of(
                        field("nombre", "Nombre", "string", true, null),
                        field("imagen", "Foto", "image", false, null),
                        field("color", "Color", "color", false, null),
                        field("talla", "Talla", "size", false, List.of("XS", "S", "M", "L", "XL", "XXL", "Único")),
                        field("precio", "Precio", "decimal", true, null),
                        field("stock", "Stock", "integer", false, null),
                        field("categoria", "Categoría", "string", false, null)
                )
        ));
    }

    private ModuleDefinition defVentas() {
        return ModuleDefinition.fromRequest(Map.of(
                "name", "Ventas", "slug", "ventas", "type", "sales",
                "has_owner", true, "has_activities", true,
                "description", "Ventas y cobranza",
                "fields", List.of(
                        field("titulo", "Título", "string", true, null),
                        field("total", "Total", "decimal", true, null),
                        field("monto_pagado", "Pagado", "decimal", false, null),
                        field("saldo", "Saldo", "decimal", false, null),
                        field("estado", "Estado", "select", true, List.of("Pendiente", "Parcial", "Pagado", "Anulado"))
                )
        ));
    }

    private ModuleDefinition defLeads() {
        return ModuleDefinition.fromRequest(Map.of(
                "name", "Oportunidades", "slug", "oportunidades", "type", "kanban",
                "has_owner", true, "has_activities", true,
                "description", "Pipeline de ventas",
                "kanban_field", "estado",
                "fields", List.of(
                        field("titulo", "Título", "string", true, null),
                        field("estado", "Etapa", "select", true, List.of("Nuevo", "En progreso", "Ganado", "Perdido")),
                        field("monto", "Monto", "decimal", false, null)
                )
        ));
    }

    private ModuleDefinition defTickets() {
        return ModuleDefinition.fromRequest(Map.of(
                "name", "Tickets", "slug", "tickets", "type", "form",
                "has_owner", true, "has_activities", true,
                "description", "Soporte y solicitudes",
                "fields", List.of(
                        field("nombre", "Nombre", "string", true, null),
                        field("email", "Email", "email", false, null),
                        field("mensaje", "Mensaje", "text", false, null),
                        field("estado", "Estado", "select", false, List.of("Nuevo", "Atendido", "Cerrado"))
                )
        ));
    }

    private static Map<String, Object> field(String name, String label, String type, boolean required, List<String> options) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("label", label);
        m.put("type", type);
        m.put("required", required);
        if (options != null) m.put("options", options);
        return m;
    }
}
