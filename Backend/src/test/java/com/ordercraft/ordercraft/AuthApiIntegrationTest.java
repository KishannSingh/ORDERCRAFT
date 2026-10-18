package com.ordercraft.ordercraft;

import com.ordercraft.ordercraft.repository.AppUserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository appUserRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Value("${security.jwt.secret}") String jwtSecret;

    @Test
    void registrationLoginInvalidCredentialsAndProtectedRoutesWorkOverHttp() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@example.test";
        String password = "OrderCraftTestPass123!";
        String registerBody = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        String registrationResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertFalse(registrationResponse.contains(password));
        var savedUser = appUserRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertNotEquals(password, savedUser.getPassword());
        assertTrue(passwordEncoder.matches(password, savedUser.getPassword()));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unicode-limit@example.test\",\"password\":\""
                                + "é".repeat(40) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"incorrect-password\"}"))
                .andExpect(status().isUnauthorized());

        String loginBody = "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + password + "\"}";
        String token = tokenFrom(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        assertNotNull(token);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBoms").isNumber())
                .andExpect(jsonPath("$.totalPurchaseOrders").isNumber())
                .andExpect(jsonPath("$.paidInvoices").isNumber())
                .andExpect(jsonPath("$.pendingOrders").isNumber());

        mockMvc.perform(get("/customers"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/customers").header(HttpHeaders.AUTHORIZATION, "Bearer malformed.token.value"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/customers").header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/customers").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/customers").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String tokenFrom(String response) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(response);
        if (!matcher.find()) {
            throw new AssertionError("Login response did not contain a JWT token");
        }
        return matcher.group(1);
    }

    private String expiredToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("expired@example.test")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String forgedToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("untrusted@example.test")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(
                        "different-test-key-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
