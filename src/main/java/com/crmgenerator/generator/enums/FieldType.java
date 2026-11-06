package com.crmgenerator.generator.enums;

public enum FieldType {
    STRING, TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, EMAIL, PHONE, SELECT, IMAGE, COLOR, SIZE;

    public String sqlType() {
        return switch (this) {
            case STRING, EMAIL, PHONE, IMAGE, SELECT, COLOR, SIZE -> "VARCHAR(255)";
            case TEXT -> "TEXT";
            case INTEGER -> "BIGINT";
            case DECIMAL -> "DECIMAL(12,2)";
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case DATETIME -> "DATETIME";
        };
    }

    public String htmlInput() {
        return switch (this) {
            case EMAIL -> "email";
            case PHONE -> "tel";
            case INTEGER, DECIMAL -> "number";
            case DATE -> "date";
            case DATETIME -> "datetime-local";
            case BOOLEAN -> "checkbox";
            case TEXT -> "textarea";
            case SELECT, SIZE -> "select";
            case COLOR -> "color";
            case IMAGE -> "file";
            default -> "text";
        };
    }

    public String label() {
        return switch (this) {
            case STRING -> "Texto";
            case TEXT -> "Texto largo";
            case INTEGER -> "Número";
            case DECIMAL -> "Dinero / decimal";
            case BOOLEAN -> "Sí / No";
            case DATE -> "Fecha";
            case DATETIME -> "Fecha y hora";
            case EMAIL -> "Email";
            case PHONE -> "Teléfono";
            case SELECT -> "Lista";
            case IMAGE -> "Imagen / foto";
            case COLOR -> "Color";
            case SIZE -> "Talla";
        };
    }

    public static FieldType from(String raw) {
        if (raw == null) return STRING;
        return FieldType.valueOf(raw.trim().toUpperCase());
    }
}
