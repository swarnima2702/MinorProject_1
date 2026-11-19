package com.mrdiet.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.core.Plans;
import com.mrdiet.security.AuthUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Was subscriptions.php. */
@Service
public class SubscriptionService {

    private final Db db;
    private final PricingService pricing;
    private final SubscriptionActivator activator;

    public SubscriptionService(Db db, PricingService pricing, SubscriptionActivator activator) {
        this.db = db;
        this.pricing = pricing;
        this.activator = activator;
    }

    public Map<String, Object> active(AuthUser me) {
        return pricing.activeSubscription(me.id());
    }

    public List<Map<String, Object>> all(AuthUser me) {
        return db.list("""
                SELECT s.*, p.name AS profile_name, u.name AS user_name
                FROM subscriptions s
                JOIN profiles p ON s.profile_id = p.id
                JOIN users u ON s.user_id = u.id
                WHERE s.user_id = ?
                ORDER BY s.start_date DESC, s.id DESC""", me.id());
    }

    /**
     * Creates a plan for the logged-in user. The browser's amount, end date and
     * payment_status are IGNORED — the server prices it, and a paid plan stays
     * "pending" until the payment gateway confirms it.
     */
    public Map<String, Object> create(AuthUser me, JsonNode body) {
        // GetStarted.tsx re-uses this endpoint to report a finished Razorpay payment.
        if ("update_status".equals(J.str(body, "action")) && J.has(body, "id")) {
            return updateStatus(me, J.lng(body, "id", 0), body);
        }

        String planType = Plans.normalize(J.str(body, "plan_type"));
        long profileId = resolveProfile(me, J.lng(body, "profile_id"));
        String method = sanitizeMethod(J.str(body, "payment_method", "online"));

        if (planType.equals("trial")) {
            Long used = db.scalar("SELECT COUNT(*) FROM subscriptions WHERE user_id = ? AND LOWER(plan_type) = 'trial'",
                    Long.class, me.id());
            if (used != null && used > 0) throw ApiException.conflict("Your free trial has already been used");
            LocalDate today = LocalDate.now();
            long id = db.insert("""
                    INSERT INTO subscriptions (user_id, profile_id, plan_type, plan_days, end_date, amount, payment_method, payment_status)
                    VALUES (?, ?, 'trial', 1, ?, 0, 'free', 'completed')""", me.id(), profileId, today.plusDays(1));
            return result(id, me.id(), profileId, "completed", BigDecimal.ZERO);
        }

        if (!Plans.isPurchasable(planType)) throw ApiException.badRequest("Unknown plan: " + planType);

        PricingService.Quote q = pricing.quote(me.id(), planType, J.str(body, "coupon_code"));
        if (q.blocked()) throw ApiException.badRequest(q.message());

        long id = db.insert("""
                INSERT INTO subscriptions (user_id, profile_id, plan_type, plan_days, end_date, amount, payment_method,
                                           payment_status, coupon_code, replaces_subscription_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)""",
                me.id(), profileId, planType, q.days(), q.endDate(), q.finalAmount(), method,
                q.couponCode(), q.replacesSubscriptionId());

        String status = "pending";
        if (q.finalAmount().signum() == 0) {
            // Upgrade credit + coupon covered everything — nothing to pay.
            activator.activate(id, "FREE-" + id);
            status = "completed";
        }
        return result(id, me.id(), profileId, status, q.finalAmount());
    }

    /**
     * PATCH subscriptions.php?id=… — the React app calls this after payment.
     * It can only move a plan to "completed" if the gateway already confirmed a
     * payment for it (see PaymentService.verify). Otherwise → 402.
     */
    public Map<String, Object> updateStatus(AuthUser me, long id, JsonNode body) {
        Map<String, Object> sub = db.one("SELECT * FROM subscriptions WHERE id = ? AND user_id = ?", id, me.id());
        if (sub == null) throw ApiException.notFound("Subscription not found");

        String requested = J.str(body, "payment_status", "").toLowerCase(Locale.ROOT);
        String current = Rows.str(sub, "payment_status", "").toLowerCase(Locale.ROOT);

        if (Plans.PAID_STATES.contains(requested)) {
            if (Plans.PAID_STATES.contains(current)) return Map.of("success", true);
            Map<String, Object> order = db.one(
                    "SELECT * FROM payment_orders WHERE subscription_id = ? AND status = 'paid' ORDER BY id DESC LIMIT 1", id);
            if (order == null) throw ApiException.paymentRequired("Payment not confirmed yet");
            String txn = Rows.str(order, "payment_id", Rows.str(order, "order_id"));
            activator.activate(id, txn);
            return Map.of("success", true);
        }
        if ((requested.equals("failed") || requested.equals("cancelled")) && current.equals("pending")) {
            db.update("UPDATE subscriptions SET payment_status = ? WHERE id = ?", requested, id);
            return Map.of("success", true);
        }
        throw ApiException.badRequest("Status change not allowed");
    }

    public Map<String, Object> preview(AuthUser me, String newPlan) {
        if (newPlan == null || newPlan.isBlank()) throw ApiException.badRequest("new_plan required");
        return pricing.preview(me.id(), newPlan);
    }

    /** Returns a profile id that belongs to this user (the body's, or their own). */
    private long resolveProfile(AuthUser me, Long requested) {
        if (requested != null && requested > 0) {
            Long owned = db.scalar("SELECT id FROM profiles WHERE id = ? AND user_id = ?", Long.class, requested, me.id());
            if (owned != null) return owned;
        }
        Long own = db.scalar("SELECT id FROM profiles WHERE user_id = ?", Long.class, me.id());
        if (own == null) throw ApiException.badRequest("Please complete your profile first");
        return own;
    }

    private static String sanitizeMethod(String m) {
        String s = m == null ? "online" : m.replaceAll("[^A-Za-z0-9_-]", "");
        return s.isEmpty() ? "online" : s.substring(0, Math.min(50, s.length()));
    }

    private static Map<String, Object> result(long id, long userId, long profileId, String status, BigDecimal amount) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("user_id", userId);
        out.put("profile_id", profileId);
        out.put("payment_status", status);
        out.put("amount", amount);
        return out;
    }
}
