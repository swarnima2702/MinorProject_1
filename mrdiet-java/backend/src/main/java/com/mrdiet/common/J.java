package com.mrdiet.common;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Null-safe readers for JSON request bodies. They mirror the PHP idioms the old
 * controllers used ({@code $data['x'] ?? default}, {@code isset}, {@code !empty}).
 */
public final class J {

    private J() {}

    /** PHP isset(): key exists and is not null. */
    public static boolean has(JsonNode n, String field) {
        return n != null && n.has(field) && !n.get(field).isNull();
    }

    public static String str(JsonNode n, String field) {
        if (!has(n, field)) return null;
        JsonNode v = n.get(field);
        return v.isValueNode() ? v.asText() : v.toString();
    }

    public static String str(JsonNode n, String field, String def) {
        String v = str(n, field);
        return v == null ? def : v;
    }

    /** Trimmed string, or default when missing/blank (PHP !empty()). */
    public static String text(JsonNode n, String field, String def) {
        String v = str(n, field);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public static Double dbl(JsonNode n, String field) {
        if (!has(n, field)) return null;
        JsonNode v = n.get(field);
        if (v.isNumber()) return v.asDouble();
        try {
            String s = v.asText().trim();
            return s.isEmpty() ? null : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static double dbl(JsonNode n, String field, double def) {
        Double v = dbl(n, field);
        return v == null ? def : v;
    }

    public static Long lng(JsonNode n, String field) {
        Double d = dbl(n, field);
        return d == null ? null : (long) Math.floor(d);
    }

    public static long lng(JsonNode n, String field, long def) {
        Long v = lng(n, field);
        return v == null ? def : v;
    }

    public static int integer(JsonNode n, String field, int def) {
        Long v = lng(n, field);
        return v == null ? def : v.intValue();
    }

    public static BigDecimal money(JsonNode n, String field) {
        Double d = dbl(n, field);
        return d == null ? null : BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    }

    /** PHP empty() inverted: false for missing, null, false, 0, "0", "", [] and {}. */
    public static boolean truthy(JsonNode n, String field) {
        if (!has(n, field)) return false;
        JsonNode v = n.get(field);
        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) return v.asDouble() != 0;
        if (v.isTextual()) {
            String s = v.asText();
            return !(s.isEmpty() || s.equals("0") || s.equalsIgnoreCase("false"));
        }
        if (v.isContainerNode()) return v.size() > 0;
        return true;
    }
}
