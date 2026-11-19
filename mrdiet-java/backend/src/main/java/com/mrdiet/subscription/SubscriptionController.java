package com.mrdiet.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/** Was subscriptions.php. The user always comes from the login token. */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** Active plan or JSON null. */
    @GetMapping("/active")
    public Map<String, Object> active(@AuthenticationPrincipal AuthUser me) {
        return subscriptions.active(me);
    }

    @GetMapping
    public List<Map<String, Object>> all(@AuthenticationPrincipal AuthUser me) {
        return subscriptions.all(me);
    }

    @GetMapping("/preview")
    public Map<String, Object> preview(@AuthenticationPrincipal AuthUser me,
                                       @RequestParam("new_plan") String newPlan) {
        return subscriptions.preview(me, newPlan);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return subscriptions.create(me, body);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                      @RequestBody JsonNode body) {
        return subscriptions.updateStatus(me, id, body);
    }
}
