package com.mrdiet.partner;

import com.mrdiet.common.Db;
import com.mrdiet.common.Rows;
import com.mrdiet.core.Plans;
import com.mrdiet.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** processReferralCommission() from subscriptions.php — one commission per paid subscription. */
@Service
public class ReferralService {

    private final Db db;
    private final SettingsService settings;

    public ReferralService(Db db, SettingsService settings) {
        this.db = db;
        this.settings = settings;
    }

    public void processCommission(long subscriptionId, long buyerUserId, String planType, BigDecimal amountPaid) {
        if (amountPaid == null || amountPaid.signum() <= 0) return; // free / 100%-coupon plans earn nothing
        Long referredBy = db.scalar("SELECT referred_by FROM users WHERE id = ?", Long.class, buyerUserId);
        if (referredBy == null || referredBy == buyerUserId) return;
        if (db.one("SELECT id FROM referral_earnings WHERE subscription_id = ?", subscriptionId) != null) return;
        if (!"1".equals(settings.get("referralEnabled", "0"))) return;

        double value = settings.getDouble(Plans.commissionKey(planType), 0);
        BigDecimal commission = Plans.commission(settings.get("referralCommissionType", "fixed"), value, amountPaid);
        if (commission.signum() > 0) {
            db.update("INSERT INTO referral_earnings (user_id, referred_user_id, subscription_id, amount, status, created_at) "
                    + "VALUES (?, ?, ?, ?, 'pending', CURRENT_TIMESTAMP)", referredBy, buyerUserId, subscriptionId, commission);
        }
    }

    /** Back-fills commissions for a referrer's paid referrals that were missed (partner.php "stats"). */
    public void backfill(long referrerId) {
        for (var row : db.list("""
                SELECT s.id AS subscription_id, s.user_id AS referee_id, s.plan_type, s.amount
                FROM subscriptions s JOIN users u ON s.user_id = u.id
                WHERE u.referred_by = ?
                  AND s.payment_status IN ('completed','active','paid','success','captured')
                  AND NOT EXISTS (SELECT 1 FROM referral_earnings re WHERE re.subscription_id = s.id)""", referrerId)) {
            processCommission(Rows.lng(row, "subscription_id"), Rows.lng(row, "referee_id"),
                    Rows.str(row, "plan_type"), Rows.money(row, "amount"));
        }
    }
}
