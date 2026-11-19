package com.mrdiet.settings;

import com.mrdiet.common.Db;
import com.mrdiet.common.Rows;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The key/value "settings" table the admin panel edits (prices, gateway keys, toggles …). */
@Service
public class SettingsService {

    private static final List<String> PRIVATE_MARKERS = List.of("secret", "api_key", "key_secret", "password");

    private final Db db;

    public SettingsService(Db db) {
        this.db = db;
    }

    public Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> row : db.list("SELECT setting_key, setting_value FROM settings ORDER BY id")) {
            out.put(Rows.str(row, "setting_key"), Rows.str(row, "setting_value"));
        }
        return out;
    }

    /** Value from the table, or {@code fallback} when missing/blank (PHP setting_get()). */
    public String get(String key, String fallback) {
        String v = db.scalar("SELECT setting_value FROM settings WHERE setting_key = ?", String.class, key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    public double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void put(String key, String value) {
        int updated = db.update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", value, key);
        if (updated == 0) {
            db.update("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?)", key, value);
        }
    }

    /** Settings safe to show in the browser — anything that looks like a secret is dropped. */
    public Map<String, String> publicSettings() {
        Map<String, String> out = new LinkedHashMap<>();
        all().forEach((k, v) -> {
            String lower = k.toLowerCase();
            if (PRIVATE_MARKERS.stream().noneMatch(lower::contains)) out.put(k, v);
        });
        return out;
    }
}
