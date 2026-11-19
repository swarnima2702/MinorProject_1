package com.mrdiet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the holes found in the PHP version are closed.
 * Run with:  mvn test
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------- helpers

    private String signup(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\",\"data\":{\"name\":\"Test\"}}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.path("user").path("token").asText()).isNotBlank(); // token stored with the user object
        return body.path("token").asText();
    }

    private String newUser() throws Exception {
        return signup("u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private void createProfile(String token, String gender) throws Exception {
        mvc.perform(post("/api/profile").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"age\":28,\"gender\":\"" + gender + "\",\"height\":160,\"weight\":62,"
                                + "\"goal\":\"weight-loss\",\"activity_level\":\"light\",\"dietary_restrictions\":[\"No onion\"],"
                                + "\"extended_data\":{\"phone\":\"9876543210\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("9876543210"));
    }

    private long createSubscription(String token, String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/subscriptions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void protectedRoutesNeedALogin() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/diet-plans/generate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        // A forged PHP-style token is rejected too
        mvc.perform(get("/api/profile").header("Authorization", "Bearer mock_token_1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void normalUsersCannotReachAdminRoutes() throws Exception {
        String token = newUser();
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users?admin_id=1").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void paidPlanCannotBeSelfActivatedAndPriceIsServerSide() throws Exception {
        String token = newUser();
        createProfile(token, "male");
        // Browser claims amount 1 and status completed — both must be ignored.
        long subId = createSubscription(token,
                "{\"plan_type\":\"weekly\",\"amount\":1,\"payment_status\":\"completed\",\"end_date\":\"2099-01-01\"}");
        String status = jdbc.queryForObject("SELECT payment_status FROM subscriptions WHERE id = ?", String.class, subId);
        String amount = jdbc.queryForObject("SELECT amount FROM subscriptions WHERE id = ?", String.class, subId);
        assertThat(status).isEqualTo("pending");
        assertThat(amount).startsWith("299");

        mvc.perform(patch("/api/subscriptions/" + subId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_status\":\"completed\"}"))
                .andExpect(status().isPaymentRequired());
        mvc.perform(get("/api/subscriptions/active").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT payment_status FROM subscriptions WHERE id = ?", String.class, subId))
                .isEqualTo("pending");
    }

    @Test
    void usersCannotTouchSomeoneElsesPlan() throws Exception {
        String alice = newUser();
        String bob = newUser();
        createProfile(alice, "female");
        long aliceTrial = createSubscription(alice, "{\"plan_type\":\"trial\"}");

        mvc.perform(get("/api/diet-plans?subscription_id=" + aliceTrial).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/subscriptions/" + aliceTrial).header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"payment_status\":\"cancelled\"}"))
                .andExpect(status().isNotFound());
        // ...and Alice can use her own
        mvc.perform(get("/api/diet-plans?subscription_id=" + aliceTrial).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void freeTrialOnlyOnce() throws Exception {
        String token = newUser();
        createProfile(token, "male");
        createSubscription(token, "{\"plan_type\":\"trial\"}");
        mvc.perform(post("/api/subscriptions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plan_type\":\"trial\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void publicSettingsHideSecrets() throws Exception {
        String body = mvc.perform(get("/api/settings/public")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("priceWeekly").doesNotContain("openai_api_key").doesNotContain("rzp-secret");
    }

    @Test
    void oldPhpPasswordsStillWork() throws Exception {
        // A hash in PHP password_hash() format ($2y$) for the password "secret123"
        jdbc.update("INSERT INTO users (email, password, name, referral_code) VALUES (?, ?, ?, ?)",
                "legacy@example.com", "$2y$10$uKBn/dUQOUsIzzm4yfz.jeqUZZ8uQLIOyUG7BBzrc8yH/UlhCWHHW", "Legacy", "LEGACY01");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"legacy@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("legacy@example.com"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"legacy@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void payoutCannotExceedBalance() throws Exception {
        String token = newUser();
        mvc.perform(post("/api/partner/payouts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500}"))
                .andExpect(status().isBadRequest());
    }
}
