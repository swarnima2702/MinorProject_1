package com.mrdiet.diet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.HttpJson;
import com.mrdiet.config.AppProperties;
import com.mrdiet.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Chat Completions in JSON mode — the curl call from openai_service.php. */
@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final HttpJson http;
    private final SettingsService settings;
    private final AppProperties props;
    private final ObjectMapper mapper;

    public OpenAiClient(HttpJson http, SettingsService settings, AppProperties props, ObjectMapper mapper) {
        this.http = http;
        this.settings = settings;
        this.props = props;
        this.mapper = mapper;
    }

    /** @return the parsed JSON object the model produced. */
    public JsonNode json(String systemPrompt, String userPrompt, double temperature) {
        // Admin panel value first (like PHP), then the OPENAI_API_KEY env variable.
        String key = settings.get("openai_api_key", props.openai().apiKey());
        if (key == null || key.isBlank() || key.equals("YOUR_OPENAI_KEY_HERE")) {
            throw ApiException.badRequest("Server Configuration Error: OpenAI Key Missing");
        }
        String model = props.openai().model() == null || props.openai().model().isBlank()
                ? "gpt-4o-mini" : props.openai().model();

        ObjectNode req = mapper.createObjectNode();
        req.put("model", model);
        ArrayNode messages = req.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        req.put("temperature", temperature);
        req.putObject("response_format").put("type", "json_object");

        HttpJson.Result r = http.send(http.client().post().uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req.toString()));

        if (r.status() == 0) {
            throw ApiException.upstream("Connection failed. Please check your internet or try again later.");
        }
        if (!r.ok() || r.body().has("error")) {
            log.warn("OpenAI error ({}): {}", r.status(), r.body().path("error"));
            throw ApiException.upstream("AI service error: " + r.body().path("error").path("message").asText("Unknown API Error"));
        }
        String content = r.body().path("choices").path(0).path("message").path("content").asText(null);
        JsonNode parsed = parseLenient(content);
        if (parsed == null || !parsed.isObject()) {
            throw ApiException.upstream("Failed to parse AI response");
        }
        return parsed;
    }

    /** Parse JSON, or the {...} block inside it if the model wrapped it in text. */
    private JsonNode parseLenient(String content) {
        if (content == null) return null;
        try {
            return mapper.readTree(content);
        } catch (Exception ignored) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return mapper.readTree(content.substring(start, end + 1));
                } catch (Exception ignoredToo) {
                    return null;
                }
            }
            return null;
        }
    }
}
