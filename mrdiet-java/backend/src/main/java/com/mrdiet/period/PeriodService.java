package com.mrdiet.period;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.core.Cycle;
import com.mrdiet.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Was period_tracker.php. Only for the logged-in user, and only if their profile says female. */
@Service
public class PeriodService {

    private final Db db;
    private final ObjectMapper mapper;

    public PeriodService(Db db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    private Map<String, Object> requireFemaleProfile(AuthUser me) {
        Map<String, Object> p = db.one(
                "SELECT id, gender, cycle_length, period_duration, last_period_start FROM profiles WHERE user_id = ?", me.id());
        if (p == null) throw ApiException.forbidden("Your health profile is missing. Please complete health metrics first.");
        String g = Rows.str(p, "gender", "").trim().toLowerCase(Locale.ROOT);
        if (!g.equals("women") && !g.equals("female")) {
            throw ApiException.forbidden("Period tracking is only available for female users.");
        }
        return p;
    }

    public List<Map<String, Object>> logs(AuthUser me, String month) {
        requireFemaleProfile(me);
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month.substring(0, 7));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            throw ApiException.badRequest("month must look like 2026-11");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : db.list(
                "SELECT * FROM period_logs WHERE user_id = ? AND log_date >= ? AND log_date < ? ORDER BY log_date",
                me.id(), ym.atDay(1), ym.plusMonths(1).atDay(1))) {
            Map<String, Object> log = new LinkedHashMap<>(row);
            log.put("is_period_day", Rows.bool(row, "is_period_day"));
            log.put("symptoms", parseArray(Rows.str(row, "symptoms")));
            out.add(log);
        }
        return out;
    }

    public Map<String, Object> summary(AuthUser me) {
        return summaryFor(me.id(), requireFemaleProfile(me));
    }

    public Map<String, Object> saveLog(AuthUser me, JsonNode d) {
        Map<String, Object> profile = requireFemaleProfile(me);
        LocalDate date = parseDate(J.str(d, "log_date"));
        int isPeriod = J.truthy(d, "is_period_day") ? 1 : 0;
        String flow = J.str(d, "flow_level");
        String symptoms = J.has(d, "symptoms") ? d.get("symptoms").toString() : null;
        String mood = J.str(d, "mood");
        String notes = J.str(d, "notes");

        int updated = db.update("""
                UPDATE period_logs SET is_period_day = ?, flow_level = ?, symptoms = ?, mood = ?, notes = ?
                WHERE user_id = ? AND log_date = ?""", isPeriod, flow, symptoms, mood, notes, me.id(), date);
        if (updated == 0) {
            db.update("""
                    INSERT INTO period_logs (user_id, profile_id, log_date, is_period_day, flow_level, symptoms, mood, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", me.id(), profile.get("id"), date, isPeriod, flow, symptoms, mood, notes);
        }
        return result("Log saved successfully.", me);
    }

    public Map<String, Object> deleteLog(AuthUser me, String logDate) {
        requireFemaleProfile(me);
        db.update("DELETE FROM period_logs WHERE user_id = ? AND log_date = ?", me.id(), parseDate(logDate));
        return result("Log deleted.", me);
    }

    public Map<String, Object> updateConfig(AuthUser me, JsonNode d) {
        requireFemaleProfile(me);
        if (!J.has(d, "cycle_length") || !J.has(d, "period_duration")) {
            throw ApiException.badRequest("Cycle length and period duration are required.");
        }
        int cycle = J.integer(d, "cycle_length", 28);
        int duration = J.integer(d, "period_duration", 5);
        if (cycle < 15 || cycle > 60 || duration < 1 || duration > 15) {
            throw ApiException.badRequest("Please enter a realistic cycle length and period duration.");
        }
        String last = J.text(d, "last_period_start", null);
        db.update("UPDATE profiles SET cycle_length = ?, period_duration = ?, last_period_start = ? WHERE user_id = ?",
                cycle, duration, last == null ? null : parseDate(last), me.id());
        return result("Config updated.", me);
    }

    private Map<String, Object> result(String message, AuthUser me) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", message);
        out.put("summary", summaryFor(me.id(), requireFemaleProfile(me)));
        return out;
    }

    private Map<String, Object> summaryFor(long userId, Map<String, Object> profile) {
        LocalDate last = Rows.date(profile, "last_period_start");
        if (last == null) {
            Map<String, Object> latest = db.one(
                    "SELECT log_date FROM period_logs WHERE user_id = ? AND is_period_day = 1 ORDER BY log_date DESC LIMIT 1", userId);
            last = Rows.date(latest, "log_date");
        }
        List<LocalDate> days = new ArrayList<>();
        for (Map<String, Object> r : db.list(
                "SELECT log_date FROM period_logs WHERE user_id = ? AND is_period_day = 1 ORDER BY log_date ASC", userId)) {
            days.add(Rows.date(r, "log_date"));
        }
        Long cycle = Rows.lng(profile, "cycle_length");
        Long duration = Rows.lng(profile, "period_duration");
        return Cycle.summary(last, LocalDate.now(), cycle == null ? null : cycle.intValue(),
                duration == null ? null : duration.intValue(), days);
    }

    private JsonNode parseArray(String json) {
        if (json == null || json.isBlank()) return mapper.createArrayNode();
        try {
            JsonNode n = mapper.readTree(json);
            return n == null || n.isNull() ? mapper.createArrayNode() : n;
        } catch (Exception e) {
            return mapper.createArrayNode();
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) throw ApiException.badRequest("Invalid data. log_date is required.");
        try {
            return LocalDate.parse(s.trim().substring(0, Math.min(10, s.trim().length())));
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Dates must look like 2026-11-19");
        }
    }
}
