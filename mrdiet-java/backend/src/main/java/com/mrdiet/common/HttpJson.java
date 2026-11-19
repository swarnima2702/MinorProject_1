package com.mrdiet.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Outgoing HTTP calls (OpenAI, Cashfree, Razorpay, Google, OneSignal).
 * Replaces the curl_* blocks in the PHP code. Never throws on 4xx/5xx — callers
 * look at {@link Result#ok()} and decide what to tell the user.
 */
@Component
public class HttpJson {

    public record Result(int status, JsonNode body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private final RestClient client;
    private final ObjectMapper mapper;

    public HttpJson(ObjectMapper mapper) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(120_000); // AI plans can take a while
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public RestClient client() {
        return client;
    }

    /** Execute a prepared request and parse whatever comes back as JSON. */
    public Result send(RestClient.RequestHeadersSpec<?> spec) {
        try {
            return spec.exchange((request, response) -> {
                int code = response.getStatusCode().value();
                byte[] bytes = response.getBody().readAllBytes();
                return new Result(code, parse(bytes));
            });
        } catch (Exception e) {
            return new Result(0, TextNode.valueOf("Connection failed: " + e.getMessage()));
        }
    }

    private JsonNode parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return mapper.nullNode();
        try {
            return mapper.readTree(bytes);
        } catch (Exception e) {
            return TextNode.valueOf(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
