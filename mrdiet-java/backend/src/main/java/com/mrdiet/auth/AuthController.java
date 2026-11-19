package com.mrdiet.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.J;
import com.mrdiet.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Was auth.php?action=signup|login|verify_google|verify_google_manual.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** Body: { email, password, data: { name, referral_code } } — same as before. */
    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody JsonNode body) {
        JsonNode data = body.path("data");
        return auth.signup(J.str(body, "email"), J.str(body, "password"),
                J.str(data, "name", J.str(body, "name")),
                J.str(data, "referral_code", J.str(body, "referral_code")));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody JsonNode body) {
        return auth.login(J.str(body, "email"), J.str(body, "password"));
    }

    /** Body: { access_token, referral_code } or { credential } (Google ID token). */
    @PostMapping("/google")
    public Map<String, Object> google(@RequestBody JsonNode body) {
        return auth.google(J.str(body, "access_token"), J.str(body, "credential"), J.str(body, "referral_code"));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthUser me) {
        if (me == null) throw ApiException.unauthorized("Please log in again");
        return auth.me(me.id());
    }
}
