package com.mrdiet.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.config.AppProperties;
import com.mrdiet.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Was notifications.php — reminder preferences and device registration. */
@Service
public class NotificationService {

    private final Db db;
    private final OneSignalClient oneSignal;
    private final AppProperties props;

    public NotificationService(Db db, OneSignalClient oneSignal, AppProperties props) {
        this.db = db;
        this.oneSignal = oneSignal;
        this.props = props;
    }

    public Map<String, Object> get(AuthUser me) {
        Map<String, Object> p = db.one("SELECT * FROM notification_preferences WHERE user_id = ?", me.id());
        if (p == null) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("user_id", me.id());
            d.put("onesignal_player_id", null);
            d.put("water_reminder_enabled", false);
            d.put("water_reminder_interval", 60);
            d.put("water_reminder_start", "08:00:00");
            d.put("water_reminder_end", "22:00:00");
            d.put("meal_reminder_enabled", false);
            d.put("breakfast_time", "08:00:00");
            d.put("lunch_time", "12:30:00");
            d.put("dinner_time", "19:00:00");
            d.put("period_reminder_enabled", false);
            d.put("period_reminder_days_before", 2);
            d.put("notifications_paused", false);
            return d;
        }
        Map<String, Object> out = new LinkedHashMap<>(p);
        for (String flag : new String[]{"water_reminder_enabled", "meal_reminder_enabled", "period_reminder_enabled",
                "notifications_paused"}) {
            out.put(flag, Rows.bool(p, flag));
        }
        return out;
    }

    public Map<String, Object> save(AuthUser me, JsonNode in) {
        int interval = J.integer(in, "water_reminder_interval", 60);
        if (interval < 15 || interval > 480) throw ApiException.badRequest("Water reminder interval must be 15–480 minutes");
        Object[] values = {
                J.truthy(in, "water_reminder_enabled") ? 1 : 0,
                interval,
                time(in, "water_reminder_start", "08:00:00"),
                time(in, "water_reminder_end", "22:00:00"),
                J.truthy(in, "meal_reminder_enabled") ? 1 : 0,
                time(in, "breakfast_time", "08:00:00"),
                time(in, "lunch_time", "12:30:00"),
                time(in, "dinner_time", "19:00:00"),
                J.truthy(in, "period_reminder_enabled") ? 1 : 0,
                J.integer(in, "period_reminder_days_before", 2),
                J.truthy(in, "notifications_paused") ? 1 : 0,
                me.id()
        };
        int updated = db.update("""
                UPDATE notification_preferences SET water_reminder_enabled = ?, water_reminder_interval = ?,
                       water_reminder_start = ?, water_reminder_end = ?, meal_reminder_enabled = ?, breakfast_time = ?,
                       lunch_time = ?, dinner_time = ?, period_reminder_enabled = ?, period_reminder_days_before = ?,
                       notifications_paused = ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?""", values);
        if (updated == 0) {
            db.update("""
                    INSERT INTO notification_preferences (water_reminder_enabled, water_reminder_interval,
                           water_reminder_start, water_reminder_end, meal_reminder_enabled, breakfast_time, lunch_time,
                           dinner_time, period_reminder_enabled, period_reminder_days_before, notifications_paused, user_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", values);
        }
        return Map.of("success", true, "message", "Preferences saved");
    }

    public Map<String, Object> registerDevice(AuthUser me, String playerId) {
        if (playerId == null || playerId.isBlank()) throw ApiException.badRequest("player_id is required");
        int updated = db.update("UPDATE notification_preferences SET onesignal_player_id = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                playerId.trim(), me.id());
        if (updated == 0) {
            db.update("INSERT INTO notification_preferences (user_id, onesignal_player_id) VALUES (?, ?)", me.id(), playerId.trim());
        }
        return Map.of("success", true, "message", "Device registered");
    }

    public Map<String, Object> unregisterDevice(AuthUser me) {
        db.update("UPDATE notification_preferences SET onesignal_player_id = NULL WHERE user_id = ?", me.id());
        return Map.of("success", true, "message", "Device unregistered");
    }

    public Map<String, Object> sendTest(AuthUser me) {
        String playerId = db.scalar("SELECT onesignal_player_id FROM notification_preferences WHERE user_id = ?",
                String.class, me.id());
        if (playerId == null || playerId.isBlank()) {
            throw ApiException.notFound("No device registered for this user. Please enable notifications first.");
        }
        OneSignalClient.Result r = oneSignal.send(playerId, "✅ Test Notification",
                "Notifications are working! You will receive reminders as configured.", props.publicUrl() + "/dashboard");
        if (r.success()) return Map.of("success", true, "message", "Test notification sent");
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", "OneSignal Error");
        err.put("message", "OneSignal Error");
        err.put("details", r.error());
        err.put("code", r.code());
        throw new OneSignalFailure(err);
    }

    /** Lets the controller return OneSignal's details with a 400, as notifications.php did. */
    public static class OneSignalFailure extends RuntimeException {
        private final transient Map<String, Object> body;

        public OneSignalFailure(Map<String, Object> body) {
            super("OneSignal Error");
            this.body = body;
        }

        public Map<String, Object> body() {
            return body;
        }
    }

    private static LocalTime time(JsonNode in, String field, String def) {
        String v = J.text(in, field, def);
        try {
            return LocalTime.parse(v.length() == 5 ? v + ":00" : v);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Invalid time for " + field);
        }
    }
}
