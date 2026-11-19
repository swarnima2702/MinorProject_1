package com.mrdiet.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Builds the OpenAI prompts. Ported line-for-line from services/openai_service.php
 * so the generated plans look exactly like before. Pure Java — easy to unit test.
 */
public final class MealPrompt {

    private MealPrompt() {}

    /** Everything the prompt needs about the client (already pulled out of the request JSON). */
    public record Client(
            String name, String age, String gender, String weight, double heightCm,
            String goal, String activityLevel, long targetCalories,
            String language, String notes, String allergies,
            List<String> dietTypes, List<String> dietaryRestrictions,
            String foodsDislike, String specialInstructions, Double expectedWeight, double weightKg,
            String medicalConditions, String bloodGroup, String sleepHours, String stressLevel,
            String location, Cycle.Phase periodPhase) {}

    public static final String SYSTEM_PROMPT = """
            You are an ultra-precise Indian Clinical Nutritionist & Meal Plan JSON API.

            ABSOLUTE OUTPUT RULES (violation = failure):
            1. Return ONLY a single valid JSON object. No markdown, no code fences, no explanation text outside JSON.
            2. ALL macro values (calories, protein, carbs, fat, fiber) MUST be RAW NUMBERS — no units, no strings.
            3. 'benefits' field MUST ALWAYS be an ARRAY of strings (never a single string).
            4. 'preparation_steps' MUST ALWAYS be an ARRAY of strings.
            5. 'ingredients' MUST ALWAYS be an ARRAY of strings where each item includes a precise quantity (e.g., "150g paneer", "2 medium roti", "1 cup cooked dal").
            6. 'water_intake' MUST be a detailed object with keys: total_liters, schedule (array), tips (array).
            7. NO emojis anywhere in the JSON.
            8. Verify calorie math BEFORE outputting. All meal calories summed MUST equal the target within ±30 kcal.""";

    public static final String SUBSTITUTION_SYSTEM_PROMPT =
            "You are a precise nutrition JSON API. Return ONLY raw JSON. No markdown, no code blocks. "
                    + "All macro values must be raw numbers (no units).";

    private static final String[] CUISINES = {
            "North Indian (Punjabi/UP/Delhi — dal makhani, rajma, chole, paneer dishes, roti, paratha, kadhi-chawal)",
            "South Indian (Tamil/Kerala/Karnataka/Andhra — idli, dosa, uttapam, sambar, rasam, curd rice, appam, pongal, upma)",
            "Bengali (machher jhol if non-veg, shukto, luchi-aloor dom, posto, mishti doi, cholar dal)",
            "Gujarati (dhokla, thepla, undhiyu, khichdi-kadhi, fafda, handvo, khaman, methi muthia)",
            "Maharashtrian (poha, thalipeeth, misal pav, varan-bhaat, sabudana khichdi, puran poli, zunka bhakri)",
            "Rajasthani/Marwari (dal-baati-churma, gatte ki sabzi, ker sangri, bajra roti, pyaaz kachori, bessan gatta)",
            "Mixed Pan-Indian Fusion (lighter, modern combinations — quinoa pulao, paneer wraps, ragi cheela, oats khichdi)",
    };

    private static final Map<String, String> PHASE_GUIDANCE = Map.of(
            "period", "High iron foods (palak, beetroot, pomegranate, rajma, horse gram), warm soups and stews, anti-cramp foods (ginger tea, dark chocolate, banana), avoid cold/raw foods and carbonated drinks. Increase magnesium (banana, dark chocolate, seeds).",
            "follicular", "High protein for cell repair (paneer, eggs if non-veg, sprouted moong, soya), energy-boosting complex carbs (oats, quinoa if available else brown rice), fermented foods for gut health (idli, dosa, yoghurt, kanji), fresh colorful salads.",
            "ovulation", "Anti-inflammatory foods (haldi milk, ginger, flaxseeds, walnuts), fiber-rich vegetables (broccoli cooked, carrots, beetroot), balanced macros, antioxidant-rich fruits (pomegranate, amla, berries), stay well hydrated.",
            "luteal", "Complex carbs to combat cravings (brown rice, oats, sweet potato), magnesium-rich foods (dark chocolate in moderation, kaju, akhrot, til), reduce sodium (avoid pickles, papad, extra salt), healthy sweets (dates, anjeer, khajur), anti-PMS herbs (shatavari, ashwagandha if available).");

    public static String season(int month) {
        if (month >= 3 && month <= 5) return "Summer (Garmi ka Mausam)";
        if (month >= 6 && month <= 9) return "Monsoon (Barish ka Mausam)";
        if (month >= 10 && month <= 11) return "Autumn/Post-Monsoon";
        return "Winter (Sardi ka Mausam)";
    }

    public static String cuisineFor(int dayNumber) {
        return CUISINES[Math.floorMod(dayNumber - 1, CUISINES.length)];
    }

    public static String userPrompt(Client c, int dayNumber, List<String> pastMeals, LocalDate today) {
        int month = today.getMonthValue();
        String season = season(month);
        String lang = c.language();
        String goal = c.goal();
        StringBuilder p = new StringBuilder(9000);

        p.append("Generate an evidence-based, highly personalized Daily Diet Plan for Day ").append(dayNumber)
                .append(" for the following client.\n");
        p.append("MANDATORY LANGUAGE: All textual fields (day_title, day_summary, ingredient names, preparation_steps, benefits, daily_dos, daily_donts) MUST be written in '")
                .append(lang).append("'.\n\n");

        // --- FULL CLIENT PROFILE ---
        String weightGoal = "";
        if (c.expectedWeight() != null && c.expectedWeight() != 0 && c.expectedWeight() != c.weightKg()) {
            double diff = Math.abs(c.weightKg() - c.expectedWeight());
            String dir = c.weightKg() > c.expectedWeight() ? "lose " + num(diff) + " kg" : "gain " + num(diff) + " kg";
            weightGoal = "Target: " + dir + " to reach " + num(c.expectedWeight()) + "kg goal weight.";
        }
        p.append("=== COMPLETE CLIENT PROFILE ===\n");
        p.append("Name: ").append(c.name()).append(" | Age: ").append(c.age()).append(" | Gender: ").append(c.gender())
                .append(" | Weight: ").append(c.weight()).append("kg | Height: ").append(num(c.heightCm())).append("cm\n");
        p.append("Primary Goal: ").append(goal).append(weightGoal.isEmpty() ? "" : " | " + weightGoal).append("\n");
        p.append("Activity Level: ").append(c.activityLevel()).append(" | Target Calories: ").append(c.targetCalories())
                .append(" kcal/day\n");
        if (notBlank(c.bloodGroup())) p.append("Blood Group: ").append(c.bloodGroup()).append("\n");
        if (notBlank(c.sleepHours())) p.append("Average Sleep: ").append(c.sleepHours()).append(" hours/night\n");
        if (notBlank(c.stressLevel())) p.append("Stress Level: ").append(c.stressLevel()).append("\n");

        // --- DIETARY PROFILE ---
        p.append("\n=== DIETARY PROFILE ===\n");
        if (c.dietTypes() != null && !c.dietTypes().isEmpty()) {
            p.append("Diet Type: ").append(String.join(", ", c.dietTypes())).append("\n");
        }
        if (c.dietaryRestrictions() != null && !c.dietaryRestrictions().isEmpty()) {
            p.append("Dietary Restrictions: ").append(String.join(", ", c.dietaryRestrictions()))
                    .append(" — STRICTLY RESPECT THESE. Never suggest any food violating these.\n");
        }
        if (notBlank(c.allergies())) {
            p.append("FOOD ALLERGIES (LIFE-CRITICAL): ").append(c.allergies()).append(" — NEVER include these. Zero tolerance.\n");
        }
        if (notBlank(c.foodsDislike())) {
            p.append("Foods Client Dislikes/Avoids: ").append(c.foodsDislike()).append(" — Do NOT include these in the plan.\n");
        }
        if (notBlank(c.medicalConditions())) {
            p.append("Medical Conditions/History: ").append(c.medicalConditions())
                    .append(" — All food choices MUST be safe and supportive for these conditions.\n");
        }
        if (notBlank(c.specialInstructions())) {
            p.append("Special Instructions from Client: ").append(c.specialInstructions()).append("\n");
        }

        // --- LOCATION & SEASON ---
        p.append("\n=== LOCATION & SEASONAL CONTEXT ===\n");
        p.append("Country: India | Current Season: ").append(season).append("\n");
        if (notBlank(c.location())) {
            p.append("Client Location: ").append(c.location()).append("\n");
            p.append("MANDATORY: Suggest locally available, affordable foods common in ").append(c.location())
                    .append(" and across India.\n");
            p.append("Consider regional food culture — e.g., if South India prefer idli/dosa/sambar; if Punjab prefer dal makhani/makki roti; if Gujarat prefer dhokla/khichdi; if Bengali prefer fish/lentils (unless restricted).\n");
        } else {
            p.append("Suggest foods easily available across ALL Indian cities and towns (dal, sabzi, roti, chawal, paneer, dahi, etc.).\n");
        }
        p.append("SEASONAL GUIDANCE for ").append(season).append(":\n");
        if (month >= 3 && month <= 5) {
            p.append("- Summer: Prioritize cooling foods (cucumber, sattu, pudina, coconut water, lassi, tarbuj, kharbuja, curd-based dishes). Avoid very heavy/oily foods.\n");
            p.append("- Increase water-rich vegetables and hydrating foods. Avoid excessive salt. Add nimbu pani where appropriate.\n");
        } else if (month >= 6 && month <= 9) {
            p.append("- Monsoon: Prioritize immunity-boosting foods (haldi, adrak, tulsi, lahsun). Avoid raw/street salads, prefer cooked vegetables.\n");
            p.append("- Prefer light, easily digestible meals. Include ginger tea, turmeric milk. Avoid leafy greens prone to contamination (prefer cooked or washed thoroughly).\n");
        } else {
            p.append("- Winter: Prioritize warming foods (til, gur, bajra, makki, ghee in moderation, methi, sarson, gond ke laddoo for energy). Include immune boosters.\n");
            p.append("- Root vegetables (gajar, shalgam, chukander) are in season — use them. Include warm soups and kadhas.\n");
        }

        // --- DAILY CHECK-IN NOTES ---
        p.append("\n=== TODAY'S CHECK-IN NOTES (CRITICAL PRIORITY) ===\n");
        if (notBlank(c.notes())) {
            p.append("PRIORITY NOTE/SYMPTOMS FROM CLIENT: \"").append(c.notes()).append("\"\n");
            p.append("INSTRUCTION: This is the most important field. You MUST adapt today's meals to treat these symptoms. \n");
            p.append("- If 'Stress' — Include calming herbs/foods (Tulsi, Ashwagandha, Chamomile-type tea, magnesium foods).\n");
            p.append("- If 'Bloating' / 'Gas' — Exclude cabbage, cauliflower, beans, and high-fiber raw salads. Include Ajwain, ginger, hing, or cumin-infused foods.\n");
            p.append("- If 'Energy Low' / 'Fatigue' — Include iron-rich foods, complex carbs, and natural stimulants like dates/dry fruits.\n");
            p.append("- If 'Constipation' — Increase water-rich foods and cooked fiber (Isabgol, papaya).\n");
            p.append("Directly mention in the 'day_summary' how you addressed the user's symptoms today.\n");
        } else {
            p.append("No specific symptoms today. Follow the goal-based plan strictly.\n");
        }

        // --- STRICT NUTRITIONAL RULES ---
        long kcal = c.targetCalories();
        p.append("\n=== STRICT NUTRITIONAL RULES (MANDATORY) ===\n");
        p.append("R1. CALORIE PRECISION: Total calories summed across ALL meals MUST equal exactly ").append(kcal)
                .append(" kcal (±30 kcal tolerance). Recalculate and adjust before outputting.\n");
        p.append("R2. MACRO BALANCE for '").append(goal).append("':\n");
        switch (goal == null ? "" : goal) {
            case "weight-loss", "fat-loss" -> {
                p.append("    - High protein (25-30% of calories) to preserve muscle. Moderate carbs (40-45%). Low fat (25-30%). High fiber (30g+).\n");
                p.append("    - Preference: lean proteins (moong dal, chicken breast if non-veg, paneer in moderation), complex carbs (oats, brown rice, jowar roti), lots of fiber-rich sabzis.\n");
            }
            case "muscle-gain", "weight-gain" -> {
                p.append("    - High protein (30-35% of calories). Moderate-high carbs (45-50%). Moderate fat (20-25%).\n");
                p.append("    - Preference: calorie-dense foods (peanut butter, milk, eggs if non-veg, dal rice, rajma, paneer), frequent meals, post-workout recovery meal.\n");
            }
            case "diabetes", "diabetes-management" -> {
                p.append("    - Low glycemic index (GI) foods ONLY. No white rice, maida, sugar, refined carbs.\n");
                p.append("    - Use: brown rice / bajra / jowar roti, bitter gourd (karela), methi, cinnamon (dalchini), fenugreek seeds. Prioritize fiber (35g+). Split into 5-6 small meals.\n");
            }
            case "thyroid", "hypothyroid" -> {
                p.append("    - Avoid raw cruciferous vegetables (gobi, broccoli) — cook them. Avoid soy products. Include selenium-rich foods (sunflower seeds, Brazil nuts if available).\n");
                p.append("    - Include iodine-rich foods (iodized salt, dairy), selenium (sunflower seeds, mushrooms), zinc (pumpkin seeds, legumes).\n");
            }
            case "pcod", "pcos" -> {
                p.append("    - Anti-inflammatory diet. Low glycemic index. Include omega-3 (flaxseeds, walnuts, fish if non-veg). Avoid sugar, processed foods, refined carbs.\n");
                p.append("    - Include: spearmint tea, cinnamon, turmeric, high-fiber vegetables, zinc-rich foods (pumpkin seeds).\n");
            }
            default -> p.append("    - Balanced macros: 20-25% protein, 50-55% carbs, 25-30% fat. Include diverse Indian foods.\n");
        }
        p.append("R3. INGREDIENT QUANTITIES: EVERY ingredient MUST have a precise quantity (e.g., '150g paneer', '2 medium roti (60g each)', '1 katori (150ml) cooked dal').\n");
        p.append("R4. PREPARATION STEPS (DETAILED): Provide 4-5 clear, sequentially numbered preparation steps for every dish. Include specific Indian cooking techniques, flame settings (low/medium/high), and timing where relevant. For salads/drinks, mention temperature and serving style.\n");
        p.append("R5. GOAL IMPACT (DETAILED BENEFITS): Provide 4-5 specific benefits per dish. Explain EXACTLY how the nutrients in this dish (e.g., fiber, specific vitamins, protein type) impact the user's body to achieve '")
                .append(goal).append("'. Use scientific yet understandable reasoning (e.g., 'high fiber slows glucose absorption' for diabetes).\n");
        p.append("R6. DUAL OPTIONS: Provide BOTH option_1 AND option_2 for Breakfast, Lunch, and Dinner. Options should be meaningfully different (not just portion changes).\n");
        p.append("R7. FIBER: Every meal option MUST include a 'fiber' field (grams, numeric).\n");
        p.append("R8. WATER INTAKE: Provide a DETAILED water intake plan as a JSON object (not just a string) with: total_liters, schedule[], tips[].\n");
        p.append("R9. COMMON INDIAN MARKET FOODS ONLY: Use ingredients easily purchased from any kirana store, sabzi mandi, or local bazaar. No exotic/imported western ingredients.\n");
        p.append("R10. MEAL TIMING: Use realistic Indian meal timing (6:30-7:00 AM wake-up, 8:00-8:30 AM breakfast, 11:00 AM mid-morning, 1:00-1:30 PM lunch, 4:30-5:00 PM evening snack, 7:30-8:00 PM dinner).\n");

        // --- DAILY CUISINE FOCUS ---
        p.append("\n=== DAILY CUISINE FOCUS (DAY ").append(dayNumber).append(") ===\n");
        p.append("Today's primary regional cuisine: ").append(cuisineFor(dayNumber)).append("\n");
        p.append("Lean breakfast, lunch and dinner main dishes towards this regional style while strictly respecting the client's dietary restrictions, allergies, medical conditions, and goal. Mid-morning and evening snacks can stay pan-Indian. This rotation EXISTS to guarantee variety — do not default to generic dishes like poha/paneer bhurji/dal-chawal every day.\n");

        // --- ANTI-REPETITION (R11) ---
        if (pastMeals != null && !pastMeals.isEmpty()) {
            List<String> avoid = pastMeals.subList(0, Math.min(30, pastMeals.size()));
            p.append("\nR11. ANTI-REPETITION (CRITICAL FOR VARIETY): The client has ALREADY been served these dishes in the last 7 days. You MUST NOT repeat ANY of them today, in either option_1 or option_2 of any meal slot:\n");
            p.append("AVOID THESE DISHES: ").append(String.join(", ", avoid)).append("\n");
            p.append("Pick completely different dishes — different protein source (e.g., if past meals had paneer, today try moong dal/chickpea/sprouts), different grain (if past had roti/rice, try millet/bajra/jowar/oats), and different cooking style (curry vs dry vs stuffed vs steamed). If similar nutritional profile is required, use a different regional variant or substitute the main ingredient.\n");
        }

        // --- JSON STRUCTURE ---
        p.append("\n=== REQUIRED JSON OUTPUT STRUCTURE ===\n");
        p.append("Return this exact structure. Populate ALL fields with real, detailed, goal-specific content:\n");
        p.append(JSON_TEMPLATE
                .replace("{{DAY}}", String.valueOf(dayNumber))
                .replace("{{LANG}}", lang)
                .replace("{{GOAL}}", goal == null ? "" : goal)
                .replace("{{KCAL}}", String.valueOf(kcal))
                .replace("{{ACTIVITY}}", c.activityLevel() == null ? "" : c.activityLevel())
                .replace("{{SEASON}}", season));

        // --- PERIOD PHASE (women only) ---
        Cycle.Phase ph = c.periodPhase();
        if (ph != null && notBlank(ph.phase())) {
            String phaseName = Character.toUpperCase(ph.phase().charAt(0)) + ph.phase().substring(1);
            String guidance = PHASE_GUIDANCE.getOrDefault(ph.phase().toLowerCase(), "Balanced wholesome nutrition.");
            p.append("\n\n=== MENSTRUAL CYCLE CONTEXT (CRITICAL — ADJUST ENTIRE PLAN) ===\n");
            p.append("Current Phase: ").append(phaseName).append(" Phase | Cycle Day: ").append(ph.cycleDay()).append("/")
                    .append(ph.cycleLength()).append(" | Days Until Next Period: ").append(ph.daysUntilNext()).append("\n");
            p.append("MANDATORY PHASE ADJUSTMENTS: ").append(guidance).append("\n");
            p.append("ACTION: Integrate these phase-specific foods naturally throughout the timeline. Mention the phase and its dietary impact in the day_summary. Add phase-specific daily_dos and daily_donts.\n");
        }

        p.append("\n\n=== FINAL CHECKLIST BEFORE OUTPUTTING ===\n");
        p.append("[ ] Total calories = ").append(kcal).append(" (±30 kcal). Verified? YES\n");
        p.append("[ ] No foods violating dietary restrictions or allergies. Verified? YES\n");
        p.append("[ ] All ingredients have quantities. Verified? YES\n");
        p.append("[ ] water_intake is a JSON object with total_liters + schedule array + tips array. Verified? YES\n");
        p.append("[ ] All benefit fields are arrays. Verified? YES\n");
        p.append("[ ] All preparation_steps are arrays. Verified? YES\n");
        p.append("[ ] Output is pure JSON only — no markdown, no code blocks. Verified? YES\n");
        return p.toString();
    }

    public static String substitutionPrompt(String userHas, double cal, double prot, double carb, double fat) {
        return "You are an expert Indian Nutritionist. A user has available: \"" + userHas + "\".\n"
                + "Their meal targets are: Calories=" + num(cal) + " kcal, Protein=" + num(prot) + "g, Carbs=" + num(carb)
                + "g, Fat=" + num(fat) + "g.\n"
                + "Calculate EXACT quantities of the available food to match these targets as closely as possible.\n"
                + "Return ONLY a JSON object (no markdown) with these exact fields:\n"
                + "{ \"name\": \"Dish Name\", \"calories\": 500, \"protein\": 25, \"carbs\": 60, \"fat\": 15, \"fiber\": 5, "
                + "\"ingredients\": [\"200g dal\", \"1 cup rice (150g cooked)\"], \"preparation_steps\": [\"Step 1\", \"Step 2\"], "
                + "\"quantity_tips\": \"Quick tip on portions\", \"benefits\": [\"Benefit 1\", \"Benefit 2\"] }";
    }

    /** 70.0 → "70", 70.5 → "70.5" (how PHP printed numbers). */
    public static String num(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static final String JSON_TEMPLATE = """
            {
              "day_title": "Catchy professional title for Day {{DAY}} in {{LANG}}",
              "daily_objectives": ["Target Focus: Detailed objective with reasoning", "Health Metric: Specific impact for {{GOAL}}", "Macro Focus: Protein/Carb strategy for today", "Mindset/Consistency objective", "Metabolic trigger goal"],
              "day_summary": "3-4 concise yet highly professional sentences explaining HOW today's entire combination of meals specifically targets {{GOAL}}. Explain the synergy between meals and what physiologic responses the client should expect (e.g., energy stability, reduced bloating, muscle synthesis).",
              "target_stats": { "calories": {{KCAL}}, "protein": "<auto-calc>", "carbs": "<auto-calc>", "fat": "<auto-calc>", "fiber": "<auto-calc>" },
              "water_intake": {
                "total_liters": 2.5,
                "schedule": [
                  "6:30 AM - 2 glasses (500ml) warm water on empty stomach",
                  "8:00 AM - 1 glass (250ml) with breakfast",
                  "11:00 AM - 1 glass (250ml) mid-morning",
                  "1:00 PM - 1 glass (250ml) before lunch",
                  "3:00 PM - 1 glass (250ml) afternoon",
                  "5:00 PM - 1 glass (250ml) with evening snack",
                  "8:00 PM - 1 glass (250ml) with/after dinner"
                ],
                "tips": [
                  "Goal-specific hydration tip 1 in {{LANG}}",
                  "Tip about what to add to water (e.g., nimbu, kheera, jeera) relevant to {{GOAL}}",
                  "Warning about what to avoid (excess tea/coffee) based on {{GOAL}}"
                ]
              },
              "timeline": [
                {
                  "time": "6:30 AM",
                  "title": "Wake-Up Ritual",
                  "type": "ritual",
                  "items": [
                    {
                      "name": "Indian ritual drink name",
                      "quantity": "200-250ml",
                      "instruction": "Detailed preparation instruction",
                      "benefit": ["Specific benefit for {{GOAL}}", "Metabolic/digestive benefit"]
                    }
                  ]
                },
                {
                  "time": "8:00 AM",
                  "title": "Breakfast",
                  "type": "meal",
                  "name": "Overall breakfast category name",
                  "option_1": {
                    "name": "Full Indian dish name",
                    "calories": 420,
                    "protein": 22,
                    "carbs": 48,
                    "fat": 12,
                    "fiber": 6,
                    "ingredients": ["150g ingredient with quantity", "2 medium roti (approx 60g each)"],
                    "preparation_steps": ["Step 1 with Indian cooking detail", "Step 2", "Step 3"],
                    "benefits": ["How this specific dish helps {{GOAL}}", "Nutrient-specific benefit", "Energy/recovery benefit"]
                  },
                  "option_2": {
                    "name": "Different Indian dish name",
                    "calories": 410,
                    "protein": 20,
                    "carbs": 50,
                    "fat": 11,
                    "fiber": 5,
                    "ingredients": ["ingredient with quantity"],
                    "preparation_steps": ["Step 1", "Step 2"],
                    "benefits": ["Benefit 1 for {{GOAL}}", "Benefit 2"]
                  },
                  "suggestions": "Expert nutritionist tip for this meal relevant to {{GOAL}}"
                },
                {
                  "time": "11:00 AM",
                  "title": "Mid-Morning Snack",
                  "type": "meal",
                  "name": "Snack name",
                  "calories": 180,
                  "protein": 8,
                  "carbs": 22,
                  "fat": 6,
                  "fiber": 3,
                  "ingredients": ["precise quantity ingredient"],
                  "preparation_steps": ["Quick prep step"],
                  "benefits": ["Benefit for {{GOAL}}", "Satiety/energy benefit"]
                },
                {
                  "time": "1:30 PM",
                  "title": "Lunch",
                  "type": "meal",
                  "name": "Lunch category",
                  "option_1": { "name": "Indian lunch name", "calories": 550, "protein": 28, "carbs": 65, "fat": 16, "fiber": 9, "ingredients": [], "preparation_steps": [], "benefits": [] },
                  "option_2": { "name": "Different Indian lunch", "calories": 540, "protein": 25, "carbs": 68, "fat": 14, "fiber": 8, "ingredients": [], "preparation_steps": [], "benefits": [] },
                  "suggestions": "Lunch expert tip"
                },
                {
                  "time": "4:30 PM",
                  "title": "Evening Snack",
                  "type": "meal",
                  "name": "Snack name",
                  "calories": 150,
                  "protein": 7,
                  "carbs": 18,
                  "fat": 5,
                  "fiber": 2,
                  "ingredients": [],
                  "preparation_steps": [],
                  "benefits": []
                },
                {
                  "time": "7:30 PM",
                  "title": "Dinner",
                  "type": "meal",
                  "name": "Dinner category",
                  "option_1": { "name": "Light Indian dinner name", "calories": 480, "protein": 24, "carbs": 58, "fat": 14, "fiber": 7, "ingredients": [], "preparation_steps": [], "benefits": [] },
                  "option_2": { "name": "Different dinner option", "calories": 470, "protein": 22, "carbs": 60, "fat": 13, "fiber": 6, "ingredients": [], "preparation_steps": [], "benefits": [] },
                  "suggestions": "Dinner timing and digestion tip"
                }
              ],
              "daily_dos": [
                "Specific actionable do for {{GOAL}} today",
                "Timing-related do (e.g., eat within 30 min of waking)",
                "Movement/lifestyle do aligned with {{ACTIVITY}} activity",
                "Seasonal do for {{SEASON}}"
              ],
              "daily_donts": [
                "Specific food to avoid for {{GOAL}} with reason",
                "Habit to avoid today with reason",
                "Seasonal dont for {{SEASON}}",
                "Common Indian mistake to avoid"
              ],
              "summary_table": {
                "total_calories": {{KCAL}},
                "focus": "{{GOAL}}",
                "expected_result": "Specific expected result for today's plan",
                "key_nutrients": "Top 3 nutrients highlighted today and why"
              }
            }""";
}
