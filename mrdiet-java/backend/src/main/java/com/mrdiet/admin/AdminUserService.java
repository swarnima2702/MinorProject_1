package com.mrdiet.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.core.Plans;
import com.mrdiet.security.AuthUser;
import com.mrdiet.subscription.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Was admin/analytics.php, admin/users.php, admin/profiles.php and admin/health_metrics.php. */
@Service
public class AdminUserService {

    /** Columns an admin may edit on a profile (PHP accepted ANY key → SQL injection through column names). */
    private static final Set<String> PROFILE_COLUMNS = Set.of("name", "email", "age", "gender", "height", "weight", "goal",
            "activity_level", "dietary_restrictions", "allergies", "meal_preference", "extended_data", "bmi",
            "cycle_length", "period_duration", "last_period_start");
    private static final Set<String> REQUIRED_PROFILE_COLUMNS = Set.of("name", "email", "age", "gender", "height",
            "weight", "goal", "activity_level");

    private static final String PAID = "payment_status IN ('completed','SUCCESS')";

    private final Db db;
    private final PricingService pricing;
    private final ObjectMapper mapper;

    public AdminUserService(Db db, PricingService pricing, ObjectMapper mapper) {
        this.db = db;
        this.pricing = pricing;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------- analytics

    public Map<String, Object> analytics() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();

        long totalUsers = count("SELECT COUNT(*) FROM users");
        long totalProfiles = count("SELECT COUNT(*) FROM profiles");
        long active = count("SELECT COUNT(*) FROM subscriptions WHERE " + PAID + " AND end_date >= ?", today);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalProfiles", totalProfiles);
        stats.put("totalRevenue", money("SELECT COALESCE(SUM(amount), 0) FROM subscriptions WHERE " + PAID));
        stats.put("mrr", money("SELECT COALESCE(SUM(amount), 0) FROM subscriptions WHERE " + PAID
                + " AND plan_days >= 28 AND end_date >= ?", today));
        stats.put("pendingPayments", count("SELECT COUNT(*) FROM subscriptions WHERE payment_status = 'pending'"));
        stats.put("revenueToday", money("SELECT COALESCE(SUM(amount), 0) FROM subscriptions WHERE " + PAID
                + " AND start_date >= ? AND start_date < ?", startOfToday, startOfToday.plusDays(1)));
        stats.put("activeSubscriptions", active);
        stats.put("totalReferralPayout", money("SELECT COALESCE(SUM(amount), 0) FROM referral_earnings"));
        stats.put("pendingWithdrawalsCount", count("SELECT COUNT(*) FROM withdrawals WHERE status = 'pending'"));
        stats.put("pendingWithdrawalsAmount", money("SELECT COALESCE(SUM(amount), 0) FROM withdrawals WHERE status = 'pending'"));
        stats.put("conversionRate", totalProfiles > 0 ? Math.round(active * 1000.0 / totalProfiles) / 10.0 : 0);

        // Revenue per month for the last 6 months (grouped in Java so it works on any SQL database)
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        Map<LocalDate, BigDecimal> byMonth = new TreeMap<>();
        for (Map<String, Object> r : db.list("SELECT start_date, amount FROM subscriptions WHERE " + PAID
                + " AND start_date >= ?", startOfToday.minusMonths(6))) {
            LocalDate m = Rows.date(r, "start_date").withDayOfMonth(1);
            byMonth.merge(m, Rows.money(r, "amount"), BigDecimal::add);
        }
        List<Map<String, Object>> revenueHistory = new ArrayList<>();
        byMonth.forEach((m, v) -> revenueHistory.add(Map.of("month", m.format(monthFmt), "revenue", v)));

        Map<LocalDate, Long> signups = new TreeMap<>();
        for (Map<String, Object> r : db.list("SELECT created_at FROM users WHERE created_at >= ?", startOfToday.minusDays(30))) {
            signups.merge(Rows.date(r, "created_at"), 1L, Long::sum);
        }
        List<Map<String, Object>> growth = new ArrayList<>();
        signups.forEach((d, c) -> growth.add(Map.of("date", d.toString(), "count", c)));

        Map<LocalDate, BigDecimal> daily = new TreeMap<>();
        for (Map<String, Object> r : db.list("SELECT start_date, amount FROM subscriptions WHERE " + PAID
                + " AND start_date >= ?", startOfToday.minusDays(30))) {
            daily.merge(Rows.date(r, "start_date"), Rows.money(r, "amount"), BigDecimal::add);
        }
        List<Map<String, Object>> revenueChart = new ArrayList<>();
        daily.forEach((d, v) -> revenueChart.add(Map.of("date", d.toString(), "amount", v)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stats", stats);
        out.put("recentTransactions", db.list("""
                SELECT s.*, u.name, u.email FROM subscriptions s JOIN users u ON s.user_id = u.id
                ORDER BY s.start_date DESC LIMIT 10"""));
        out.put("growthChart", growth);
        out.put("revenueChart", revenueChart);
        out.put("revenueHistory", revenueHistory);
        return out;
    }

    // ------------------------------------------------------------- users

    /** Same columns as before, minus the password hash (PHP sent u.* — including hashes — to the browser). */
    public List<Map<String, Object>> users() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = db.list("""
                SELECT u.id, u.email, u.name, u.google_id, u.created_at, u.is_admin, u.referral_code, u.referred_by,
                  (SELECT s.id FROM subscriptions s WHERE s.user_id = u.id AND s.payment_status = 'completed' AND s.end_date >= ? ORDER BY s.end_date DESC LIMIT 1) AS active_subscription_id,
                  (SELECT s.plan_type FROM subscriptions s WHERE s.user_id = u.id AND s.payment_status = 'completed' AND s.end_date >= ? ORDER BY s.end_date DESC LIMIT 1) AS active_plan,
                  (SELECT s.end_date FROM subscriptions s WHERE s.user_id = u.id AND s.payment_status = 'completed' AND s.end_date >= ? ORDER BY s.end_date DESC LIMIT 1) AS active_plan_end_date,
                  (SELECT COUNT(*) FROM subscriptions s WHERE s.user_id = u.id) AS sub_count,
                  COALESCE((SELECT SUM(re.amount) FROM referral_earnings re WHERE re.user_id = u.id), 0) AS total_earned,
                  COALESCE((SELECT SUM(w.amount) FROM withdrawals w WHERE w.user_id = u.id AND w.status IN ('approved','pending')), 0) AS committed_withdrawals
                FROM users u
                ORDER BY u.created_at DESC""", today, today, today);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> u = new LinkedHashMap<>(r);
            u.put("current_balance", Rows.money(r, "total_earned").subtract(Rows.money(r, "committed_withdrawals")));
            u.remove("committed_withdrawals");
            out.add(u);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> changePlan(AuthUser admin, long userId, String planTypeRaw) {
        String planType = Plans.normalize(planTypeRaw);
        if (!Plans.isPurchasable(planType)) throw ApiException.badRequest("user_id and a valid plan_type are required");
        Map<String, Object> target = db.one("SELECT id, name, email FROM users WHERE id = ?", userId);
        if (target == null) throw ApiException.notFound("User not found");

        LocalDate today = LocalDate.now();
        int days = Plans.days(planType);
        BigDecimal amount = pricing.planPrice(planType);
        LocalDate end = today.plusDays(days);

        Long profileId = db.scalar("SELECT id FROM profiles WHERE user_id = ?", Long.class, userId);
        if (profileId == null) {
            profileId = db.insert("""
                    INSERT INTO profiles (user_id, name, email, age, gender, height, weight, goal, activity_level,
                                          dietary_restrictions, allergies, meal_preference, extended_data)
                    VALUES (?, ?, ?, 0, '', 0, 0, '', '', '[]', '', '', '{}')""",
                    userId, Rows.str(target, "name"), Rows.str(target, "email"));
        }

        String note = "Admin changed plan to " + planType + " on " + today;
        for (Map<String, Object> s : db.list(
                "SELECT id, admin_note FROM subscriptions WHERE user_id = ? AND payment_status = 'completed' AND end_date >= ?",
                userId, today)) {
            String existing = Rows.str(s, "admin_note", "");
            db.update("UPDATE subscriptions SET status = 'expired', end_date = ?, admin_note = ? WHERE id = ?",
                    today.minusDays(1), existing.isEmpty() ? note : existing + "\n" + note, s.get("id"));
        }

        long subId = db.insert("""
                INSERT INTO subscriptions (user_id, profile_id, plan_type, plan_days, end_date, amount, payment_method,
                                           payment_status, transaction_id, admin_note)
                VALUES (?, ?, ?, ?, ?, ?, 'admin', 'completed', ?, ?)""",
                userId, profileId, planType, days, end, amount,
                "ADMIN-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + userId,
                "Plan assigned by admin #" + admin.id() + " on " + today);
        String name = Rows.str(target, "name", "User");
        for (int d = 1; d <= days; d++) {
            db.update("INSERT INTO daily_diet_plans (subscription_id, day_number, plan_date, is_unlocked, user_name) VALUES (?, ?, ?, 0, ?)",
                    subId, d, today.plusDays(d - 1L), name);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("subscription_id", subId);
        out.put("user_id", userId);
        out.put("plan_type", planType);
        out.put("plan_days", days);
        out.put("end_date", end.toString());
        out.put("amount", amount);
        return out;
    }

    @Transactional
    public Map<String, Object> deleteUsers(AuthUser admin, List<Long> requested) {
        List<Long> ids = requested.stream().filter(id -> id != null && id != admin.id()).distinct().toList();
        if (ids.isEmpty()) throw ApiException.badRequest("No user IDs provided");
        // Never delete admins.
        List<Long> deletable = db.jdbc().queryForList(
                "SELECT id FROM users WHERE is_admin <> 1 AND id IN (" + Db.placeholders(ids.size()) + ")",
                Long.class, ids.toArray());
        if (deletable.isEmpty()) return Map.of("success", true, "deleted", 0, "message", "0 user(s) deleted successfully");
        Object[] userArgs = deletable.toArray();
        String userIn = Db.placeholders(deletable.size());

        List<Long> subIds = db.jdbc().queryForList("SELECT id FROM subscriptions WHERE user_id IN (" + userIn + ")", Long.class, userArgs);
        List<Long> profileIds = db.jdbc().queryForList("SELECT id FROM profiles WHERE user_id IN (" + userIn + ")", Long.class, userArgs);

        if (!subIds.isEmpty()) {
            String subIn = Db.placeholders(subIds.size());
            for (String t : List.of("daily_diet_plans", "daily_logs", "checkins", "generation_timers", "timers")) {
                db.update("DELETE FROM " + t + " WHERE subscription_id IN (" + subIn + ")", subIds.toArray());
            }
            db.update("UPDATE referral_earnings SET subscription_id = NULL WHERE subscription_id IN (" + subIn + ")", subIds.toArray());
        }
        if (!profileIds.isEmpty()) {
            db.update("DELETE FROM health_metrics WHERE profile_id IN (" + Db.placeholders(profileIds.size()) + ")", profileIds.toArray());
        }
        for (String t : List.of("payment_orders", "period_logs", "subscriptions", "profiles", "referral_earnings",
                "withdrawal_requests", "withdrawals", "notification_logs", "notification_preferences")) {
            db.update("DELETE FROM " + t + " WHERE user_id IN (" + userIn + ")", userArgs);
        }
        db.update("UPDATE referral_earnings SET referred_user_id = NULL WHERE referred_user_id IN (" + userIn + ")", userArgs);
        db.update("UPDATE users SET referred_by = NULL WHERE referred_by IN (" + userIn + ")", userArgs);
        int deleted = db.update("DELETE FROM users WHERE id IN (" + userIn + ") AND is_admin <> 1", userArgs);
        return Map.of("success", true, "deleted", deleted, "message", deleted + " user(s) deleted successfully");
    }

    // ------------------------------------------------------------- profiles

    public List<Map<String, Object>> profiles() {
        return db.list("""
                SELECT p.*,
                  COALESCE((SELECT s.plan_type FROM subscriptions s
                            WHERE s.user_id = p.user_id AND s.payment_status = 'completed' AND s.end_date >= ?
                            ORDER BY s.end_date DESC LIMIT 1), 'Trial') AS active_plan
                FROM profiles p ORDER BY p.created_at DESC""", LocalDate.now());
    }

    public Map<String, Object> profile(long id) {
        return db.one("SELECT * FROM profiles WHERE id = ?", id);
    }

    public Map<String, Object> updateProfile(long id, JsonNode data) {
        Map<String, Object> existing = profile(id);
        if (existing == null) throw ApiException.notFound("Profile not found");
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        ObjectNode ext = null;
        var fields = data.fields();
        while (fields.hasNext()) {
            var e = fields.next();
            String col = e.getKey();
            if (!PROFILE_COLUMNS.contains(col)) continue;
            JsonNode v = e.getValue();
            if (col.equals("extended_data")) {
                ext = v.isObject() ? ((ObjectNode) v).deepCopy() : mapper.createObjectNode();
                continue;
            }
            if (v.isNull() && REQUIRED_PROFILE_COLUMNS.contains(col)) continue; // NOT NULL in the table
            sets.add(col + " = ?");
            if (v.isNull()) args.add(null);
            else if (v.isContainerNode()) args.add(v.toString());
            else if (v.isNumber()) args.add(v.numberValue());
            else args.add(v.asText());
        }
        // "phone" isn't a column — it lives in extended_data (the PHP version crashed on it).
        if (J.has(data, "phone")) {
            if (ext == null) ext = parseObject(Rows.str(existing, "extended_data"));
            ext.put("phone", J.str(data, "phone"));
        }
        if (ext != null) {
            sets.add("extended_data = ?");
            args.add(ext.toString());
        }
        if (sets.isEmpty()) throw ApiException.badRequest("ID and data required");
        args.add(id);
        db.update("UPDATE profiles SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());
        return Map.of("success", true);
    }

    @Transactional
    public Map<String, Object> deleteProfile(long id) {
        db.update("DELETE FROM health_metrics WHERE profile_id = ?", id);
        db.update("DELETE FROM profiles WHERE id = ?", id);
        return Map.of("success", true, "deleted", id);
    }

    public Map<String, Object> healthMetrics(long profileId) {
        return db.one("SELECT * FROM health_metrics WHERE profile_id = ? ORDER BY last_updated DESC LIMIT 1", profileId);
    }

    /** One row per profile (the table has UNIQUE(profile_id), so PHP's plain INSERT failed on the 2nd save). */
    public Map<String, Object> saveHealthMetrics(JsonNode d) {
        long profileId = J.lng(d, "profile_id", 0);
        if (profileId <= 0 || profile(profileId) == null) throw ApiException.badRequest("Profile ID required");
        double bmi = J.dbl(d, "bmi", 0);
        int kcal = J.integer(d, "target_calories", 0);
        String notes = J.str(d, "notes", "");
        int updated = db.update("""
                UPDATE health_metrics SET bmi = ?, target_calories = ?, notes = ?, updated_by_admin = 1,
                       last_updated = CURRENT_TIMESTAMP WHERE profile_id = ?""", bmi, kcal, notes, profileId);
        if (updated == 0) {
            db.update("INSERT INTO health_metrics (profile_id, bmi, target_calories, notes, updated_by_admin) VALUES (?, ?, ?, ?, 1)",
                    profileId, bmi, kcal, notes);
        }
        return Map.of("success", true);
    }

    // ------------------------------------------------------------- helpers

    private long count(String sql, Object... args) {
        Long v = db.scalar(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private BigDecimal money(String sql, Object... args) {
        BigDecimal v = db.scalar(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }

    private ObjectNode parseObject(String json) {
        try {
            JsonNode n = json == null ? null : mapper.readTree(json);
            return n != null && n.isObject() ? (ObjectNode) n : mapper.createObjectNode();
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
