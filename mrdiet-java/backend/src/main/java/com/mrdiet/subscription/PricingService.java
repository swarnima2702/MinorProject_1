package com.mrdiet.subscription;

import com.mrdiet.common.Db;
import com.mrdiet.common.Rows;
import com.mrdiet.coupon.CouponService;
import com.mrdiet.core.Plans;
import com.mrdiet.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Works out what a plan costs RIGHT NOW for this user — on the server.
 * In the PHP version the browser sent the amount and the backend trusted it.
 */
@Service
public class PricingService {

    public record Quote(String mode, String planType, BigDecimal basePrice, BigDecimal credit, BigDecimal discount,
                        BigDecimal finalAmount, int days, LocalDate startBase, LocalDate endDate,
                        Long replacesSubscriptionId, String couponCode, boolean blocked, String message,
                        Map<String, Object> current, long daysRemaining) {}

    private final Db db;
    private final SettingsService settings;
    private final CouponService coupons;

    public PricingService(Db db, SettingsService settings, CouponService coupons) {
        this.db = db;
        this.settings = settings;
        this.coupons = coupons;
    }

    public BigDecimal planPrice(String planType) {
        String key = Plans.priceKey(planType);
        if (key == null) return BigDecimal.ZERO.setScale(2);
        return Plans.money(settings.getDouble(key, Plans.defaultPrice(planType)));
    }

    public Map<String, Object> activeSubscription(long userId) {
        return db.one("""
                SELECT s.*, p.name AS profile_name, u.name AS user_name
                FROM subscriptions s
                JOIN profiles p ON s.profile_id = p.id
                JOIN users u ON s.user_id = u.id
                WHERE s.user_id = ? AND s.payment_status = 'completed' AND s.end_date >= ?
                ORDER BY s.start_date DESC, s.id DESC LIMIT 1""", userId, LocalDate.now());
    }

    public Quote quote(long userId, String planType, String couponCode) {
        String plan = Plans.normalize(planType);
        LocalDate today = LocalDate.now();
        BigDecimal base = planPrice(plan);
        int days = Plans.days(plan);
        Map<String, Object> current = activeSubscription(userId);

        String mode = "new";
        BigDecimal credit = BigDecimal.ZERO.setScale(2);
        LocalDate startBase = today;
        Long replaces = null;
        boolean blocked = false;
        String message = "No active plan — full price applies.";
        long daysRem = 0;

        if (current != null) {
            int curTier = Plans.tier(Rows.str(current, "plan_type"));
            int newTier = Plans.tier(plan);
            LocalDate curEnd = Rows.date(current, "end_date");
            daysRem = Plans.daysRemaining(today, curEnd);
            if (newTier == curTier) {
                mode = "renew";
                if (curEnd != null && curEnd.isAfter(today)) startBase = curEnd;
                message = "Renewal — your access will extend by " + days + " day(s) after the current period.";
            } else if (newTier < curTier) {
                mode = "downgrade";
                blocked = true;
                message = "Downgrade not allowed mid-cycle. Your " + Rows.str(current, "plan_type")
                        + " plan is active till " + curEnd + ".";
            } else {
                mode = "upgrade";
                credit = Plans.prorataCredit(Rows.money(current, "amount"),
                        Rows.integer(current, "plan_days", 1), daysRem);
                replaces = Rows.lng(current, "id");
                message = "Upgrade — ₹" + credit + " credit from your remaining " + daysRem + " day(s) applied.";
            }
        }

        // Coupon is measured against the full plan price (same as the checkout page shows it).
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        String appliedCoupon = null;
        if (couponCode != null && !couponCode.isBlank()) {
            CouponService.Check c = coupons.check(couponCode, plan, base);
            if (c.valid()) {
                discount = c.discount();
                appliedCoupon = c.code();
            }
        }
        BigDecimal finalAmount = Plans.nonNegative(base.subtract(credit).subtract(discount));
        return new Quote(mode, plan, base, credit, discount, finalAmount, days, startBase,
                startBase.plusDays(days), replaces, appliedCoupon, blocked, message, current, daysRem);
    }

    /** JSON for subscriptions.php?action=preview_change — same keys as before. */
    public Map<String, Object> preview(long userId, String newPlan) {
        Quote q = quote(userId, newPlan, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", q.mode());
        out.put("current_plan", q.current() == null ? null : q.current().get("plan_type"));
        if (q.replacesSubscriptionId() != null) out.put("current_sub_id", q.replacesSubscriptionId());
        out.put("new_plan", q.planType());
        if (q.current() != null) out.put("current_end", q.current().get("end_date"));
        out.put("new_price", q.basePrice());
        out.put("credit", q.credit());
        out.put("final_amount", q.finalAmount());
        out.put("days_remaining", q.daysRemaining());
        if (q.blocked()) out.put("blocked", true);
        out.put("message", q.message());
        return out;
    }
}
