package com.mrdiet.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Was profiles.php. You can only ever read or change YOUR OWN profile. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal AuthUser me) {
        return profiles.get(me);
    }

    @PostMapping
    public Map<String, Object> save(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
        return profiles.save(me, body);
    }
}
