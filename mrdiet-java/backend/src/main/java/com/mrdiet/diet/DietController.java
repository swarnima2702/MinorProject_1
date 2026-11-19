package com.mrdiet.diet;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.security.AuthUser;
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

import java.util.List;
import java.util.Map;

/** Day plans, AI generation, meal swaps, check-ins and unlock timers. */
@RestController
@RequestMapping("/api")
public class DietController {

    private final DietPlanService plans;
    private final MealGenerationService generation;

    public DietController(DietPlanService plans, MealGenerationService generation) {
        this.plans = plans;
        this.generation = generation;
    }

    @GetMapping("/diet-plans")
    public List<Map<String, Object>> list(@AuthenticationPrincipal AuthUser me,
                                          @RequestParam("subscription_id") long subscriptionId) {
        return plans.list(me, subscriptionId);
    }

    @PostMapping("/diet-plans/batch")
    public Map<String, Object> batch(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode rows) {
        return plans.createBatch(me, rows);
    }

    @PatchMapping("/diet-plans/{subscriptionId}/days/{day}")
    public Map<String, Object> updateDay(@AuthenticationPrincipal AuthUser me, @PathVariable long subscriptionId,
                                         @PathVariable int day, @RequestBody JsonNode body) {
        return plans.updateDay(me, subscriptionId, day, body);
    }

    @PostMapping("/diet-plans/generate")
    public Map<String, Object> generate(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return generation.generate(me, body);
    }

    @PostMapping("/diet-plans/substitute")
    public Map<String, Object> substitute(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return generation.substitute(me, body);
    }

    @PostMapping("/checkins")
    public Map<String, Object> checkIn(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return plans.checkIn(me, body);
    }

    @GetMapping("/timers")
    public Map<String, Object> getTimer(@AuthenticationPrincipal AuthUser me,
                                        @RequestParam("subscription_id") long subscriptionId,
                                        @RequestParam("day_number") int day) {
        return plans.getTimer(me, subscriptionId, day);
    }

    @PostMapping("/timers")
    public Map<String, Object> createTimer(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return plans.createTimer(me, body);
    }

    @DeleteMapping("/timers")
    public Map<String, Object> deleteTimer(@AuthenticationPrincipal AuthUser me,
                                           @RequestParam("subscription_id") long subscriptionId,
                                           @RequestParam("day_number") int day) {
        return plans.deleteTimer(me, subscriptionId, day);
    }
}
