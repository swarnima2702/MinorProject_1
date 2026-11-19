package com.mrdiet.auth;

import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.Rows;
import com.mrdiet.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/** Was auth.php — signup, login and Google sign-in. */
@Service
public class AuthService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Db db;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final GoogleVerifier google;

    public AuthService(Db db, PasswordEncoder passwords, JwtService jwt, GoogleVerifier google) {
        this.db = db;
        this.passwords = passwords;
        this.jwt = jwt;
        this.google = google;
    }

    @Transactional
    public Map<String, Object> signup(String email, String password, String name, String referralCode) {
        email = email == null ? "" : email.trim();
        if (email.isEmpty() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Email and password required");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("Please enter a valid email address");
        }
        if (password.length() < 6) {
            throw ApiException.badRequest("Password must be at least 6 characters");
        }
        if (db.one("SELECT id FROM users WHERE email = ?", email) != null) {
            throw ApiException.badRequest("Email already registered");
        }
        String displayName = (name == null || name.isBlank()) ? email.substring(0, email.indexOf('@')) : name.trim();
        long id = db.insert(
                "INSERT INTO users (email, password, name, referral_code, referred_by) VALUES (?, ?, ?, ?, ?)",
                email, passwords.encode(password), displayName, newReferralCode(), resolveReferrer(referralCode));
        return session(db.one("SELECT * FROM users WHERE id = ?", id));
    }

    public Map<String, Object> login(String email, String password) {
        email = email == null ? "" : email.trim();
        if (email.isEmpty() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Email and password required");
        }
        Map<String, Object> user = db.one("SELECT * FROM users WHERE email = ?", email);
        String hash = Rows.str(user, "password");
        // Same message for "no such user" and "wrong password" so emails can't be probed.
        if (user == null || hash == null || hash.isBlank() || !passwords.matches(password, hash)) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return session(user);
    }

    @Transactional
    public Map<String, Object> google(String accessToken, String idToken, String referralCode) {
        GoogleVerifier.GoogleUser g = google.verify(accessToken, idToken);

        Map<String, Object> user = db.one("SELECT * FROM users WHERE google_id = ?", g.sub());
        if (user == null) {
            Map<String, Object> byEmail = db.one("SELECT * FROM users WHERE email = ?", g.email());
            if (byEmail != null) {
                // Safe to link: Google has confirmed this person owns the email.
                db.update("UPDATE users SET google_id = ? WHERE id = ?", g.sub(), byEmail.get("id"));
                user = db.one("SELECT * FROM users WHERE id = ?", byEmail.get("id"));
            } else {
                long id = db.insert(
                        "INSERT INTO users (email, google_id, name, referral_code, referred_by) VALUES (?, ?, ?, ?, ?)",
                        g.email(), g.sub(), g.name(), newReferralCode(), resolveReferrer(referralCode));
                user = db.one("SELECT * FROM users WHERE id = ?", id);
            }
        }
        return session(user);
    }

    public Map<String, Object> me(long userId) {
        Map<String, Object> user = db.one("SELECT * FROM users WHERE id = ?", userId);
        if (user == null) throw ApiException.unauthorized("Please log in again");
        return publicUser(user, null);
    }

    /**
     * Response shape covers both formats the React app reads:
     * {@code data.user}, {@code data.token} and {@code data.session.access_token}.
     * The token is also put INSIDE user, because the pages save only
     * {@code data.user} to localStorage — that way they need no changes.
     */
    private Map<String, Object> session(Map<String, Object> userRow) {
        long id = Rows.lng(userRow, "id");
        String token = jwt.issue(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", publicUser(userRow, token));
        out.put("token", token);
        out.put("session", Map.of("access_token", token));
        return out;
    }

    private Map<String, Object> publicUser(Map<String, Object> row, String token) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", Rows.lng(row, "id"));
        u.put("email", Rows.str(row, "email"));
        u.put("name", Rows.str(row, "name"));
        u.put("is_admin", Rows.bool(row, "is_admin") ? 1 : 0);
        u.put("referral_code", Rows.str(row, "referral_code"));
        if (token != null) u.put("token", token);
        return u;
    }

    /** Referral code (e.g. "A1B2C3D4") or, like before, a numeric user id. */
    private Long resolveReferrer(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim();
        Long id = db.scalar("SELECT id FROM users WHERE referral_code = ?", Long.class, c.toUpperCase());
        if (id != null) return id;
        if (c.matches("\\d+")) {
            return db.scalar("SELECT id FROM users WHERE id = ?", Long.class, Long.parseLong(c));
        }
        return null;
    }

    private String newReferralCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            String code = sb.toString();
            if (db.one("SELECT id FROM users WHERE referral_code = ?", code) == null) return code;
        }
        throw new IllegalStateException("Could not generate a unique referral code");
    }
}
