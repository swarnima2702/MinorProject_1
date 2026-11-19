package com.mrdiet.diet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Was daily_diet_plans.php, checkin.php and timers.php. Every call checks the plan is yours. */
@Service
public class DietPlanService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Db db;
    private final ObjectMapper mapper;

    public DietPlanService(Db db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    /** The subscription row if it belongs to this user, else 404. */
    public Map<String, Object> ownedSubscription(AuthUser me, long subscriptionId) {
        Map<String, Object> sub = db.one("SELECT * FROM subscriptions WHERE id = ? AND user_id = ?", subscriptionId, me.id());
        if (sub == null) throw ApiException.notFound("Plan not found");
        return sub;
    }

    // ---------------------------------------------------------------- day plans

    public List<Map<String, Object>> list(AuthUser me, long subscriptionId) {
        if (subscriptionId <= 0) return List.of();
        ownedSubscription(me, subscriptionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : db.list(
                "SELECT * FROM daily_diet_plans WHERE subscription_id = ? ORDER BY day_number ASC", subscriptionId)) {
            Map<String, Object> plan = new LinkedHashMap<>(row);
            String json = Rows.str(plan, "meal_json");
            plan.remove("meal_json");
            plan.put("meal_plan", parse(json));
            plan.put("is_unlocked", Rows.bool(row, "is_unlocked"));
            out.add(plan);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> createBatch(AuthUser me, JsonNode rows) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) throw ApiException.badRequest("Invalid data format");
        Map<Long, Integer> planDays = new HashMap<>();
        for (JsonNode r : rows) {
            long subId = J.lng(r, "subscription_id", 0);
            int day = J.integer(r, "day_number", 0);
            int maxDays = planDays.computeIfAbsent(subId, id -> Rows.integer(ownedSubscription(me, id), "plan_days", 0));
            if (day < 1 || day > maxDays) throw ApiException.badRequest("Day " + day + " is outside this plan");
            if (db.one("SELECT id FROM daily_diet_plans WHERE subscription_id = ? AND day_number = ?", subId, day) != null) {
                continue; // INSERT IGNORE behaviour
            }
            String dateStr = J.str(r, "plan_date");
            LocalDate date = dateStr == null ? LocalDate.now().plusDays(day - 1L) : LocalDate.parse(dateStr.substring(0, 10));
            db.update("INSERT INTO daily_diet_plans (subscription_id, day_number, plan_date, is_unlocked, user_name) VALUES (?, ?, ?, ?, ?)",
                    subId, day, date, J.truthy(r, "is_unlocked") ? 1 : 0, J.str(r, "user_name", "Unknown"));
        }
        return Map.of("message", "Plans created successfully");
    }

    public Map<String, Object> updateDay(AuthUser me, long subscriptionId, int day, JsonNode d) {
        ownedSubscription(me, subscriptionId);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (J.has(d, "meal_plan")) {
            sets.add("meal_json = ?");
            args.add(d.get("meal_plan").toString());
        }
        if (J.has(d, "is_unlocked")) {
            sets.add("is_unlocked = ?");
            args.add(J.truthy(d, "is_unlocked") ? 1 : 0);
        }
        if (J.has(d, "unlocked_at")) {
            sets.add("unlocked_at = ?");
            args.add(LocalDateTime.now());
        }
        if (sets.isEmpty()) return Map.of("message", "No changes");
        args.add(subscriptionId);
        args.add(day);
        db.update("UPDATE daily_diet_plans SET " + String.join(", ", sets) + " WHERE subscription_id = ? AND day_number = ?",
                args.toArray());
        return Map.of("message", "Updated successfully");
    }

    // ---------------------------------------------------------------- check-ins

    public Map<String, Object> checkIn(AuthUser me, JsonNode d) {
        long subId = J.lng(d, "subscription_id", 0);
        int day = J.integer(d, "day_number", 0);
        if (subId <= 0) return Map.of("success", true, "message", "Free day — nothing to save");
        if (day <= 0) throw ApiException.badRequest("Missing subscription_id or day_number");
        ownedSubscription(me, subId);
        Double weight = J.dbl(d, "weight");
        String mood = J.str(d, "mood");
        String notes = J.str(d, "notes");
        int updated = db.update("UPDATE daily_logs SET weight_log = ?, mood_log = ?, notes = ? WHERE subscription_id = ? AND day_number = ?",
                weight, mood, notes, subId, day);
        if (updated == 0) {
            db.update("INSERT INTO daily_logs (subscription_id, day_number, weight_log, mood_log, notes) VALUES (?, ?, ?, ?, ?)",
                    subId, day, weight, mood, notes);
        }
        return Map.of("success", true, "message", "Daily log saved successfully");
    }

    // ---------------------------------------------------------------- timers (stored in UTC, like PHP)

    public Map<String, Object> getTimer(AuthUser me, long subId, int day) {
        Map<String, Object> out = new HashMap<>();
        if (subId <= 0) {
            out.put("timer", null);
            return out;
        }
        ownedSubscription(me, subId);
        Map<String, Object> t = db.one("SELECT * FROM generation_timers WHERE subscription_id = ? AND day_number = ?", subId, day);
        if (t == null) {
            out.put("timer", null);
            return out;
        }
        Map<String, Object> timer = new LinkedHashMap<>(t);
        LocalDateTime unlockAt = Rows.dateTime(t, "unlock_at");
        timer.put("unlock_at", unlockAt.format(DT).replace(' ', 'T') + "Z");
        out.put("timer", timer);
        return out;
    }

    public Map<String, Object> createTimer(AuthUser me, JsonNode d) {
        long subId = J.lng(d, "subscription_id", 0);
        int day = J.integer(d, "day_number", 0);
        Long unlockMs = J.lng(d, "unlock_at");
        if (subId <= 0) return Map.of("success", true);
        if (day <= 0 || unlockMs == null) throw ApiException.badRequest("Missing fields");
        ownedSubscription(me, subId);
        LocalDateTime unlockUtc = LocalDateTime.ofEpochSecond(unlockMs / 1000, 0, ZoneOffset.UTC);
        int updated = db.update("UPDATE generation_timers SET unlock_at = ? WHERE subscription_id = ? AND day_number = ?",
                unlockUtc, subId, day);
        if (updated == 0) {
            db.update("INSERT INTO generation_timers (subscription_id, day_number, unlock_at) VALUES (?, ?, ?)", subId, day, unlockUtc);
        }
        return Map.of("success", true, "unlock_at", unlockUtc.format(DT));
    }

    public Map<String, Object> deleteTimer(AuthUser me, long subId, int day) {
        if (subId > 0) {
            ownedSubscription(me, subId);
            db.update("DELETE FROM generation_timers WHERE subscription_id = ? AND day_number = ?", subId, day);
        }
        return Map.of("success", true);
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
