package com.mrdiet.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mrdiet.common.ApiException;
import com.mrdiet.common.HttpJson;
import com.mrdiet.config.AppProperties;
import com.mrdiet.settings.SettingsService;
import org.springframework.stereotype.Component;

/**
 * Asks Google who a token belongs to. The old "verify_google_manual" action
 * trusted whatever email the browser sent, so anyone could log in as anyone.
 * Now the backend checks the token with Google and that it was issued to OUR
 * client id before trusting the email.
 */
@Component
public class GoogleVerifier {

    public record GoogleUser(String sub, String email, String name, String picture) {}

    private final HttpJson http;
    private final AppProperties props;
    private final SettingsService settings;

    public GoogleVerifier(HttpJson http, AppProperties props, SettingsService settings) {
        this.http = http;
        this.props = props;
        this.settings = settings;
    }

    /** @param accessToken from useGoogleLogin() (implicit flow), or
     *  @param idToken     a "credential" from the One-Tap / GoogleLogin button. */
    public GoogleUser verify(String accessToken, String idToken) {
        String clientId = settings.get("google_client_id", props.google().clientId());
        boolean useIdToken = idToken != null && !idToken.isBlank();
        String param = useIdToken ? "id_token" : "access_token";
        String token = useIdToken ? idToken : accessToken;
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("Missing Google token");
        }

        HttpJson.Result info = http.send(http.client().get()
                .uri("https://oauth2.googleapis.com/tokeninfo?" + param + "={t}", token));
        if (!info.ok() || info.body() == null || !info.body().isObject()) {
            throw ApiException.unauthorized("Google sign-in could not be verified");
        }
        JsonNode b = info.body();
        String aud = b.path("aud").asText("");
        String azp = b.path("azp").asText("");
        if (!clientId.equals(aud) && !clientId.equals(azp)) {
            throw ApiException.unauthorized("Google token was issued to a different app");
        }
        String sub = b.path("sub").asText("");
        String email = b.path("email").asText("");
        boolean verified = "true".equalsIgnoreCase(b.path("email_verified").asText("false"));
        if (sub.isEmpty() || email.isEmpty() || !verified) {
            throw ApiException.unauthorized("Your Google email is not verified");
        }

        String name = b.path("name").asText("");
        String picture = b.path("picture").asText("");
        if (!useIdToken) {
            // tokeninfo for access tokens has no name/picture — fetch the profile.
            HttpJson.Result profile = http.send(http.client().get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + token));
            if (profile.ok() && profile.body() != null) {
                name = profile.body().path("name").asText(name);
                picture = profile.body().path("picture").asText(picture);
            }
        }
        if (name.isBlank()) name = email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length());
        return new GoogleUser(sub, email, name, picture);
    }
}
