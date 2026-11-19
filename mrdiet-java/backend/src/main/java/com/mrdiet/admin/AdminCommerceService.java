package com.mrdiet.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.FileStorage;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.core.Plans;
import com.mrdiet.security.AuthUser;
import com.mrdiet.settings.SettingsService;
import com.mrdiet.subscription.SubscriptionActivator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Was admin/coupons.php, admin/transactions.php, admin/withdrawals.php,
 * admin/adjust_balance.php, admin/settings.php and admin/cms.php (POST).
 */
@Service
public class AdminCommerceService {

    private static final Set<String> COUPON_FIELDS = Set.of("code", "discount_type", "discount_value", "max_uses",
            "min_amount", "applies_to", "valid_from", "valid_until", "is_active");
    private static final List<String> TXN_STATUSES = List.of("pending", "completed", "failed", "cancelled", "upgraded", "refunded");
    private static final Set<String> WITHDRAWAL_STATUSES = Set.of("pending", "approved", "rejected");

    private final Db db;
    private final SettingsService settings;
    private final FileStorage files;
    private final SubscriptionActivator activator;

    public AdminCommerceService(Db db, SettingsService settings, FileStorage files, SubscriptionActivator activator) {
        this.db = db;
        this.settings = settings;
        this.files = files;
        this.activator = activator;
    }

    // ------------------------------------------------------------- coupons

    public List<Map<String, Object>> coupons() {
        return db.list("SELECT * FROM coupons ORDER BY id DESC");
    }

    public Map<String, Object> couponAnalytics() {
        List<Map<String, Object>> rows = db.list("""
                SELECT id, code, discount_type, discount_value, applies_to, used_count, max_uses, is_active, valid_until, created_at
                FROM coupons ORDER BY used_count DESC, id DESC""");
        LocalDateTime now = LocalDateTime.now();
        long redemptions = 0, activeCount = 0, expired = 0, exhausted = 0;
        for (Map<String, Object> r : rows) {
            long used = Rows.lng(r, "used_count", 0);
            redemptions += used;
            if (Rows.bool(r, "is_active")) activeCount++;
            LocalDateTime until = Rows.dateTime(r, "valid_until");
            if (until != null && until.isBefore(now)) expired++;
            Long max = Rows.lng(r, "max_uses");
            if (max != null && used >= max) exhausted++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total_coupons", rows.size());
        out.put("active_coupons", activeCount);
        out.put("expired_coupons", expired);
        out.put("exhausted_coupons", exhausted);
        out.put("total_redemptions", redemptions);
        out.put("top_coupons", rows.subList(0, Math.min(5, rows.size())));
        return out;
    }

    public Map<String, Object> createCoupon(JsonNode b) {
        String code = J.text(b, "code", "").toUpperCase(Locale.ROOT);
        if (code.isEmpty()) throw ApiException.badRequest("Code required");
        String type = J.str(b, "discount_type", "percent");
        if (!type.equals("percent") && !type.equals("fixed")) throw ApiException.badRequest("discount_type must be percent or fixed");
        try {
            long id = db.insert("""
                    INSERT INTO coupons (code, discount_type, discount_value, max_uses, min_amount, applies_to, valid_from, valid_until, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    code, type, J.dbl(b, "discount_value", 0), maxUses(b), J.dbl(b, "min_amount", 0),
                    J.text(b, "applies_to", "all"), dateTime(J.str(b, "valid_from")), dateTime(J.str(b, "valid_until")),
                    J.truthy(b, "is_active") ? 1 : 0);
            return Map.of("success", true, "id", id);
        } catch (DuplicateKeyException e) {
            throw ApiException.badRequest("Coupon code already exists");
        }
    }

    public Map<String, Object> updateCoupon(long id, JsonNode b) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (String f : COUPON_FIELDS) {
            if (!b.has(f)) continue;
            Object v;
            switch (f) {
                case "code" -> v = J.text(b, f, "").toUpperCase(Locale.ROOT);
                case "is_active" -> v = J.truthy(b, f) ? 1 : 0;
                case "max_uses" -> v = maxUses(b);
                case "valid_from", "valid_until" -> v = dateTime(J.str(b, f));
                case "discount_value", "min_amount" -> v = J.dbl(b, f, 0);
                case "discount_type" -> {
                    String t = J.str(b, f, "percent");
                    if (!t.equals("percent") && !t.equals("fixed")) throw ApiException.badRequest("discount_type must be percent or fixed");
                    v = t;
                }
                default -> v = J.str(b, f);
            }
            sets.add(f + " = ?");
            args.add(v);
        }
        if (sets.isEmpty()) return Map.of("success", true);
        args.add(id);
        try {
            db.update("UPDATE coupons SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());
        } catch (DuplicateKeyException e) {
            throw ApiException.badRequest("Coupon code already exists");
        }
        return Map.of("success", true);
    }

    public Map<String, Object> deleteCoupon(long id) {
        db.update("DELETE FROM coupons WHERE id = ?", id);
        return Map.of("success", true);
    }

    // ------------------------------------------------------------- transactions

    public Map<String, Object> transactions(String status, String gateway, String q) {
        List<String> where = new ArrayList<>(List.of("1=1"));
        List<Object> args = new ArrayList<>();
        String st = status == null ? "all" : status.toLowerCase(Locale.ROOT);
        String gw = gateway == null ? "all" : gateway.toLowerCase(Locale.ROOT);
        if (!st.isEmpty() && !st.equals("all")) { where.add("s.payment_status = ?"); args.add(st); }
        if (!gw.isEmpty() && !gw.equals("all")) { where.add("LOWER(s.payment_method) LIKE ?"); args.add("%" + gw + "%"); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim() + "%";
            where.add("(u.email LIKE ? OR u.name LIKE ? OR s.transaction_id LIKE ? OR CAST(s.id AS CHAR) = ?)");
            args.add(like); args.add(like); args.add(like); args.add(q.trim());
        }
        String filter = String.join(" AND ", where);
        List<Map<String, Object>> rows = db.list("""
                SELECT s.id, s.user_id, s.profile_id, s.plan_type, s.plan_days, s.start_date, s.end_date, s.amount,
                       s.payment_method, s.payment_status, s.transaction_id, s.admin_note,
                       u.email, u.name AS user_name, p.name AS profile_name
                FROM subscriptions s
                LEFT JOIN users u ON s.user_id = u.id
                LEFT JOIN profiles p ON s.profile_id = p.id
                WHERE """ + filter + " ORDER BY s.start_date DESC LIMIT 500", args.toArray());
        Map<String, Object> stats = db.one("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(CASE WHEN s.payment_status = 'completed' THEN s.amount ELSE 0 END), 0) AS revenue,
                       COUNT(CASE WHEN s.payment_status = 'completed' THEN 1 END) AS completed_count,
                       COUNT(CASE WHEN s.payment_status = 'pending' THEN 1 END) AS pending_count,
                       COUNT(CASE WHEN s.payment_status IN ('failed','cancelled') THEN 1 END) AS failed_count
                FROM subscriptions s LEFT JOIN users u ON s.user_id = u.id
                WHERE """ + filter, args.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transactions", rows);
        out.put("stats", stats);
        return out;
    }

    /** Manual status change. Marking a plan "completed" runs the full activation (referral, coupon, upgrade). */
    @Transactional
    public Map<String, Object> updateTransaction(long id, JsonNode b) {
        String status = J.str(b, "payment_status");
        if (status == null) throw ApiException.badRequest("id and payment_status required");
        if (!TXN_STATUSES.contains(status)) {
            throw ApiException.badRequest("Invalid status. Allowed: " + String.join(", ", TXN_STATUSES));
        }
        Map<String, Object> sub = db.one("SELECT * FROM subscriptions WHERE id = ?", id);
        if (sub == null) throw ApiException.notFound("Subscription not found");

        boolean wasPaid = Plans.PAID_STATES.contains(Rows.str(sub, "payment_status", "").toLowerCase(Locale.ROOT));
        if (status.equals("completed") && !wasPaid) {
            activator.activate(id, J.str(b, "transaction_id"));
        } else {
            db.update("UPDATE subscriptions SET payment_status = ? WHERE id = ?", status, id);
        }
        if (J.has(b, "transaction_id")) db.update("UPDATE subscriptions SET transaction_id = ? WHERE id = ?", J.str(b, "transaction_id"), id);
        if (J.has(b, "admin_note")) db.update("UPDATE subscriptions SET admin_note = ? WHERE id = ?", J.str(b, "admin_note"), id);
        return Map.of("success", true);
    }

    // ------------------------------------------------------------- withdrawals & balance

    public List<Map<String, Object>> withdrawals() {
        return db.list("SELECT w.*, u.name, u.email FROM withdrawals w JOIN users u ON w.user_id = u.id ORDER BY w.requested_at DESC");
    }

    public Map<String, Object> processWithdrawal(long id, String status, String transactionId, MultipartFile screenshot) {
        if (status == null || !WITHDRAWAL_STATUSES.contains(status)) throw ApiException.badRequest("ID and status required");
        Map<String, Object> w = db.one("SELECT screenshot_url FROM withdrawals WHERE id = ?", id);
        if (w == null) throw ApiException.notFound("Withdrawal not found");
        String url = Rows.str(w, "screenshot_url", "");
        if (screenshot != null && !screenshot.isEmpty()) {
            url = files.save(screenshot, "withdrawal_" + id, FileStorage.IMAGES_AND_PDF);
        }
        db.update("UPDATE withdrawals SET status = ?, transaction_id = ?, screenshot_url = ?, processed_at = ? WHERE id = ?",
                status, transactionId == null ? "" : transactionId, url, LocalDateTime.now(), id);
        return Map.of("success", true, "screenshot_url", url);
    }

    public Map<String, Object> adjustBalance(AuthUser admin, JsonNode b) {
        long userId = J.lng(b, "user_id", 0);
        BigDecimal amount = J.money(b, "amount");
        String type = J.str(b, "type", "add");
        if (userId <= 0) throw ApiException.badRequest("Missing required parameters");
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("Invalid amount. Must be a positive number.");
        if (!type.equals("add") && !type.equals("remove")) throw ApiException.badRequest("Invalid type. Must be \"add\" or \"remove\".");
        if (db.one("SELECT id FROM users WHERE id = ?", userId) == null) throw ApiException.notFound("User not found");
        db.update("""
                INSERT INTO referral_earnings (user_id, referred_user_id, subscription_id, amount, description,
                                               adjusted_by_admin_id, is_admin_adjustment, status, created_at)
                VALUES (?, NULL, NULL, ?, ?, ?, 1, 'completed', CURRENT_TIMESTAMP)""",
                userId, type.equals("remove") ? amount.negate() : amount, J.str(b, "description", ""), admin.id());
        return Map.of("success", true, "message", "Balance adjusted successfully");
    }

    // ------------------------------------------------------------- settings & CMS

    public Map<String, Object> saveSettings(Map<String, String[]> fields, Map<String, MultipartFile> uploads) {
        fields.forEach((key, values) -> {
            // admin_id came from the old PHP form; _t is the cache-buster query param.
            if (key.equals("admin_id") || key.equals("_t") || values == null || values.length == 0) return;
            requireSettingKey(key);
            settings.put(key, values[0]);
        });
        if (uploads != null) {
            uploads.forEach((key, file) -> {
                if (file == null || file.isEmpty()) return;
                requireSettingKey(key);
                settings.put(key, files.save(file, key, FileStorage.IMAGES));
            });
        }
        return Map.of("success", true);
    }

    public Map<String, Object> savePage(JsonNode b) {
        String slug = J.text(b, "slug", "");
        if (slug.isEmpty() || !slug.matches("[a-z0-9-]{1,100}")) throw ApiException.badRequest("Slug required (a-z, 0-9, -)");
        String title = J.str(b, "title", "");
        String content = J.str(b, "content", "");
        int updated = db.update("UPDATE pages SET title = ?, content = ? WHERE slug = ?", title, content, slug);
        if (updated == 0) db.update("INSERT INTO pages (slug, title, content) VALUES (?, ?, ?)", slug, title, content);
        return Map.of("success", true);
    }

    // ------------------------------------------------------------- helpers

    private static void requireSettingKey(String key) {
        if (!key.matches("[A-Za-z0-9_\\-]{1,100}")) throw ApiException.badRequest("Invalid setting name: " + key);
    }

    private static Integer maxUses(JsonNode b) {
        String raw = J.str(b, "max_uses");
        if (raw == null || raw.isBlank()) return null;
        Long v = J.lng(b, "max_uses");
        return v == null ? null : v.intValue();
    }

    /** Accepts "2026-11-19", "2026-11-19T10:30" (datetime-local) or "2026-11-19 10:30:00". */
    static LocalDateTime dateTime(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().replace(' ', 'T');
        if (v.length() == 10) v += "T00:00:00";
        else if (v.length() == 16) v += ":00";
        try {
            return LocalDateTime.parse(v.length() > 19 ? v.substring(0, 19) : v);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid date: " + s);
        }
    }
}
