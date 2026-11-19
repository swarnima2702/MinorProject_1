package com.mrdiet.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.J;
import com.mrdiet.security.AuthUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Was notifications.php. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/preferences")
    public Map<String, Object> get(@AuthenticationPrincipal AuthUser me) {
        return notifications.get(me);
    }

    @PostMapping("/preferences")
    public Map<String, Object> save(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return notifications.save(me, body);
    }

    @PostMapping("/device")
    public Map<String, Object> register(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return notifications.registerDevice(me, J.str(body, "player_id"));
    }

    @DeleteMapping("/device")
    public Map<String, Object> unregister(@AuthenticationPrincipal AuthUser me) {
        return notifications.unregisterDevice(me);
    }

    @PostMapping("/test")
    public Map<String, Object> test(@AuthenticationPrincipal AuthUser me) {
        return notifications.sendTest(me);
    }

    @ExceptionHandler(NotificationService.OneSignalFailure.class)
    public ResponseEntity<Map<String, Object>> oneSignalFailed(NotificationService.OneSignalFailure e) {
        return ResponseEntity.badRequest().body(e.body());
    }
}
