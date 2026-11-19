package com.mrdiet.coupon;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.J;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** Was coupons.php?action=validate. */
@RestController
public class CouponController {

    private final CouponService coupons;

    public CouponController(CouponService coupons) {
        this.coupons = coupons;
    }

    @PostMapping("/api/coupons/validate")
    public Map<String, Object> validate(@RequestBody JsonNode body) {
        BigDecimal amount = J.money(body, "amount");
        return CouponService.toJson(coupons.check(J.str(body, "code"), J.str(body, "plan_type", "all"),
                amount == null ? BigDecimal.ZERO : amount));
    }
}
