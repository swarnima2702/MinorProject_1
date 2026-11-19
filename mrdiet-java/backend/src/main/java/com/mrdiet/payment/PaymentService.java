package com.mrdiet.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.HttpJson;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.config.AppProperties;
import com.mrdiet.security.AuthUser;
import com.mrdiet.settings.SettingsService;
import com.mrdiet.subscription.SubscriptionActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Was payment.php. Two important changes:
 *  1. The amount charged is the pending subscription's server-side price —
 *     the browser can no longer choose what it pays.
 *  2. When the gateway confirms payment, THIS service activates the plan.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final Db db;
    private final HttpJson http;
    private final SettingsService settings;
    private final AppProperties props;
    private final SubscriptionActivator activator;
    private final ObjectMapper mapper;

    public PaymentService(Db db, HttpJson http, SettingsService settings, AppProperties props,
                          SubscriptionActivator activator, ObjectMapper mapper) {
        this.db = db;
        this.http = http;
        this.settings = settings;
        this.props = props;
        this.activator = activator;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ create

    public Map<String, Object> createOrder(AuthUser me, JsonNode body) {
        String planType = J.text(body, "plan_type", null);
        Map<String, Object> sub = planType == null
                ? db.one("SELECT * FROM subscriptions WHERE user_id = ? AND payment_status = 'pending' ORDER BY id DESC LIMIT 1", me.id())
                : db.one("SELECT * FROM subscriptions WHERE user_id = ? AND payment_status = 'pending' AND LOWER(plan_type) = ? ORDER BY id DESC LIMIT 1",
                me.id(), planType.toLowerCase(Locale.ROOT));
        if (sub == null) throw ApiException.badRequest("No pending plan found. Please choose your plan again.");

        BigDecimal amount = Rows.money(sub, "amount");
        if (amount.signum() <= 0) throw ApiException.badRequest("Nothing to pay for this plan");
        long subId = Rows.lng(sub, "id");

        String phone = sanitizePhone(J.str(body, "customer_phone"));
        String name = J.text(body, "customer_name", me.name() == null ? "Customer" : me.name());
        String email = J.text(body, "customer_email", me.email());
        String gateway = resolveGateway(J.str(body, "gateway"));

        return gateway.equals("razorpay")
                ? razorpayOrder(me, subId, amount, name, email, phone)
                : cashfreeOrder(me, subId, amount, name, email, phone, J.str(body, "return_url"));
    }

    private Map<String, Object> razorpayOrder(AuthUser me, long subId, BigDecimal amount, String name, String email,
                                              String phone) {
        String keyId = settings.get("razorpay_key_id", props.razorpay().keyId());
        String keySecret = settings.get("razorpay_key_secret", props.razorpay().keySecret());
        if (isBlank(keyId) || isBlank(keySecret)) throw ApiException.badRequest("Razorpay keys not configured");

        String receipt = "SUB_" + subId + "_" + System.currentTimeMillis();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("amount", amount.movePointRight(2).longValue()); // paise
        payload.put("currency", "INR");
        payload.put("receipt", receipt.substring(0, Math.min(40, receipt.length())));
        ObjectNode notes = payload.putObject("notes");
        notes.put("customer_id", String.valueOf(me.id()));
        notes.put("customer_name", name);
        notes.put("customer_email", email);
        notes.put("customer_phone", phone);

        String basic = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        HttpJson.Result r = http.send(http.client().post().uri("https://api.razorpay.com/v1/orders")
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload.toString()));
        if (!r.ok() || !r.body().hasNonNull("id")) {
            log.warn("Razorpay order failed ({}): {}", r.status(), r.body());
            throw ApiException.upstream(r.body().path("error").path("description").asText("Razorpay order failed"));
        }
        String orderId = r.body().get("id").asText();
        recordOrder(orderId, me.id(), subId, "razorpay", amount);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gateway", "razorpay");
        out.put("order_id", orderId);
        out.put("amount", r.body().path("amount").asLong());
        out.put("currency", r.body().path("currency").asText("INR"));
        out.put("key_id", keyId);
        out.put("prefill", Map.of("name", name, "email", email, "contact", phone));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cashfreeOrder(AuthUser me, long subId, BigDecimal amount, String name, String email,
                                              String phone, String returnUrl) {
        Cashfree cf = cashfree();
        String orderId = "ORDER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 13) + "_"
                + (System.currentTimeMillis() / 1000);
        String ret = isBlank(returnUrl) ? props.publicUrl() + "/dashboard?order_id={order_id}" : returnUrl;

        ObjectNode payload = mapper.createObjectNode();
        payload.put("order_id", orderId);
        payload.put("order_amount", amount);
        payload.put("order_currency", "INR");
        ObjectNode customer = payload.putObject("customer_details");
        customer.put("customer_id", "CUST_" + me.id());
        customer.put("customer_name", name);
        customer.put("customer_email", email);
        customer.put("customer_phone", phone);
        payload.putObject("order_meta").put("return_url", ret);

        HttpJson.Result r = http.send(http.client().post().uri(cf.baseUrl() + "/orders")
                .header("x-client-id", cf.appId())
                .header("x-client-secret", cf.secret())
                .header("x-api-version", cf.apiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload.toString()));
        if (!r.ok() || !r.body().hasNonNull("payment_session_id")) {
            log.warn("Cashfree order failed ({}): {}", r.status(), r.body());
            throw ApiException.badRequest(r.body().path("message").asText("Payment initiation failed"));
        }
        recordOrder(orderId, me.id(), subId, "cashfree", amount);

        Map<String, Object> out = mapper.convertValue(r.body(), Map.class);
        out.put("gateway", "cashfree");
        out.put("cf_mode", cf.sandbox() ? "sandbox" : "production");
        return out;
    }

    // ------------------------------------------------------------------ verify

    public Object verify(AuthUser me, JsonNode body) {
        String gateway = J.str(body, "gateway", resolveGateway(null));
        return "razorpay".equals(gateway) ? verifyRazorpay(me, body) : verifyCashfree(me, J.str(body, "order_id"));
    }

    private Map<String, Object> verifyRazorpay(AuthUser me, JsonNode body) {
        String orderId = J.str(body, "razorpay_order_id");
        String paymentId = J.str(body, "razorpay_payment_id");
        String signature = J.str(body, "razorpay_signature");
        String keySecret = settings.get("razorpay_key_secret", props.razorpay().keySecret());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gateway", "razorpay");
        out.put("order_id", orderId);
        out.put("payment_id", paymentId);
        if (isBlank(orderId) || isBlank(paymentId) || isBlank(signature) || isBlank(keySecret)) {
            out.put("order_status", "FAILED");
            out.put("error", "Missing params");
            return out;
        }
        Map<String, Object> order = db.one("SELECT * FROM payment_orders WHERE order_id = ? AND user_id = ?", orderId, me.id());
        boolean valid = order != null && signatureMatches(orderId + "|" + paymentId, signature, keySecret);
        if (valid) markPaidAndActivate(order, paymentId);
        out.put("order_status", valid ? "PAID" : "FAILED");
        return out;
    }

    private Object verifyCashfree(AuthUser me, String orderId) {
        if (isBlank(orderId)) throw ApiException.badRequest("order_id required");
        Map<String, Object> order = db.one("SELECT * FROM payment_orders WHERE order_id = ? AND user_id = ?", orderId, me.id());
        if (order == null) {
            return Map.of("order_status", "FAILED", "error", "Order not found");
        }
        Cashfree cf = cashfree();
        HttpJson.Result r = http.send(http.client().get().uri(cf.baseUrl() + "/orders/{id}", orderId)
                .header("x-client-id", cf.appId())
                .header("x-client-secret", cf.secret())
                .header("x-api-version", cf.apiVersion()));
        if (!r.ok()) {
            log.warn("Cashfree verify failed ({}): {}", r.status(), r.body());
            return Map.of("order_status", "UNKNOWN", "error", "Could not reach payment gateway");
        }
        String status = r.body().path("order_status").asText("");
        BigDecimal paid = new BigDecimal(r.body().path("order_amount").asText("0"));
        if ("PAID".equalsIgnoreCase(status) && paid.compareTo(Rows.money(order, "amount")) >= 0) {
            markPaidAndActivate(order, r.body().path("cf_order_id").asText(orderId));
        }
        return r.body(); // React reads order_status from Cashfree's own response, as before
    }

    private void markPaidAndActivate(Map<String, Object> order, String paymentId) {
        db.update("UPDATE payment_orders SET status = 'paid', payment_id = ?, paid_at = ? WHERE id = ? AND status <> 'paid'",
                paymentId, LocalDateTime.now(), order.get("id"));
        Long subId = Rows.lng(order, "subscription_id");
        if (subId != null) activator.activate(subId, paymentId);
    }

    // ------------------------------------------------------------------ helpers

    private void recordOrder(String orderId, long userId, long subId, String gateway, BigDecimal amount) {
        db.update("INSERT INTO payment_orders (order_id, user_id, subscription_id, gateway, amount, status) VALUES (?, ?, ?, ?, ?, 'created')",
                orderId, userId, subId, gateway, amount);
    }

    /** Admin picks the gateway in Settings; razorpay wins if both are on (same as PHP). */
    private String resolveGateway(String requested) {
        boolean razor = "1".equals(settings.get("enable_razorpay", "0"));
        boolean cashfree = "1".equals(settings.get("enable_cashfree", "0"));
        if ("razorpay".equals(requested) && razor) return "razorpay";
        if ("cashfree".equals(requested) && cashfree) return "cashfree";
        if (razor) return "razorpay";
        return "cashfree";
    }

    private record Cashfree(String appId, String secret, String apiVersion, boolean sandbox, String baseUrl) {}

    private Cashfree cashfree() {
        String appId = settings.get("cashfree_app_id", props.cashfree().appId());
        String secret = settings.get("cashfree_secret_key", props.cashfree().secretKey());
        if (isBlank(appId) || isBlank(secret)) throw ApiException.badRequest("Cashfree keys not configured");
        boolean sandbox = appId.toUpperCase(Locale.ROOT).startsWith("TEST") || secret.toLowerCase(Locale.ROOT).contains("cfsk_ma_test");
        String version = isBlank(props.cashfree().apiVersion()) ? "2022-09-01" : props.cashfree().apiVersion();
        return new Cashfree(appId, secret, version, sandbox,
                sandbox ? "https://sandbox.cashfree.com/pg" : "https://api.cashfree.com/pg");
    }

    static boolean signatureMatches(String data, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static String sanitizePhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() < 10) return "9999999999";
        return digits.substring(digits.length() - 10);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
