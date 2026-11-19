package com.mrdiet.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.security.AuthUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Was profiles.php — one health profile per user. */
@Service
public class ProfileService {

    private final Db db;
    private final ObjectMapper mapper;
    private final PasswordEncoder passwords;

    public ProfileService(Db db, ObjectMapper mapper, PasswordEncoder passwords) {
        this.db = db;
        this.mapper = mapper;
        this.passwords = passwords;
    }

    /** Same shape as profiles.php GET. */
    public Map<String, Object> get(AuthUser me) {
        Map<String, Object> row = db.one("""
                SELECT p.*, u.name AS u_name, u.email AS u_email
                FROM users u LEFT JOIN profiles p ON u.id = p.user_id
                WHERE u.id = ?""", me.id());
        if (row == null) throw ApiException.notFound("Account not found");

        ObjectNode ext = parseObject(Rows.str(row, "extended_data"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", Rows.lng(row, "id"));
        res.put("user_id", me.id());
        res.put("name", firstNonBlank(Rows.str(row, "name"), Rows.str(row, "u_name"), "User"));
        res.put("email", firstNonBlank(Rows.str(row, "email"), Rows.str(row, "u_email"), ""));
        res.put("phone", firstNonBlank(ext.path("phone").asText(""), ext.path("formData").path("phone").asText(""), ""));
        res.put("city", firstNonBlank(ext.path("city").asText(""), ext.path("formData").path("city").asText(""), ""));
        res.put("weight", Rows.dbl(row, "weight", 0));
        res.put("height", Rows.dbl(row, "height", 0));
        res.put("age", Rows.integer(row, "age", 0));
        res.put("gender", Rows.str(row, "gender", ""));
        res.put("bmi", Rows.dbl(row, "bmi", 0));
        res.put("goal", Rows.str(row, "goal", ""));
        res.put("activity_level", Rows.str(row, "activity_level", ""));
        res.put("dietary_restrictions", parseAny(Rows.str(row, "dietary_restrictions")));
        res.put("allergies", Rows.str(row, "allergies", ""));
        res.put("meal_preference", Rows.str(row, "meal_preference", ""));
        res.put("cycle_length", Rows.lng(row, "cycle_length"));
        res.put("period_duration", Rows.lng(row, "period_duration"));
        res.put("last_period_start", row.get("last_period_start"));
        res.put("extended_data", ext);
        return res;
    }

    /** Create-or-update, merging extended_data instead of overwriting it (as profiles.php POST did). */
    @Transactional
    public Map<String, Object> save(AuthUser me, JsonNode d) {
        Map<String, Object> ex = db.one("SELECT * FROM profiles WHERE user_id = ?", me.id());
        if (ex == null) ex = Map.of();

        String name = J.text(d, "name", Rows.str(ex, "name", me.name() == null ? "" : me.name()));
        String email = J.text(d, "email", Rows.str(ex, "email", me.email() == null ? "" : me.email()));
        int age = J.has(d, "age") ? J.integer(d, "age", 0) : Rows.integer(ex, "age", 0);
        String gender = J.has(d, "gender") ? J.str(d, "gender") : Rows.str(ex, "gender", "");
        double height = J.has(d, "height") ? J.dbl(d, "height", 0) : Rows.dbl(ex, "height", 0);
        double weight = J.has(d, "weight") ? J.dbl(d, "weight", 0) : Rows.dbl(ex, "weight", 0);
        String goal = J.has(d, "goal") ? J.str(d, "goal") : Rows.str(ex, "goal", "");
        String activity = J.has(d, "activity_level") ? J.str(d, "activity_level") : Rows.str(ex, "activity_level", "");
        Double bmi = J.has(d, "bmi") ? J.dbl(d, "bmi") : (ex.get("bmi") == null ? null : Rows.dbl(ex, "bmi", 0));
        String restrictions = J.has(d, "dietary_restrictions") ? d.get("dietary_restrictions").toString()
                : Rows.str(ex, "dietary_restrictions", "[]");
        String allergies = J.has(d, "allergies") ? J.str(d, "allergies") : Rows.str(ex, "allergies", "");
        String mealPref = J.has(d, "meal_preference") ? J.str(d, "meal_preference") : Rows.str(ex, "meal_preference", "");

        ObjectNode ext = parseObject(Rows.str(ex, "extended_data"));
        mergeInto(ext, d.get("extended_data"));
        mergeInto(ext, d.get("formData"));
        if (J.has(d, "phone")) ext.put("phone", J.str(d, "phone"));
        if (J.has(d, "city")) ext.put("city", J.str(d, "city"));

        if (ex.isEmpty()) {
            db.insert("""
                    INSERT INTO profiles (user_id, name, email, age, gender, height, weight, goal, activity_level,
                                          dietary_restrictions, allergies, meal_preference, extended_data, bmi)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    me.id(), name, email, age, nz(gender), height, weight, nz(goal), nz(activity), restrictions,
                    allergies, mealPref, ext.toString(), bmi);
        } else {
            db.update("""
                    UPDATE profiles SET name = ?, email = ?, age = ?, gender = ?, height = ?, weight = ?, goal = ?,
                           activity_level = ?, dietary_restrictions = ?, allergies = ?, meal_preference = ?,
                           extended_data = ?, bmi = ?
                    WHERE user_id = ?""",
                    name, email, age, nz(gender), height, weight, nz(goal), nz(activity), restrictions, allergies,
                    mealPref, ext.toString(), bmi, me.id());
        }

        // Account fields (users table)
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (J.text(d, "name", null) != null) { sets.add("name = ?"); args.add(J.text(d, "name", null)); }
        if (J.text(d, "email", null) != null) { sets.add("email = ?"); args.add(J.text(d, "email", null)); }
        String newPassword = J.str(d, "password");
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 6) throw ApiException.badRequest("Password must be at least 6 characters");
            sets.add("password = ?");
            args.add(passwords.encode(newPassword));
        }
        if (!sets.isEmpty()) {
            args.add(me.id());
            try {
                db.update("UPDATE users SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());
            } catch (DuplicateKeyException e) {
                throw ApiException.conflict("That email is already used by another account");
            }
        }

        Map<String, Object> saved = new LinkedHashMap<>(db.one("SELECT * FROM profiles WHERE user_id = ?", me.id()));
        ObjectNode savedExt = parseObject(Rows.str(saved, "extended_data"));
        saved.put("dietary_restrictions", parseAny(Rows.str(saved, "dietary_restrictions")));
        saved.put("extended_data", savedExt);
        saved.put("phone", savedExt.path("phone").asText(""));
        saved.put("city", savedExt.path("city").asText(""));
        saved.put("message", "Profile updated securely without data loss.");
        return saved;
    }

    // --- JSON helpers -------------------------------------------------------

    private ObjectNode parseObject(String json) {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        try {
            JsonNode n = mapper.readTree(json);
            return n != null && n.isObject() ? (ObjectNode) n : mapper.createObjectNode();
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    JsonNode parseAny(String json) {
        if (json == null || json.isBlank()) return mapper.createArrayNode();
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.getNodeFactory().textNode(json);
        }
    }

    /** PHP array_merge() for an object or a JSON string. */
    private void mergeInto(ObjectNode target, JsonNode source) {
        if (source == null || source.isNull()) return;
        JsonNode obj = source;
        if (source.isTextual()) {
            try {
                obj = mapper.readTree(source.asText());
            } catch (Exception e) {
                return;
            }
        }
        if (obj != null && obj.isObject()) target.setAll((ObjectNode) obj);
    }

    private static String firstNonBlank(String a, String b, String def) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return def;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
