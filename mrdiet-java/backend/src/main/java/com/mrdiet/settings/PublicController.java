package com.mrdiet.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Was settings_public.php. */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final SettingsService settings;

    public PublicController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/settings/public")
    public Map<String, String> publicSettings() {
        return settings.publicSettings();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
