package com.mrdiet.diet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.Db;
import com.mrdiet.common.J;
import com.mrdiet.common.Rows;
import com.mrdiet.core.Cycle;
import com.mrdiet.core.MealPrompt;
import com.mrdiet.core.Nutrition;
import com.mrdiet.core.Plans;
import com.mrdiet.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Was generate_plan.php. Now requires a login, an active plan you own (or your
 * unused free day), and is rate-limited — the PHP endpoint was open to anyone.
 */
@Service
public class MealGenerationService {

    private static final int MAX_MEAL_CHANGES_PER_DAY = 5;

    private final Db db;
    private final DietPlanService plans;
    private final OpenAiClient openAi;
    private final RateLimiter limiter;
    private final ObjectMapper mapper;

    public MealGenerationService(Db db, DietPlanService plans, OpenAiClient openAi, RateLimiter limiter,
                                 ObjectMapper mapper) {
        this.db = db;
        this.plans = plans;
        this.openAi = openAi;
        this.limiter = limiter;
        this.mapper = mapper;
    }

    public Map<String, Object> generate(AuthUser me, JsonNode in) {
        JsonNode body = in.has("body") && in.get("body").isObject() ? in.get("body") : in;
        if ("substitute_meal".equals(J.str(body, "action"))) return substitute(me, body);

        int day = J.integer(body, "dayNumber", J.integer(body, "day_number", 1));
        long subId = J.lng(body, "subscription_id", 0);
        requireAccess(me, subId);
        limiter.check(me.id());

        JsonNode up = body.path("userProfile");
        JsonNode ext = asObject(up.get("extended_data"));
        String gender = J.str(up, "gender", "male").toLowerCase(Locale.ROOT);
        double weight = J.dbl(up, "weight", 70);
        double heightCm = Nutrition.normalizeHeightCm(J.dbl(up, "height", 170));
        String goal = J.str(up, "goal", "weight-loss");
        String activity = J.str(up, "activity_level", "moderate");
        long kcal = Nutrition.targetCalories(weight, J.dbl(up, "height", 170), J.dbl(up, "age", 30), gender, activity, goal);

        Cycle.Phase phase = null;
        if (gender.equals("female") || gender.equals("women")) {
            Map<String, Object> p = db.one(
                    "SELECT cycle_length, period_duration, last_period_start FROM profiles WHERE user_id = ?", me.id());
            LocalDate last = Rows.date(p, "last_period_start");
            if (last != null) {
                phase = Cycle.planPhase(last, LocalDate.now(), Rows.integer(p, "cycle_length", 28),
                        Rows.integer(p, "period_duration", 5));
            }
        }

        String location = firstText(ext.path("location"), up.path("city"), up.path("state"));
        Double expected = J.dbl(ext, "expectedWeight");
        MealPrompt.Client client = new MealPrompt.Client(
                J.str(up, "name", "the client"), J.str(up, "age", "30"), J.str(up, "gender", "male"),
                J.str(up, "weight", "70"), heightCm, goal, activity, kcal,
                J.text(body, "language", "English"), J.str(body, "notes", ""), joined(up.get("allergies")),
                list(ext.get("dietType")), list(up.get("dietary_restrictions")),
                joined(ext.get("foodsDislike")), joined(ext.get("specialInstructions")), expected, weight,
                firstText(ext.path("medicalConditions"), ext.path("medicalHistory")),
                joined(ext.get("bloodGroup")), joined(ext.get("sleepHours")), joined(ext.get("stressLevel")),
                location, phase);

        String prompt = MealPrompt.userPrompt(client, day, pastMeals(subId, day), LocalDate.now());
        JsonNode plan = openAi.json(MealPrompt.SYSTEM_PROMPT, prompt, 0.85);
        return Map.of("mealPlan", plan);
    }

    public Map<String, Object> substitute(AuthUser me, JsonNode body) {
        long subId = J.lng(body, "subscription_id", 0);
        int day = J.integer(body, "day_number", 1);
        String userHas = J.text(body, "user_has", "");
        if (userHas.isEmpty()) throw ApiException.badRequest("user_has is required for meal substitution");

        int used = 0;
        if (subId > 0) {
            plans.ownedSubscription(me, subId);
            used = db.list("SELECT meal_changes_count FROM daily_diet_plans WHERE subscription_id = ? AND day_number = ?", subId, day)
                    .stream().findFirst().map(r -> Rows.integer(r, "meal_changes_count", 0)).orElse(0);
            if (used >= MAX_MEAL_CHANGES_PER_DAY) {
                throw ApiException.tooMany("You've used all " + MAX_MEAL_CHANGES_PER_DAY + " meal swaps for today");
            }
        } else {
            requireAccess(me, 0);
        }
        limiter.check(me.id());

        JsonNode m = body.path("target_macros");
        JsonNode meal = openAi.json(MealPrompt.SUBSTITUTION_SYSTEM_PROMPT,
                MealPrompt.substitutionPrompt(userHas, J.dbl(m, "calories", 500), J.dbl(m, "protein", 25),
                        J.dbl(m, "carbs", 60), J.dbl(m, "fat", 15)), 0.3);

        int remaining = MAX_MEAL_CHANGES_PER_DAY;
        if (subId > 0) {
            db.update("UPDATE daily_diet_plans SET meal_changes_count = ? WHERE subscription_id = ? AND day_number = ?",
                    used + 1, subId, day);
            remaining = Math.max(0, MAX_MEAL_CHANGES_PER_DAY - (used + 1));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("meal", meal);
        out.put("changes_remaining", remaining);
        return out;
    }

    /** Paid & owned plan, or — for subscription 0 — the user's still-unused free day. */
    private void requireAccess(AuthUser me, long subId) {
        if (subId > 0) {
            Map<String, Object> sub = plans.ownedSubscription(me, subId);
            String status = Rows.str(sub, "payment_status", "").toLowerCase(Locale.ROOT);
            if (!Plans.PAID_STATES.contains(status)) throw ApiException.paymentRequired("This plan is not active yet");
            LocalDate end = Rows.date(sub, "end_date");
            if (end != null && end.isBefore(LocalDate.now())) throw ApiException.paymentRequired("This plan has expired");
            return;
        }
        Long trials = db.scalar("SELECT COUNT(*) FROM subscriptions WHERE user_id = ? AND LOWER(plan_type) = 'trial'",
                Long.class, me.id());
        if (trials != null && trials > 0) throw ApiException.paymentRequired("Please choose a plan to continue");
    }

    /** Dish names from the last 7 days of this plan, so the AI doesn't repeat them. */
    private List<String> pastMeals(long subId, int day) {
        if (subId <= 0) return List.of();
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> row : db.list("""
                SELECT day_number, meal_json FROM daily_diet_plans
                WHERE subscription_id = ? AND day_number < ? AND meal_json IS NOT NULL
                ORDER BY day_number DESC LIMIT 7""", subId, day)) {
            try {
                JsonNode plan = mapper.readTree(Rows.str(row, "meal_json"));
                for (JsonNode slot : plan.path("timeline")) {
                    addName(names, slot.path("option_1").path("name"));
                    addName(names, slot.path("option_2").path("name"));
                    if (slot.path("option_1").isMissingNode()) addName(names, slot.path("name"));
                }
            } catch (Exception ignored) {
                // a broken old plan shouldn't block today's
            }
        }
        return new ArrayList<>(names);
    }

    private static void addName(Set<String> names, JsonNode n) {
        if (n != null && n.isTextual() && !n.asText().isBlank()) names.add(n.asText());
    }

    private JsonNode asObject(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return mapper.createObjectNode();
        if (n.isTextual()) {
            try {
                JsonNode parsed = mapper.readTree(n.asText());
                return parsed != null && parsed.isObject() ? parsed : mapper.createObjectNode();
            } catch (Exception e) {
                return mapper.createObjectNode();
            }
        }
        return n.isObject() ? n : mapper.createObjectNode();
    }

    /** Array → list of strings; "a" → ["a"]; '["a","b"]' → ["a","b"]. */
    private List<String> list(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n == null || n.isNull() || n.isMissingNode()) return out;
        JsonNode node = n;
        if (n.isTextual() && n.asText().trim().startsWith("[")) {
            try {
                node = mapper.readTree(n.asText());
            } catch (Exception ignored) {
                // fall through and treat as plain text
            }
        }
        if (node.isArray()) {
            node.forEach(v -> {
                if (!v.isNull() && !v.asText().isBlank()) out.add(v.isValueNode() ? v.asText() : v.toString());
            });
        } else if (!node.asText().isBlank()) {
            out.add(node.asText());
        }
        return out;
    }

    private String joined(JsonNode n) {
        return String.join(", ", list(n));
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            String s = joined(n);
            if (!s.isBlank()) return s;
        }
        return "";
    }
}
