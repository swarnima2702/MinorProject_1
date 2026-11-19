package com.mrdiet.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

/**
 * Tiny wrapper around JdbcTemplate so services read like the old PDO code:
 * {@code db.one("SELECT * FROM users WHERE id = ?", id)}.
 * Every value is passed as a "?" parameter — never concatenated — so SQL
 * injection is not possible through these methods.
 */
@Component
public class Db {

    private final JdbcTemplate jdbc;

    public Db(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** First row or null. */
    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    /** Single value from the first row, or null. */
    public <T> T scalar(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbc.queryForList(sql, type, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    /** INSERT and return the new auto-increment id. */
    public long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return an id");
        }
        return key.longValue();
    }

    /** "?, ?, ?" for IN (...) lists. */
    public static String placeholders(int n) {
        return String.join(", ", java.util.Collections.nCopies(Math.max(n, 1), "?"));
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
