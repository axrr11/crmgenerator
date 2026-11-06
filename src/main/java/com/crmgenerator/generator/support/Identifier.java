package com.crmgenerator.generator.support;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Identifier {
    private static final Pattern SAFE = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern SAFE_CLASS = Pattern.compile("^[A-Z][A-Za-z0-9]*$");

    private Identifier() {}

    public static String column(String raw) {
        String s = slugify(raw);
        if (!SAFE.matcher(s).matches()) {
            throw new IllegalArgumentException("Identificador de columna inválido: " + raw);
        }
        return s;
    }

    public static String table(String raw) {
        return column(raw);
    }

    public static String className(String raw) {
        String[] parts = slugify(raw).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        String name = sb.toString();
        if (!SAFE_CLASS.matcher(name).matches()) {
            throw new IllegalArgumentException("Nombre de clase inválido: " + raw);
        }
        return name;
    }

    public static String slugify(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[áàäâ]", "a")
                .replaceAll("[éèëê]", "e")
                .replaceAll("[íìïî]", "i")
                .replaceAll("[óòöô]", "o")
                .replaceAll("[úùüû]", "u")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
        if (s.isEmpty()) throw new IllegalArgumentException("Identificador vacío");
        if (Character.isDigit(s.charAt(0))) s = "n_" + s;
        return s;
    }
}
