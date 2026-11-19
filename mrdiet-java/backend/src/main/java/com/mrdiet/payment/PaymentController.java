package com.mrdiet.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Was payment.php?action=create_order|verify. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return payments.createOrder(me, body);
    }

    @PostMapping("/verify")
    public Object verify(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return payments.verify(me, body);
    }
}
