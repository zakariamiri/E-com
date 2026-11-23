package org.sid.Auth_service.security;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.sid.Auth_service.entity.*;
import org.sid.Auth_service.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// These tests exercise real HTTP/database components and do not require a Mockito JVM agent.
@org.springframework.test.context.TestExecutionListeners(
        listeners = org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        mergeMode = org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class AuthenticationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach void defaultRole() {
        if (roles.findByName("USER").isEmpty()) {
            Role role = new Role(); role.setName("USER"); roles.saveAndFlush(role);
        }
    }

    HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    String email() { return "test-" + UUID.randomUUID() + "@example.com"; }
    String register(String email) throws Exception {
        var result = request("POST", "/api/users", """
                {"firstName":"Rim","lastName":"Sakine","email":"%s","password":"Demo-V6-2026!"}
                """.formatted(email), null);
        assertEquals(201, result.statusCode(), result.body());
        assertFalse(result.body().contains("password"));
        return json.readTree(result.body()).get("id").asText();
    }
    String login(String email) throws Exception {
        var result = request("POST", "/api/auth/login", """
                {"email":"%s","password":"Demo-V6-2026!"}
                """.formatted(email), null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.headers().firstValue("cache-control").orElse("").contains("no-store"));
        return json.readTree(result.body()).get("accessToken").asText();
    }

    @Test void registrationLoginAndOwnerPermissions() throws Exception {
        String email = email(); String id = register(email); String token = login(email);
        assertTrue(passwords.matches("Demo-V6-2026!", users.findById(Long.valueOf(id)).orElseThrow().getPasswordHash()));
        assertEquals(200, request("GET", "/api/auth/me", null, token).statusCode());
        assertEquals(200, request("GET", "/api/users/" + id, null, token).statusCode());
        assertEquals(401, request("GET", "/api/users/" + id, null, null).statusCode());
        assertEquals(403, request("GET", "/api/users", null, token).statusCode());
        String other = register(email());
        assertEquals(403, request("GET", "/api/users/" + other, null, token).statusCode());
        assertEquals(403, request("DELETE", "/api/users/" + other, null, token).statusCode());
        assertEquals(403, request("PUT", "/api/users/" + other,
                "{\"firstName\":\"Other\",\"lastName\":\"User\",\"email\":\"other@example.com\"}", token).statusCode());
        assertEquals(200, request("PUT", "/api/users/" + id,
                "{\"firstName\":\"Rim\",\"lastName\":\"Sakine\",\"email\":\"" + email + "\"}", token).statusCode());
        assertEquals(204, request("DELETE", "/api/users/" + id, null, token).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", null, token).statusCode());
    }

    @Test void badCredentialsDisabledAccountAndInvalidTokens() throws Exception {
        String email = email(); String id = register(email); String token = login(email);
        assertEquals(401, request("POST", "/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"incorrect\"}", null).statusCode());
        assertEquals(401, request("POST", "/api/auth/login", "{\"email\":\"unknown@example.com\",\"password\":\"incorrect\"}", null).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", null, token.substring(0, token.length() - 12) + "invalid").statusCode());
        assertEquals(401, request("GET", "/api/auth/me", null, "not-a-jwt").statusCode());
        for (String issuer : List.of("novastore-auth", "wrong-issuer")) {
            Instant now = Instant.now();
            var claims = JwtClaimsSet.builder().issuer(issuer).subject(id).audience(List.of("novastore-auth-api"))
                    .issuedAt(now.minusSeconds(600)).expiresAt(issuer.equals("novastore-auth") ? now.minusSeconds(300) : now.plusSeconds(900))
                    .claim("roles", List.of("USER")).build();
            String invalid = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            assertEquals(401, request("GET", "/api/auth/me", null, invalid).statusCode());
        }
        User user = users.findById(Long.valueOf(id)).orElseThrow(); user.setEnabled(false); users.saveAndFlush(user);
        assertEquals(401, request("GET", "/api/auth/me", null, token).statusCode());
        assertEquals(401, request("POST", "/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"Demo-V6-2026!\"}", null).statusCode());
    }

    @Test void adminAndInternalSummaryAreLimited() throws Exception {
        String email = email(); String id = register(email);
        Role admin = roles.findByName("ADMIN").orElseGet(() -> { Role r = new Role(); r.setName("ADMIN"); return roles.saveAndFlush(r); });
        // Join table update avoids touching a detached lazy collection in this HTTP integration test.
        users.findById(Long.valueOf(id)).orElseThrow();
        grantAdmin(Long.valueOf(id), admin.getId());
        assertEquals(200, request("GET", "/api/users", null, login(email)).statusCode());
        var internal = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + id + "/summary"))
                .header("X-Internal-Token", "test-internal-token-for-security-tests").GET().build();
        assertEquals(200, http.send(internal, HttpResponse.BodyHandlers.ofString()).statusCode());
        var forbidden = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users"))
                .header("X-Internal-Token", "test-internal-token-for-security-tests").GET().build();
        assertEquals(401, http.send(forbidden, HttpResponse.BodyHandlers.ofString()).statusCode());
        var wrong = HttpRequest.newBuilder(internal.uri()).header("X-Internal-Token", "wrong").GET().build();
        assertEquals(401, http.send(wrong, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    void grantAdmin(Long id, Long role) { jdbc.update("INSERT INTO auth_user_roles(user_id,role_id) VALUES (?,?)", id, role); }

    @Test void duplicateEmailValidationAndRoleInjection() throws Exception {
        String email = email(); register(email);
        var invalidPassword = request("POST", "/api/users", "{\"firstName\":\"Rim\",\"lastName\":\"Sakine\",\"email\":\"" + email() + "\",\"password\":\"short!\"}", null);
        assertEquals(400, invalidPassword.statusCode());
        assertFalse(invalidPassword.body().contains("short!"));
        assertEquals(409, request("POST", "/api/users", "{\"firstName\":\"Rim\",\"lastName\":\"Sakine\",\"email\":\"" + email.toUpperCase(Locale.ROOT) + "\",\"password\":\"Demo-V6-2026!\"}", null).statusCode());
        assertEquals(400, request("POST", "/api/users", "{\"firstName\":\"Rim\",\"lastName\":\"Sakine\",\"email\":\"" + email() + "\",\"password\":\"Demo-V6-2026!\",\"roles\":[\"ADMIN\"]}", null).statusCode());
        assertEquals(400, request("POST", "/api/auth/login", "{\"email\":\"invalid\",\"password\":\"\"}", null).statusCode());
    }
}
