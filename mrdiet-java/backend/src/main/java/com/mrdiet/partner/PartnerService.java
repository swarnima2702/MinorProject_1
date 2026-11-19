package com.mrdiet.partner;

import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.security.AuthUser;
import com.mrdiet.settings.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Was partner.php — referral dashboard and payout requests. */
@Service
public class PartnerService {

    private final Db db;
    private final SettingsService settings;
    private final ReferralService referrals;

    public PartnerService(Db db, SettingsService settings, ReferralService referrals) {
        this.db = db;
        this.settings = settings;
        this.referrals = referrals;
    }

    public Map<String, Object> stats(AuthUser me) {
        long uid = me.id();
        referrals.backfill(uid);

        String code = db.scalar("SELECT referral_code FROM users WHERE id = ?", String.class, uid);
        long totalReferrals = db.scalar("SELECT COUNT(*) FROM users WHERE referred_by = ?", Long.class, uid);
        BigDecimal earned = sum("SELECT COALESCE(SUM(amount), 0) FROM referral_earnings WHERE user_id = ?", uid);
        BigDecimal withdrawn = sum("SELECT COALESCE(SUM(amount), 0) FROM withdrawals WHERE user_id = ? AND status = 'approved'", uid);
        BigDecimal pending = sum("SELECT COALESCE(SUM(amount), 0) FROM withdrawals WHERE user_id = ? AND status = 'pending'", uid);

        List<Map<String, Object>> recentEarnings = db.list("""
                SELECT re.id, re.amount, re.description, re.created_at,
                       u.name AS referred_user_name, COALESCE(u.name, adm.name) AS user_name,
                       re.adjusted_by_admin_id, re.is_admin_adjustment, re.referred_user_id
                FROM referral_earnings re
                LEFT JOIN users u ON re.referred_user_id = u.id
                LEFT JOIN users adm ON re.adjusted_by_admin_id = adm.id
                WHERE re.user_id = ?
                ORDER BY re.created_at DESC LIMIT 10""", uid);
        List<Map<String, Object>> withdrawals = db.list("""
                SELECT id, amount, status, transaction_id, requested_at, screenshot_url
                FROM withdrawals WHERE user_id = ? ORDER BY requested_at DESC LIMIT 20""", uid);
        List<Map<String, Object>> referred = db.list("""
                SELECT u.id, u.name, u.name AS user_name, u.email, u.created_at,
                       re.amount AS earning_amount, re.amount AS commission_amount
                FROM users u
                LEFT JOIN referral_earnings re ON re.referred_user_id = u.id AND re.user_id = ?
                WHERE u.referred_by = ?
                ORDER BY u.created_at DESC LIMIT 50""", uid, uid);

        String minPayout = settings.get("minWithdrawalAmount", "100");
        BigDecimal balance = earned.subtract(withdrawn).subtract(pending);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_referrals", totalReferrals);
        stats.put("total_earned", fmt(earned));
        stats.put("total_withdrawn", fmt(withdrawn));
        stats.put("pending_withdrawal", fmt(pending));
        stats.put("min_payout", minPayout);

        // Same keys as partner.php (several are aliases PartnerProgram.tsx reads).
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("referral_code", code == null ? String.valueOf(uid) : code);
        out.put("stats", stats);
        out.put("recentEarnings", recentEarnings);
        out.put("withdrawalHistory", withdrawals);
        out.put("referredUsers", referred);
        out.put("referrals", totalReferrals);
        out.put("referrals_list", referred);
        out.put("earnings", recentEarnings);
        out.put("payouts", withdrawals);
        out.put("total_pay", fmt(earned));
        out.put("balance", fmt(balance));
        out.put("min_payout", minPayout);
        return out;
    }

    /**
     * Balance = everything earned − approved payouts − pending payouts.
     * (PHP subtracted 'processed' payouts, a status that never exists in this
     * table, so already-approved money could be withdrawn a second time.)
     */
    @Transactional
    public Map<String, Object> requestPayout(AuthUser me, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("Enter a valid amount");
        double min = settings.getDouble("minWithdrawalAmount", 100);
        if (amount.doubleValue() < min) {
            throw ApiException.badRequest("Minimum payout is ₹" + BigDecimal.valueOf(min).stripTrailingZeros().toPlainString());
        }
        // Lock this user's row so two quick clicks can't both pass the balance check.
        db.one("SELECT id FROM users WHERE id = ? FOR UPDATE", me.id());
        BigDecimal earned = sum("SELECT COALESCE(SUM(amount), 0) FROM referral_earnings WHERE user_id = ?", me.id());
        BigDecimal committed = sum("SELECT COALESCE(SUM(amount), 0) FROM withdrawals WHERE user_id = ? AND status IN ('approved', 'pending')", me.id());
        if (amount.compareTo(earned.subtract(committed)) > 0) throw ApiException.badRequest("Insufficient balance");
        db.update("INSERT INTO withdrawals (user_id, amount, status, requested_at) VALUES (?, ?, 'pending', CURRENT_TIMESTAMP)",
                me.id(), amount);
        return Map.of("success", true, "message", "Payout request submitted");
    }

    private BigDecimal sum(String sql, Object... args) {
        BigDecimal v = db.scalar(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
