package com.mrdiet.core;

import java.util.Locale;
import java.util.Map;

/** Daily calorie target — Mifflin-St Jeor, ported 1:1 from generate_plan.php. */
public final class Nutrition {

    private Nutrition() {}

    private static final Map<String, Double> ACTIVITY = Map.of(
            "sedentary", 1.2,
            "light", 1.375,
            "moderate", 1.55,
            "active", 1.725);

    /**
     * @param heightRaw cm, or metres (&lt; 3) or feet (&lt; 10) — normalised like PHP did
     */
    public static long targetCalories(double weightKg, double heightRaw, double age, String gender,
                                      String activity, String goal) {
        double height = normalizeHeightCm(heightRaw);
        String g = gender == null ? "male" : gender.toLowerCase(Locale.ROOT);
        boolean female = g.equals("female") || g.equals("women");
        double bmr = female
                ? (10 * weightKg) + (6.25 * height) - (5 * age) - 161
                : (10 * weightKg) + (6.25 * height) - (5 * age) + 5;
        double tdee = bmr * ACTIVITY.getOrDefault(activity == null ? "moderate" : activity, 1.55);
        double target = "weight-loss".equals(goal) ? tdee - 500
                : "muscle-gain".equals(goal) ? tdee + 300
                : tdee;
        return Math.round(target);
    }

    public static double normalizeHeightCm(double h) {
        if (h < 3) return h * 100;      // metres
        if (h < 10) return h * 30.48;   // feet
        return h;                       // already cm
    }
}
