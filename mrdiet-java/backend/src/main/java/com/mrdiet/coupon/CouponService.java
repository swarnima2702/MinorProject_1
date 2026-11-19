package com.mrdiet.coupon;

import com.mrdiet.common.Db;
import com.mrdiet.common.Rows;
import com.mrdiet.core.Plans;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Coupon checks shared by the public "Apply coupon" button and the server-side price quote. */
@Service
public class CouponService {

    /** Result of checking a code against an amount. {@code message} is shown to the user. */
    public record Check(boolean valid, String message, String code, String discountType,
                        BigDecimal discountValue, BigDecimal discount, BigDecimal finalAmount) {}

    private final Db db;

    public CouponService(Db db) {
        this.db = db;
    }

    public Check check(String rawCode, String planType, BigDecimal amount) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        String plan = (planType == null || planType.isBlank()) ? "all" : planType.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty()) return invalid("Enter a coupon code", amount);
        if (amount == null || amount.signum() <= 0) return invalid("Invalid amount", amount);

        Map<String, Object> c = db.one("SELECT * FROM coupons WHERE UPPER(code) = ?", code);
        if (c == null) return invalid("Coupon not found", amount);

        String applies = Rows.str(c, "applies_to", "all").toLowerCase(Locale.ROOT);
        if (!applies.equals("all") && !applies.equals(plan)) return invalid("Coupon not applicable to this plan", amount);
        if (!Rows.bool(c, "is_active")) return invalid("Coupon is inactive", amount);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = Rows.dateTime(c, "valid_from");
        LocalDateTime until = Rows.dateTime(c, "valid_until");
        if (from != null && from.isAfter(now)) return invalid("Coupon not yet active", amount);
        if (until != null && until.isBefore(now)) return invalid("Coupon expired", amount);

        Long maxUses = Rows.lng(c, "max_uses");
        if (maxUses != null && Rows.lng(c, "used_count", 0) >= maxUses) return invalid("Coupon limit reached", amount);

        BigDecimal min = Rows.money(c, "min_amount");
        if (amount.compareTo(min) < 0) return invalid("Minimum order ₹" + min + " required", amount);

        String type = Rows.str(c, "discount_type", "percent");
        BigDecimal value = Rows.money(c, "discount_value");
        BigDecimal discount = Plans.couponDiscount(amount, type, value);
        return new Check(true, "Coupon applied!", Rows.str(c, "code"), type, value, discount,
                Plans.nonNegative(amount.subtract(discount)));
    }

    public void markUsed(String code) {
        if (code == null || code.isBlank()) return;
        db.update("UPDATE coupons SET used_count = used_count + 1 WHERE UPPER(code) = ?", code.trim().toUpperCase(Locale.ROOT));
    }

    /** JSON for coupons.php?action=validate — identical keys. */
    public static Map<String, Object> toJson(Check c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", c.valid());
        if (c.valid()) {
            out.put("code", c.code());
            out.put("discount_type", c.discountType());
            out.put("discount_value", c.discountValue());
            out.put("discount_amount", c.discount());
            out.put("final_amount", c.finalAmount());
        }
        out.put("message", c.message());
        return out;
    }

    private static Check invalid(String msg, BigDecimal amount) {
        return new Check(false, msg, null, null, null, BigDecimal.ZERO, amount);
    }
}
