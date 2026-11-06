package com.crmgenerator.generator.enums;

public enum ModuleType {
    CATALOG, METRICS, KANBAN, FORM, SALES;

    public String label() {
        return simpleLabel();
    }

    public String description() {
        return simpleHint();
    }

    public String simpleLabel() {
        return switch (this) {
            case CATALOG -> "Listado / catálogo";
            case METRICS -> "Indicadores";
            case KANBAN -> "Tablero por etapas";
            case FORM -> "Formulario de captura";
            case SALES -> "Ventas y cobros";
        };
    }

    public String simpleHint() {
        return switch (this) {
            case CATALOG -> "Clientes, productos, proveedores…";
            case METRICS -> "KPIs y gráficos sobre datos";
            case KANBAN -> "Pipeline visual por columnas";
            case FORM -> "Entradas + bandeja de atención";
            case SALES -> "Ventas, cuotas y saldo";
        };
    }

    public static ModuleType from(String raw) {
        if (raw == null) return CATALOG;
        return ModuleType.valueOf(raw.trim().toUpperCase());
    }
}
