package com.mrdiet.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** Null-safe readers for database rows returned by {@link Db}. */
public final class Rows {

    private Rows() {}

    public static Long lng(Map<String, Object> row, String col) {
        Object v = row == null ? null : row.get(col);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof Boolean b) return b ? 1L : 0L;
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long lng(Map<String, Object> row, String col, long def) {
        Long v = lng(row, col);
        return v == null ? def : v;
    }

    public static int integer(Map<String, Object> row, String col, int def) {
        Long v = lng(row, col);
        return v == null ? def : v.intValue();
    }

    public static double dbl(Map<String, Object> row, String col, double def) {
        Object v = row == null ? null : row.get(col);
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static BigDecimal money(Map<String, Object> row, String col) {
        return BigDecimal.valueOf(dbl(row, col, 0)).setScale(2, RoundingMode.HALF_UP);
    }

    public static String str(Map<String, Object> row, String col) {
        Object v = row == null ? null : row.get(col);
        return v == null ? null : v.toString();
    }

    public static String str(Map<String, Object> row, String col, String def) {
        String v = str(row, col);
        return v == null ? def : v;
    }

    public static boolean bool(Map<String, Object> row, String col) {
        Object v = row == null ? null : row.get(col);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = v.toString();
        return !(s.isEmpty() || s.equals("0") || s.equalsIgnoreCase("false"));
    }

    public static LocalDate date(Map<String, Object> row, String col) {
        Object v = row == null ? null : row.get(col);
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof LocalDateTime dt) return dt.toLocalDate();
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        String s = v.toString();
        return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    }

    public static LocalDateTime dateTime(Map<String, Object> row, String col) {
        Object v = row == null ? null : row.get(col);
        if (v == null) return null;
        if (v instanceof LocalDateTime dt) return dt;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (v instanceof java.time.OffsetDateTime o) return o.toLocalDateTime();
        return LocalDateTime.parse(v.toString().replace(' ', 'T'));
    }
}
