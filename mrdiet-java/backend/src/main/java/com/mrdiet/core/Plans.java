package com.mrdiet.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

/**
 * Plan rules shared by checkout, admin and payments (pure Java, no Spring).
 * Ported from plan_tier(), plan_days_for(), plan_price() and the coupon math in PHP.
 */
public final class Plans {

    private Plans() {}

    /** Payment statuses that count as "paid" (same list PHP used). */
    public static final Set<String> PAID_STATES = Set.of("completed", "active", "paid", "success", "captured");

    public static String normalize(String planType) {
        return planType == null ? "" : planType.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isPurchasable(String planType) {
        String t = normalize(planType);
        return t.equals("daily") || t.equals("weekly") || t.equals("monthly");
    }

    public static int days(String planType) {
        String t = normalize(planType);
        if (t.contains("monthly")) return 30;
        if (t.contains("weekly")) return 7;
        if (t.contains("daily")) return 1;
        if (t.contains("trial")) return 1;
        return 0;
    }

    /** Higher = better. trial 0, daily 1, weekly 2, monthly 3. */
    public static int tier(String planType) {
        String t = normalize(planType);
        if (t.contains("monthly")) return 3;
        if (t.contains("weekly")) return 2;
        if (t.contains("daily")) return 1;
        return 0;
    }

    /** Settings key holding the live price, or null for trial/unknown. */
    public static String priceKey(String planType) {
        String t = normalize(planType);
        if (t.contains("monthly")) return "priceMonthly";
        if (t.contains("weekly")) return "priceWeekly";
        if (t.contains("daily")) return "priceDaily";
        return null;
    }

    public static double defaultPrice(String planType) {
        String t = normalize(planType);
        if (t.contains("monthly")) return 1999;
        if (t.contains("weekly")) return 599;
        if (t.contains("daily")) return 99;
        return 0;
    }

    public static String commissionKey(String planType) {
        String t = normalize(planType);
        if (t.contains("monthly")) return "referralCommissionMonthly";
        if (t.contains("weekly")) return "referralCommissionWeekly";
        if (t.contains("daily")) return "referralCommissionDaily";
        return "referralCommissionWeekly"; // PHP fallback for custom names
    }

    public static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** Whole days left on a plan, counting from today (0 if it already ended). */
    public static long daysRemaining(LocalDate today, LocalDate endDate) {
        if (endDate == null || endDate.isBefore(today)) return 0;
        return ChronoUnit.DAYS.between(today, endDate);
    }

    /** Unused value of the current plan, credited on upgrade. */
    public static BigDecimal prorataCredit(BigDecimal paidAmount, int planDays, long daysRemaining) {
        int total = Math.max(1, planDays);
        return paidAmount.multiply(BigDecimal.valueOf(daysRemaining))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /** Coupon discount on {@code amount}; never more than the amount itself. */
    public static BigDecimal couponDiscount(BigDecimal amount, String discountType, BigDecimal discountValue) {
        BigDecimal discount = "percent".equalsIgnoreCase(discountType)
                ? amount.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : discountValue.setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(amount) > 0) discount = amount;
        if (discount.signum() < 0) discount = BigDecimal.ZERO.setScale(2);
        return discount;
    }

    public static BigDecimal nonNegative(BigDecimal v) {
        return v.signum() < 0 ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Referral commission: fixed rupees, or a percentage of what the friend paid. */
    public static BigDecimal commission(String commissionType, double value, BigDecimal paidAmount) {
        if (value <= 0) return BigDecimal.ZERO.setScale(2);
        if ("percentage".equalsIgnoreCase(commissionType)) {
            return paidAmount.multiply(BigDecimal.valueOf(value)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return money(value);
    }
}
