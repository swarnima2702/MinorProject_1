package com.mrdiet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Everything that used to live in api/config/config.php — now read from
 * application.yml, which in turn reads environment variables (see .env.example).
 * No secret is hard-coded in the source any more.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        String publicUrl,
        String uploadDir,
        Jwt jwt,
        Cors cors,
        Google google,
        OpenAi openai,
        Cashfree cashfree,
        Razorpay razorpay,
        OneSignal onesignal,
        Reminders reminders,
        Ai ai
) {
    public record Jwt(String secret, long expiryDays) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Google(String clientId) {}
    public record OpenAi(String apiKey, String model) {}
    public record Cashfree(String appId, String secretKey, String apiVersion) {}
    public record Razorpay(String keyId, String keySecret) {}
    public record OneSignal(String appId, String restApiKey) {}
    public record Reminders(boolean enabled) {}
    public record Ai(int maxGenerationsPerHour) {}
}
