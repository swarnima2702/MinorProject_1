package com.mrdiet.subscription;

import com.mrdiet.common.Db;
import com.mrdiet.common.Rows;
import com.mrdiet.coupon.CouponService;
import com.mrdiet.partner.ReferralService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * The ONLY place a subscription becomes "completed". It is called after a
 * gateway confirms payment, for ₹0 checkouts, and by admins — never directly
 * by the browser. Safe to call twice: the second call does nothing.
 */
@Service
public class SubscriptionActivator {

    private final Db db;
    private final CouponService coupons;
    private final ReferralService referrals;

    public SubscriptionActivator(Db db, CouponService coupons, ReferralService referrals) {
        this.db = db;
        this.coupons = coupons;
        this.referrals = referrals;
    }

    /** @return true if this call activated it, false if it was already active. */
    @Transactional
    public boolean activate(long subscriptionId, String transactionId) {
        int changed = db.update("""
                UPDATE subscriptions
                SET payment_status = 'completed', transaction_id = COALESCE(?, transaction_id)
                WHERE id = ? AND payment_status NOT IN ('completed','active','paid','success','captured')""",
                transactionId, subscriptionId);
        if (changed == 0) return false;

        Map<String, Object> sub = db.one("SELECT * FROM subscriptions WHERE id = ?", subscriptionId);
        long userId = Rows.lng(sub, "user_id");

        // Upgrade: close the old plan only now that the new one is paid for
        // (PHP closed it before payment, so an abandoned checkout lost the old plan).
        Long replaces = Rows.lng(sub, "replaces_subscription_id");
        if (replaces != null) {
            db.update("UPDATE subscriptions SET payment_status = 'upgraded', end_date = ? WHERE id = ? AND user_id = ?",
                    LocalDate.now(), replaces, userId);
        }
        coupons.markUsed(Rows.str(sub, "coupon_code"));
        referrals.processCommission(subscriptionId, userId, Rows.str(sub, "plan_type"), Rows.money(sub, "amount"));
        return true;
    }
}
