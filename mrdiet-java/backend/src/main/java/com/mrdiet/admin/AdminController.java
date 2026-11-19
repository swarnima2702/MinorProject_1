package com.mrdiet.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.J;
import com.mrdiet.security.AuthUser;
import com.mrdiet.settings.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything under admin/*.php. SecurityConfig only lets users whose
 * is_admin = 1 (checked in the database on every request) reach these routes —
 * the old "?admin_id=1" trick no longer works.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminUserService users;
    private final AdminCommerceService commerce;
    private final SettingsService settings;

    public AdminController(AdminUserService users, AdminCommerceService commerce, SettingsService settings) {
        this.users = users;
        this.commerce = commerce;
        this.settings = settings;
    }

    // ---- dashboard / users -------------------------------------------------

    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        return users.analytics();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return users.users();
    }

    @PostMapping("/users/{id}/plan")
    public Map<String, Object> changePlan(@AuthenticationPrincipal AuthUser admin, @PathVariable long id,
                                          @RequestBody JsonNode body) {
        return users.changePlan(admin, id, J.str(body, "plan_type"));
    }

    /** Body: { "userIds": [1,2,3] } or { "userId": 4 }. */
    @DeleteMapping("/users")
    public Map<String, Object> deleteUsers(@AuthenticationPrincipal AuthUser admin, @RequestBody JsonNode body) {
        List<Long> ids = new ArrayList<>();
        if (J.has(body, "userId")) ids.add(J.lng(body, "userId"));
        body.path("userIds").forEach(n -> ids.add(n.asLong()));
        return users.deleteUsers(admin, ids);
    }

    // ---- profiles & health metrics ------------------------------------------

    @GetMapping("/profiles")
    public List<Map<String, Object>> profiles() {
        return users.profiles();
    }

    @GetMapping("/profiles/{id}")
    public Map<String, Object> profile(@PathVariable long id) {
        return users.profile(id);
    }

    @PatchMapping("/profiles/{id}")
    public Map<String, Object> updateProfile(@PathVariable long id, @RequestBody JsonNode body) {
        return users.updateProfile(id, body);
    }

    @DeleteMapping("/profiles/{id}")
    public Map<String, Object> deleteProfile(@PathVariable long id) {
        return users.deleteProfile(id);
    }

    @GetMapping("/health-metrics")
    public Map<String, Object> healthMetrics(@RequestParam("profile_id") long profileId) {
        return users.healthMetrics(profileId);
    }

    @PostMapping("/health-metrics")
    public Map<String, Object> saveHealthMetrics(@RequestBody JsonNode body) {
        return users.saveHealthMetrics(body);
    }

    // ---- settings (multipart, like the PHP form) -----------------------------

    @GetMapping("/settings")
    public Map<String, String> getSettings() {
        return settings.all();
    }

    @PostMapping(value = "/settings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> saveSettings(HttpServletRequest request) {
        Map<String, MultipartFile> uploads = request instanceof MultipartHttpServletRequest m ? m.getFileMap() : Map.of();
        return commerce.saveSettings(request.getParameterMap(), uploads);
    }

    // ---- payouts & balances ---------------------------------------------------

    @GetMapping("/withdrawals")
    public List<Map<String, Object>> withdrawals() {
        return commerce.withdrawals();
    }

    @PostMapping(value = "/withdrawals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> processWithdrawal(@RequestParam("id") long id,
                                                 @RequestParam("status") String status,
                                                 @RequestParam(value = "transaction_id", required = false) String txn,
                                                 @RequestParam(value = "screenshot", required = false) MultipartFile screenshot) {
        return commerce.processWithdrawal(id, status, txn, screenshot);
    }

    @PostMapping("/balance-adjustments")
    public Map<String, Object> adjustBalance(@AuthenticationPrincipal AuthUser admin, @RequestBody JsonNode body) {
        return commerce.adjustBalance(admin, body);
    }

    // ---- coupons --------------------------------------------------------------

    @GetMapping("/coupons")
    public List<Map<String, Object>> coupons() {
        return commerce.coupons();
    }

    @GetMapping("/coupons/analytics")
    public Map<String, Object> couponAnalytics() {
        return commerce.couponAnalytics();
    }

    @PostMapping("/coupons")
    public Map<String, Object> createCoupon(@RequestBody JsonNode body) {
        return commerce.createCoupon(body);
    }

    @PatchMapping("/coupons/{id}")
    public Map<String, Object> updateCoupon(@PathVariable long id, @RequestBody JsonNode body) {
        return commerce.updateCoupon(id, body);
    }

    @DeleteMapping("/coupons/{id}")
    public Map<String, Object> deleteCoupon(@PathVariable long id) {
        return commerce.deleteCoupon(id);
    }

    // ---- transactions -----------------------------------------------------------

    @GetMapping("/transactions")
    public Map<String, Object> transactions(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "gateway", required = false) String gateway,
                                            @RequestParam(value = "q", required = false) String q) {
        return commerce.transactions(status, gateway, q);
    }

    @PatchMapping("/transactions/{id}")
    public Map<String, Object> updateTransaction(@PathVariable long id, @RequestBody JsonNode body) {
        return commerce.updateTransaction(id, body);
    }

    // ---- CMS pages ----------------------------------------------------------------

    @PostMapping("/pages")
    public Map<String, Object> savePage(@RequestBody JsonNode body) {
        return commerce.savePage(body);
    }
}
