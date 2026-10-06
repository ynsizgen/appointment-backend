package com.appointmentplatform;

import com.appointmentplatform.identity.security.Totp;
import com.appointmentplatform.identity.domain.*;
import com.appointmentplatform.identity.persistence.*;
import com.appointmentplatform.notification.persistence.EmailJobRepository;
import com.appointmentplatform.shared.Crypto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityBrandIntegrationTests {
    private static final String ADMIN_TOTP_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    @LocalServerPort
    int port;
    @Autowired
    PendingRegistrationRepository registrations;
    @Autowired
    PendingForgotBrandPasswordRepository passwordResets;
    @Autowired
    UserAccountRepository users;
    @Autowired
    EmailJobRepository emailJobs;
    @Autowired
    ObjectMapper json;
    @Autowired
    Crypto crypto;
    @Autowired
    PasswordEncoder passwords;
    @Autowired Totp totp;
    @Autowired Clock clock;

    @Test
    void registrationCreatesBrandAndBranchAndSeparatesAdminAndOwnerControls() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "owner-" + suffix + "@example.com";
        String otherEmail = "other-" + suffix + "@example.com";
        createVerifiedBrand(email, "First Brand");
        createVerifiedBrand(otherEmail, "Second Brand");
        var owner = loginBrand(email);
        var other = loginBrand(otherEmail);
        var account = json.readTree(get(owner, "/api/auth/me").body());
        String userId = account.get("id").asText();
        assertThat(account.get("enabled").asBoolean()).isTrue();
        assertThat(account.get("subscriptionActive").asBoolean()).isFalse();
        var brand = json.readTree(get(owner, "/api/panel/brand").body());
        assertThat(brand.get("displayName").asText()).isEqualTo("First Brand");
        assertThat(brand.has("address")).isFalse();
        var branches = json.readTree(get(owner, "/api/panel/branches").body());
        assertThat(branches.size()).isEqualTo(1);
        var branch = branches.get(0);
        assertThat(branch.get("brandId").asText()).isEqualTo(brand.get("id").asText());
        assertThat(branch.get("name").asText()).isEqualTo("Merkez");
        assertThat(branch.get("address").asText()).isEqualTo("İstanbul, Türkiye");
        assertThat(branch.get("googleMapsUrl").asText()).isEqualTo("https://maps.google.com/example");
        assertThat(branch.get("acceptingAppointments").asBoolean()).isFalse();
        String acceptancePath = "/api/panel/branches/" + branch.get("id").asText() + "/appointment-acceptance";
        String statusPath = "/api/admin/users/" + userId + "/status";
        assertThat(get(owner, "/api/admin/users").statusCode()).isEqualTo(403);
        assertThat(patch(owner, statusPath, "{\"enabled\":true,\"subscriptionActive\":true}").statusCode()).isEqualTo(403);
        assertThat(patch(other, acceptancePath, "{\"acceptingAppointments\":true}").statusCode()).isEqualTo(404);
        assertThat(patch(owner, acceptancePath, "{}").statusCode()).isEqualTo(400);
        // The switch is the owner's preference, not proof of subscription.
        assertThat(patch(owner, acceptancePath, "{\"acceptingAppointments\":true}").statusCode()).isEqualTo(200);
        assertThat(json.readTree(get(owner, "/api/auth/me").body()).get("subscriptionActive").asBoolean()).isFalse();
        // A future add-branch UI is intentionally not exposed yet.
        assertThat(post(owner, "/api/panel/branches", "{}").statusCode()).isEqualTo(405);

        var admin = createAndLoginAdmin(suffix);
        assertThat(get(admin, "/api/admin/users?size=100").body()).contains(userId);
        assertThat(get(admin, "/api/admin/users?size=0").statusCode()).isEqualTo(400);
        assertThat(patch(admin, acceptancePath, "{\"acceptingAppointments\":false}").statusCode()).isEqualTo(403);
        assertThat(patch(admin, statusPath, "{\"enabled\":true}").statusCode()).isEqualTo(400);
        String adminId = json.readTree(get(admin, "/api/auth/me").body()).get("id").asText();
        assertThat(patch(admin, "/api/admin/users/" + adminId + "/status", "{\"enabled\":false,\"subscriptionActive\":false}").statusCode()).isEqualTo(404);
        assertThat(patch(admin, statusPath, "{\"enabled\":true,\"subscriptionActive\":true}").statusCode()).isEqualTo(200);
        assertThat(json.readTree(get(owner, "/api/auth/me").body()).get("subscriptionActive").asBoolean()).isTrue();
        assertThat(patch(owner, acceptancePath, "{\"acceptingAppointments\":false}").statusCode()).isEqualTo(200);
        assertThat(json.readTree(get(owner, "/api/auth/me").body()).get("subscriptionActive").asBoolean()).isTrue();
        // Subscription expiry keeps panel access; it does not change branch preference.
        assertThat(patch(admin, statusPath, "{\"enabled\":true,\"subscriptionActive\":false}").statusCode()).isEqualTo(200);
        assertThat(get(owner, "/api/panel/brand").statusCode()).isEqualTo(200);
        var untouchedSession = loginBrand(email);
        assertThat(patch(admin, statusPath, "{\"enabled\":false,\"subscriptionActive\":false}").statusCode()).isEqualTo(200);
        assertThat(get(owner, "/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(post(session(), "/api/auth/login", brandLogin(email)).statusCode()).isEqualTo(401);
        assertThat(patch(admin, statusPath, "{\"enabled\":true,\"subscriptionActive\":false}").statusCode()).isEqualTo(200);
        assertThat(get(untouchedSession, "/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(get(loginBrand(email), "/api/panel/branches").statusCode()).isEqualTo(200);
        assertThat(get(other, "/api/panel/brand").statusCode()).isEqualTo(200);
        assertThat(patch(admin, "/api/admin/tenants/" + brand.get("id").asText() + "/activation", "{\"active\":true}").statusCode()).isEqualTo(404);
    }

    private String brandLogin(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"a-secure-password-123\"}";
    }

    private TestSession loginBrand(String email) throws Exception {
        var client = session();
        assertThat(post(client, "/api/auth/login", brandLogin(email)).statusCode()).isEqualTo(200);
        return client;
    }

    @Test
    void registrationDoesNotRevealExistingEmailAndSecretsAreNotStoredInPlaintext() throws Exception {
        String email = "privacy-" + UUID.randomUUID() + "@example.com";
        var client = session();
        String payload = registration(email, "Privacy Brand");
        var first = post(client, "/api/auth/registrations", payload);
        var duplicate = post(client, "/api/auth/registrations", payload);
        assertThat(first.statusCode()).isEqualTo(202);
        assertThat(duplicate.statusCode()).isEqualTo(202);
        assertThat(duplicate.body()).isEqualTo(first.body());

        String encrypted = emailJobs.findByRegistrationId(registrations.findByEmail(email).orElseThrow().id()).orElseThrow().encryptedBody();
        String token = tokenFrom(encrypted);
        String tokenHash = registrations.findByEmail(email).orElseThrow().tokenHash();
        String passwordHash = registrations.findByEmail(email).orElseThrow().passwordHash();
        assertThat(encrypted).doesNotContain(token, email, "a-secure-password-123");
        assertThat(tokenHash).isEqualTo(Crypto.hash(token)).doesNotContain(token);
        assertThat(passwordHash).doesNotContain("a-secure-password-123");
    }

    @Test
    void loginRequiresValidCredentialsAndAdminMfaCannotBeReplayed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "login-" + suffix + "@example.com";
        createVerifiedBrand(email, "Login Brand");

        var invalid = session();
        var result = post(invalid, "/api/auth/login", """
            {"email":"%s","password":"wrong-password"}
            """.formatted(email));
        assertThat(result.statusCode()).isEqualTo(401);
        assertThat(result.body()).doesNotContain("password", "stack", "exception");

        String adminEmail = insertAdmin(suffix);
        String currentCode = totp.code(ADMIN_TOTP_SECRET, clock.instant().getEpochSecond() / 30, 6);
        var admin = session();
        assertThat(post(admin, "/api/auth/login", adminLogin(adminEmail, currentCode)).statusCode()).isEqualTo(200);
        var replay = session();
        assertThat(post(replay, "/api/auth/login", adminLogin(adminEmail, currentCode)).statusCode()).isEqualTo(401);
    }

    @Test
    void brandPasswordResetUsesOneTimeTokenAndRevokesOldSession() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(email, "Reset Brand");
        var oldSession = session();
        assertThat(post(oldSession, "/api/auth/login", """
            {"email":"%s","password":"a-secure-password-123"}
            """.formatted(email)).statusCode()).isEqualTo(200);

        var resetSession = session();
        String requestBody = "{\"email\":\"" + email + "\"}";
        assertThat(post(resetSession, "/api/auth/forgot-brand-password", requestBody).statusCode()).isEqualTo(202);
        assertThat(post(resetSession, "/api/auth/forgot-brand-password", "{\"email\":\"absent-" + UUID.randomUUID() + "@example.com\"}").body())
                .contains("Adres uygunsa parola sıfırlama e-postası gönderilecektir.");

        var pending = passwordResets.findByUserId(users.findByEmail(email).orElseThrow().id()).orElseThrow();
        var encrypted = emailJobs.findByPasswordResetId(pending.id()).orElseThrow().encryptedBody();
        String token = tokenFrom(encrypted);
        assertThat(post(resetSession, "/api/auth/forgot-brand-password", requestBody).statusCode()).isEqualTo(202);
        assertThat(passwordResets.findById(pending.id())).isEmpty();
        pending = passwordResets.findByUserId(users.findByEmail(email).orElseThrow().id()).orElseThrow();
        encrypted = emailJobs.findByPasswordResetId(pending.id()).orElseThrow().encryptedBody();
        token = tokenFrom(encrypted);
        assertThat(pending.tokenHash()).isEqualTo(Crypto.hash(token)).doesNotContain(token);
        assertThat(crypto.decrypt(encrypted)).contains("/reset-password#token=");
        assertThat(get(resetSession, "/reset-password").statusCode()).isEqualTo(200);
        assertThat(get(resetSession, "/reset-password.js").statusCode()).isEqualTo(200);

        String payload = "{\"token\":\"" + token + "\",\"password\":\"a-new-secure-password-456\"}";
        assertThat(post(resetSession, "/api/auth/reset-brand-password", payload).statusCode()).isEqualTo(204);
        assertThat(post(resetSession, "/api/auth/reset-brand-password", payload).statusCode()).isEqualTo(400);
        assertThat(passwordResets.findByUserId(users.findByEmail(email).orElseThrow().id())).isEmpty();
        assertThat(get(oldSession, "/api/auth/me").statusCode()).isEqualTo(401);

        var newSession = session();
        assertThat(post(newSession, "/api/auth/login", """
            {"email":"%s","password":"a-secure-password-123"}
            """.formatted(email)).statusCode()).isEqualTo(401);
        assertThat(post(newSession, "/api/auth/login", """
            {"email":"%s","password":"a-new-secure-password-456"}
            """.formatted(email)).statusCode()).isEqualTo(200);
    }

    private void createVerifiedBrand(String email, String name) throws Exception {
        var client = session();
        assertThat(post(client, "/api/auth/registrations", registration(email, name)).statusCode()).isEqualTo(202);
        String encrypted = emailJobs.findByRegistrationId(registrations.findByEmail(email).orElseThrow().id()).orElseThrow().encryptedBody();
        String token = tokenFrom(encrypted);
        assertThat(post(client, "/api/auth/verify-email", "{\"token\":\"" + token + "\"}").statusCode()).isEqualTo(204);
    }

    private TestSession createAndLoginAdmin(String suffix) throws Exception {
        String email = insertAdmin(suffix);
        String code = totp.code(ADMIN_TOTP_SECRET, clock.instant().getEpochSecond() / 30, 6);
        var client = session();
        assertThat(post(client, "/api/auth/login", adminLogin(email, code)).statusCode()).isEqualTo(200);
        return client;
    }

    private String insertAdmin(String suffix) {
        String email = "admin-" + suffix + "@example.com";
        users.saveAndFlush(new UserAccount(UUID.randomUUID(), email, passwords.encode("admin-secure-password-123"), "Test", "Admin", null, Role.ADMIN, null, true,true, crypto.encrypt(ADMIN_TOTP_SECRET), -1));
        return email;
    }

    private String adminLogin(String email, String code) {
        return "{\"email\":\"" + email + "\",\"password\":\"admin-secure-password-123\",\"mfaCode\":\"" + code + "\"}";
    }

    private String registration(String email, String name) {
        return """
            {"email":"%s","password":"a-secure-password-123","firstName":"Ada","lastName":"Lovelace",
             "phone":"+905551112233","displayName":"%s","address":"İstanbul, Türkiye",
             "googleMapsUrl":"https://maps.google.com/example"}
            """.formatted(email, name);
    }

    private String tokenFrom(String encryptedBody) {
        String body = crypto.decrypt(encryptedBody);
        return body.substring(body.indexOf("#token=") + 7).strip();
    }

    private TestSession session() throws Exception {
        var cookies = new CookieManager();
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var response = client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode token = json.readTree(response.body());
        return new TestSession(client, token.get("headerName").asText(), token.get("token").asText());
    }

    private HttpResponse<String> get(TestSession session, String path) throws Exception {
        return session.client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(TestSession session, String path, String body) throws Exception {
        return mutate(session, "POST", path, body);
    }

    private HttpResponse<String> patch(TestSession session, String path, String body) throws Exception {
        return mutate(session, "PATCH", path, body);
    }

    private HttpResponse<String> mutate(TestSession session, String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .header("Content-Type", "application/json").header(session.csrfHeader, session.csrfToken).build();
        var response = session.client.send(request, HttpResponse.BodyHandlers.ofString());
        // Authentication rotates the CSRF token. Refresh it for later mutations.
        if (path.equals("/api/auth/login") && response.statusCode() == 200) {
            var csrf = session.client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode token = json.readTree(csrf.body());
            session.csrfHeader = token.get("headerName").asText();
            session.csrfToken = token.get("token").asText();
        }
        return response;
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private static final class TestSession {
        final HttpClient client;
        String csrfHeader;
        String csrfToken;
        TestSession(HttpClient client, String csrfHeader, String csrfToken) {
            this.client = client; this.csrfHeader = csrfHeader; this.csrfToken = csrfToken;
        }
    }
}
