package com.mrdiet.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrdiet.common.HttpJson;
import com.mrdiet.config.AppProperties;
import com.mrdiet.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Sends one push notification to one device (sendOneSignalNotification() in PHP). */
@Component
public class OneSignalClient {

    private static final Logger log = LoggerFactory.getLogger(OneSignalClient.class);

    public record Result(boolean success, int code, JsonNode error) {}

    private final HttpJson http;
    private final SettingsService settings;
    private final AppProperties props;
    private final ObjectMapper mapper;

    public OneSignalClient(HttpJson http, SettingsService settings, AppProperties props, ObjectMapper mapper) {
        this.http = http;
        this.settings = settings;
        this.props = props;
        this.mapper = mapper;
    }

    public boolean configured() {
        String appId = appId();
        return !appId.isBlank() && !apiKey().isBlank() && !appId.startsWith("YOUR_");
    }

    public Result send(String playerId, String title, String message, String url) {
        if (!configured()) {
            log.warn("OneSignal keys not configured");
            return new Result(false, 0, mapper.getNodeFactory().textNode("OneSignal keys not configured"));
        }
        String key = apiKey();
        ObjectNode body = mapper.createObjectNode();
        body.put("app_id", appId());
        body.putArray("include_player_ids").add(playerId);
        body.putObject("headings").put("en", title);
        body.putObject("contents").put("en", message);
        if (url != null) body.put("url", url);

        // Newer OneSignal keys ("os_v2_…") use the "Key" scheme on api.onesignal.com;
        // older REST keys use "Basic" on onesignal.com — the PHP code used the latter.
        boolean v2 = key.startsWith("os_v2_");
        String endpoint = v2 ? "https://api.onesignal.com/notifications" : "https://onesignal.com/api/v1/notifications";
        HttpJson.Result r = http.send(http.client().post().uri(endpoint)
                .header("Authorization", (v2 ? "Key " : "Basic ") + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString()));
        if (r.ok()) return new Result(true, r.status(), null);
        log.warn("OneSignal error {}: {}", r.status(), r.body());
        return new Result(false, r.status(), r.body());
    }

    private String appId() {
        String v = settings.get("onesignal_app_id", props.onesignal().appId());
        return v == null ? "" : v;
    }

    private String apiKey() {
        String v = settings.get("onesignal_rest_api_key", props.onesignal().restApiKey());
        return v == null ? "" : v;
    }
}
