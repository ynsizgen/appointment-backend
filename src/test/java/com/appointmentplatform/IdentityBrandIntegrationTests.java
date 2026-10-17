package com.appointmentplatform;

import com.appointmentplatform.appointment.AppointmentSource;
import com.appointmentplatform.appointment.AppointmentStatus;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import com.appointmentplatform.identity.domain.*;
import com.appointmentplatform.identity.persistence.*;
import com.appointmentplatform.identity.security.Totp;
import com.appointmentplatform.notification.persistence.EmailJobRepository;
import com.appointmentplatform.shared.Crypto;
import com.appointmentplatform.shared.internal.ratelimit.RateLimitRepository;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.appointmentplatform.notification.SmsSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
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
    @Autowired
    Totp totp;
    @Autowired
    Clock clock;
    @Autowired
    RateLimitRepository rateLimits;

    @BeforeEach
    void resetRateLimitsBetweenIndependentScenarios() {
        rateLimits.deleteAllInBatch();
    }

    @Test
    void registrationCreatesBrandThenOwnerCreatesBranchAndControlsAccess() throws Exception {
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
        assertThat(branches.size()).isZero();
        var created = post(owner, "/api/panel/branches", branchPayload());
        assertThat(created.statusCode()).isEqualTo(201);
        var branch = json.readTree(created.body());
        assertThat(json.readTree(get(owner, "/api/panel/branches").body()).size()).isEqualTo(1);
        String hoursPath = "/api/panel/branches/" + branch.get("id").asText() + "/working-hours";
        var initialHours = json.readTree(get(owner, hoursPath).body());
        assertThat(initialHours.size()).isEqualTo(2);
        assertThat(initialHours.get(0).get("dayOfWeek").asText()).isEqualTo("MONDAY");
        assertThat(initialHours.get(0).get("startTime").asText()).startsWith("08:00");
        assertThat(get(other, hoursPath).statusCode()).isEqualTo(404);
        assertThat(mutate(other, "PUT", hoursPath, "{\"workingHours\":[]}").statusCode()).isEqualTo(404);
        assertThat(mutate(owner, "PUT", hoursPath, "{\"workingHours\":[]}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, hoursPath).body()).size()).isZero();
        assertThat(mutate(owner, "PUT", hoursPath,
                "{\"workingHours\":[{\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"10:00\",\"endTime\":\"18:00\"}]}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, hoursPath).body()).size()).isEqualTo(1);
        assertThat(mutate(owner, "PUT", hoursPath,
                "{\"workingHours\":[{\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"18:00\",\"endTime\":\"10:00\"}]}").statusCode()).isEqualTo(400);
        assertThat(json.readTree(get(owner, hoursPath).body()).get(0).get("startTime").asText()).startsWith("10:00");
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
        assertThat(post(owner, "/api/panel/branches", "{}").statusCode()).isEqualTo(400);
        assertThat(post(owner, "/api/panel/branches", branchPayload().replace("13:00", "11:00")).statusCode()).isEqualTo(400);
        assertThat(post(owner, "/api/panel/branches", branchPayload().replace("https://maps.google.com/example", "http://maps.google.com/example")).statusCode()).isEqualTo(400);
        assertThat(json.readTree(get(owner, "/api/panel/branches").body()).size()).isEqualTo(1);

        var admin = createAndLoginAdmin(suffix);
        assertThat(post(admin, "/api/panel/branches", branchPayload()).statusCode()).isEqualTo(403);
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

    @Test
    void branchHoursValidateNestedFieldsAndRejectOverlapWithoutChangingSchedule() throws Exception {
        String email = "hours-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(email, "Hours Brand");
        var owner = loginBrand(email);
        String path = "/api/panel/branches";
        for (String invalid : List.of(
                branchPayload().replace("08:00", "12:00"),
                branchPayload().replace("MONDAY", "INVALID"),
                branchPayload().replace("\"startTime\":\"08:00\"", "\"startTime\":null"),
                "{\"name\":\"Merkez\",\"address\":\"Address\",\"workingHours\":[]}",
                "{\"name\":\"Merkez\",\"address\":\"Address\",\"workingHours\":[null]}")) {
            assertThat(post(owner, path, invalid).statusCode()).isEqualTo(400);
        }
        assertThat(json.readTree(get(owner, path).body()).size()).isZero();
        var created = post(owner, path, branchPayload());
        assertThat(created.statusCode()).isEqualTo(201);
        String hoursPath = path + "/" + json.readTree(created.body()).get("id").asText() + "/working-hours";
        assertThat(mutate(owner, "PUT", hoursPath, "{}").statusCode()).isEqualTo(400);
        String overlapping = """
            {"workingHours":[
              {"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"12:00"},
              {"dayOfWeek":"MONDAY","startTime":"11:00","endTime":"14:00"}]}
            """;
        assertThat(mutate(owner, "PUT", hoursPath, overlapping).statusCode()).isEqualTo(400);
        var unchanged = json.readTree(get(owner, hoursPath).body());
        assertThat(unchanged.size()).isEqualTo(2);
        assertThat(unchanged.get(0).get("startTime").asText()).startsWith("08:00");
    }

    @Test
    void brandServicesAreValidatedListedAndIsolated() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "service-owner-" + suffix + "@example.com";
        String otherEmail = "service-other-" + suffix + "@example.com";
        createVerifiedBrand(ownerEmail, "Service Brand");
        createVerifiedBrand(otherEmail, "Other Service Brand");
        var owner = loginBrand(ownerEmail);
        var other = loginBrand(otherEmail);
        String addPath = "/api/panel/addService";
        String listPath = "/api/panel/services";

        assertThat(json.readTree(get(owner, listPath).body()).size()).isZero();
        var blankName = post(owner, addPath, "{\"displayName\":\"   \",\"durationMinutes\":30}");
        assertThat(blankName.statusCode()).isEqualTo(400);
        assertThat(json.readTree(blankName.body()).get("errors").has("displayName")).isTrue();
        var longName = post(owner, addPath, "{\"displayName\":\"" + "x".repeat(201) + "\",\"durationMinutes\":30}");
        assertThat(longName.statusCode()).isEqualTo(400);
        assertThat(json.readTree(longName.body()).get("errors").has("displayName")).isTrue();
        var invalidDuration = post(owner, addPath, "{\"displayName\":\"Kesim\",\"durationMinutes\":0}");
        assertThat(invalidDuration.statusCode()).isEqualTo(400);
        assertThat(json.readTree(invalidDuration.body()).get("errors").has("durationMinutes")).isTrue();
        assertThat(json.readTree(get(owner, listPath).body()).size()).isZero();

        assertThat(post(owner, addPath, "{\"displayName\":\"  Saç Kesimi  \",\"durationMinutes\":30}").statusCode()).isEqualTo(202);
        assertThat(post(owner, addPath, "{\"displayName\":\"Saç Boyama\",\"durationMinutes\":60}").statusCode()).isEqualTo(202);
        var services = json.readTree(get(owner, listPath).body());
        assertThat(services.size()).isEqualTo(2);
        assertThat(List.of(services.get(0).get("displayName").asText(), services.get(1).get("displayName").asText()))
                .containsExactlyInAnyOrder("Saç Kesimi", "Saç Boyama");
        assertThat(List.of(services.get(0).get("durationMinutes").asInt(), services.get(1).get("durationMinutes").asInt()))
                .containsExactlyInAnyOrder(30, 60);
        String ownerBrandId = json.readTree(get(owner, "/api/panel/brand").body()).get("id").asText();
        assertThat(services.get(0).get("brandId").asText()).isEqualTo(ownerBrandId);
        assertThat(services.get(1).get("brandId").asText()).isEqualTo(ownerBrandId);
        assertThat(json.readTree(get(other, listPath).body()).size()).isZero();
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

    @Test
    void employeeSchedulesInheritOverrideCloseAndRespectBranchChanges() throws Exception {
        String email = "schedule-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(email, "Schedule Brand");
        var owner = loginBrand(email);
        var branchResponse = post(owner, "/api/panel/branches", branchPayload());
        assertThat(branchResponse.statusCode()).isEqualTo(201);
        String branchId = json.readTree(branchResponse.body()).get("id").asText();
        var employeeResponse = post(owner, "/api/panel/employee/" + branchId + "/employees",
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"active\":true}");
        assertThat(employeeResponse.statusCode()).isEqualTo(201);
        String employeeId = json.readTree(employeeResponse.body()).get("id").asText();
        String base = "/api/panel/employee/" + employeeId;
        String weeklyPath = base + "/working-hours";
        var date = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")))
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        String dailyPath = base + "/schedule/" + date;
        var inherited = json.readTree(get(owner, weeklyPath).body());
        assertThat(inherited.get("usesBranchHours").asBoolean()).isTrue();
        assertThat(inherited.get("workingHours").size()).isEqualTo(2);
        assertThat(json.readTree(get(owner, dailyPath).body()).get("workingHours").size()).isEqualTo(2);
        String custom = """
            {"workingHours":[{"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"11:00"}]}
            """;
        assertThat(mutate(owner, "PUT", weeklyPath, custom).statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, weeklyPath).body()).get("usesBranchHours").asBoolean()).isFalse();
        assertThat(json.readTree(get(owner, dailyPath).body()).get("workingHours").get(0).get("startTime").asText()).startsWith("09:00");
        assertThat(mutate(owner, "PUT", dailyPath, "{\"workingHours\":[]}").statusCode()).isEqualTo(204);
        var closed = json.readTree(get(owner, dailyPath).body());
        assertThat(closed.get("source").asText()).isEqualTo("DATE_OVERRIDE");
        assertThat(closed.get("workingHours").size()).isZero();
        String special = "{\"workingHours\":[{\"startTime\":\"14:00\",\"endTime\":\"16:00\"}]}";
        assertThat(mutate(owner, "PUT", dailyPath, special).statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, dailyPath).body()).get("workingHours").get(0).get("startTime").asText()).startsWith("14:00");
        assertThat(mutate(owner, "DELETE", dailyPath, "").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, dailyPath).body()).get("source").asText()).isEqualTo("EMPLOYEE_WEEKLY");
        // A branch change restricts effective employee hours without erasing the configured schedule.
        assertThat(mutate(owner, "PUT", "/api/panel/branches/" + branchId + "/working-hours",
                custom.replace("09:00", "10:00")).statusCode()).isEqualTo(204);
        var clipped = json.readTree(get(owner, dailyPath).body());
        assertThat(clipped.get("configuredHours").get(0).get("startTime").asText()).startsWith("09:00");
        assertThat(clipped.get("workingHours").get(0).get("startTime").asText()).startsWith("10:00");
        assertThat(mutate(owner, "PUT", weeklyPath, "{\"workingHours\":[],\"closedDays\":[\"MONDAY\"]}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, weeklyPath).body()).get("usesBranchHours").asBoolean()).isFalse();
        assertThat(json.readTree(get(owner, dailyPath).body()).get("workingHours").size()).isZero();
        assertThat(mutate(owner, "DELETE", weeklyPath, "").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, weeklyPath).body()).get("usesBranchHours").asBoolean()).isTrue();
        assertThat(patch(owner, base + "/status", "{\"active\":false}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, dailyPath).body()).get("workingHours").size()).isZero();
    }

    @Test
    void employeeScheduleRejectsForeignAccessPastDatesAndInvalidIntervals() throws Exception {
        String suffix = UUID.randomUUID().toString();
        createVerifiedBrand("hours-owner-" + suffix + "@example.com", "Owner");
        createVerifiedBrand("hours-other-" + suffix + "@example.com", "Other");
        var owner = loginBrand("hours-owner-" + suffix + "@example.com");
        var other = loginBrand("hours-other-" + suffix + "@example.com");
        String branchId = json.readTree(post(owner, "/api/panel/branches", branchPayload()).body()).get("id").asText();
        var employee = post(owner, "/api/panel/employee/" + branchId + "/employees",
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"active\":true}");
        String base = "/api/panel/employee/" + json.readTree(employee.body()).get("id").asText();
        String weeklyPath = base + "/working-hours";
        var today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")));
        String datePath = base + "/schedule/" + today;
        for (String path : List.of(weeklyPath, datePath)) {
            assertThat(get(other, path).statusCode()).isEqualTo(404);
            assertThat(mutate(other, "PUT", path, "{\"workingHours\":[]}").statusCode()).isEqualTo(404);
            assertThat(mutate(other, "DELETE", path, "").statusCode()).isEqualTo(404);
        }
        for (String invalid : List.of("{}", "{\"workingHours\":[null]}",
                "{\"workingHours\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"08:00\"}]}",
                "{\"workingHours\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"11:00\",\"endTime\":\"14:00\"}]}",
                "{\"workingHours\":[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}]}",
                "{\"workingHours\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"09:00\"}]}",
                "{\"workingHours\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"08:00\",\"endTime\":\"11:00\"},{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}]}")) {
            assertThat(mutate(owner, "PUT", weeklyPath, invalid).statusCode()).isEqualTo(400);
        }
        assertThat(json.readTree(get(owner, weeklyPath).body()).get("usesBranchHours").asBoolean()).isTrue();
        String past = base + "/schedule/" + today.minusDays(1);
        assertThat(mutate(owner, "PUT", past, "{\"workingHours\":[]}").statusCode()).isEqualTo(400);
        assertThat(mutate(owner, "DELETE", past, "").statusCode()).isEqualTo(400);
        assertThat(mutate(owner, "PUT", datePath, "{\"workingHours\":[]}").statusCode()).isEqualTo(204);
        assertThat(mutate(owner, "DELETE", datePath, "").statusCode()).isEqualTo(204);
        assertThat(mutate(owner, "PUT", datePath, "{\"workingHours\":[{\"startTime\":\"22:00\",\"endTime\":\"23:00\"}]}").statusCode()).isEqualTo(400);
    }

    @Test
    void branchDateOverridesControlEmployeesAndAreValidatedAndIsolated() throws Exception {
        String suffix = UUID.randomUUID().toString();
        createVerifiedBrand("branch-date-" + suffix + "@example.com", "Date Brand");
        createVerifiedBrand("branch-other-" + suffix + "@example.com", "Other");
        var owner = loginBrand("branch-date-" + suffix + "@example.com");
        var other = loginBrand("branch-other-" + suffix + "@example.com");
        String branchId = json.readTree(post(owner, "/api/panel/branches", branchPayload()).body()).get("id").asText();
        String employeeId = json.readTree(post(owner, "/api/panel/employee/" + branchId + "/employees",
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"active\":true}").body()).get("id").asText();
        var today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")));
        var monday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        String branchBase = "/api/panel/branches/" + branchId + "/schedule/";
        String path = branchBase + monday;
        String employeeBase = "/api/panel/employee/" + employeeId;
        String employeePath = employeeBase + "/schedule/" + monday;
        assertThat(json.readTree(get(owner, path).body()).get("dateOverride").asBoolean()).isFalse();
        String special = """
            {"workingHours":[{"startTime":"10:00","endTime":"12:00"},{"startTime":"16:00","endTime":"18:00"}]}
            """;
        assertThat(mutate(owner, "PUT", path, special).statusCode()).isEqualTo(204);
        var result = json.readTree(get(owner, path).body());
        assertThat(result.get("dateOverride").asBoolean()).isTrue();
        assertThat(result.get("workingHours").size()).isEqualTo(2);
        var employeeHours = json.readTree(get(owner, employeePath).body()).get("workingHours");
        assertThat(employeeHours).isEqualTo(result.get("workingHours"));
        assertThat(mutate(owner, "PUT", employeePath,
                "{\"workingHours\":[{\"startTime\":\"10:00\",\"endTime\":\"18:00\"}]}").statusCode()).isEqualTo(400);
        assertThat(mutate(owner, "PUT", employeePath, special).statusCode()).isEqualTo(204);
        assertThat(mutate(owner, "PUT", path, "{\"workingHours\":[]}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, employeePath).body()).get("workingHours").size()).isZero();
        assertThat(mutate(owner, "DELETE", path, "").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, path).body()).get("dateOverride").asBoolean()).isFalse();
        assertThat(json.readTree(get(owner, employeePath).body()).get("workingHours").size()).isEqualTo(2);
        // A date override can open a normally closed day; inheriting employees follow it.
        String tuesdayPath = branchBase + monday.plusDays(1);
        assertThat(json.readTree(get(owner, tuesdayPath).body()).get("workingHours").size()).isZero();
        assertThat(mutate(owner, "PUT", tuesdayPath, special).statusCode()).isEqualTo(204);
        String employeeTuesday = employeeBase + "/schedule/" + monday.plusDays(1);
        assertThat(json.readTree(get(owner, employeeTuesday).body()).get("workingHours").size()).isEqualTo(2);
        assertThat(mutate(owner, "PUT", employeeTuesday, special).statusCode()).isEqualTo(204);
        assertThat(mutate(owner, "DELETE", employeeTuesday, "").statusCode()).isEqualTo(204);
        assertThat(mutate(owner, "PUT", employeeBase + "/working-hours", "{\"workingHours\":[],\"closedDays\":[\"TUESDAY\"]}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, employeeTuesday).body()).get("workingHours").size()).isZero();
        assertThat(get(other, path).statusCode()).isEqualTo(404);
        assertThat(mutate(other, "PUT", path, special).statusCode()).isEqualTo(404);
        assertThat(mutate(other, "DELETE", path, "").statusCode()).isEqualTo(404);
        for (String invalid : List.of("{}", "{\"workingHours\":[null]}",
                special.replace("16:00", "11:00"), special.replace("10:00", "12:00"))) {
            assertThat(mutate(owner, "PUT", tuesdayPath, invalid).statusCode()).isEqualTo(400);
        }
        assertThat(json.readTree(get(owner, tuesdayPath).body()).get("workingHours").size()).isEqualTo(2);
        assertThat(mutate(owner, "PUT", branchBase + today.minusDays(1), special).statusCode()).isEqualTo(400);
        assertThat(mutate(owner, "DELETE", branchBase + today.minusDays(1), "").statusCode()).isEqualTo(400);
        assertThat(mutate(owner, "PUT", branchBase + today, "{\"workingHours\":[]}").statusCode()).isEqualTo(204);
    }

    @Test
    void availableSlotsEndpointChecksOwnershipAssignmentAndBranchClosure() throws Exception {
        String suffix = UUID.randomUUID().toString();
        createVerifiedBrand("slots-" + suffix + "@example.com", "Slots");
        createVerifiedBrand("slots-other-" + suffix + "@example.com", "Other");
        var owner = loginBrand("slots-" + suffix + "@example.com");
        var other = loginBrand("slots-other-" + suffix + "@example.com");
        String branchId = json.readTree(post(owner, "/api/panel/branches", branchPayload()).body()).get("id").asText();
        String employeeId = json.readTree(post(owner, "/api/panel/employee/" + branchId + "/employees",
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"active\":true}").body()).get("id").asText();
        assertThat(post(owner, "/api/panel/addService", "{\"displayName\":\"Kesim\",\"durationMinutes\":60}").statusCode()).isEqualTo(202);
        assertThat(post(other, "/api/panel/addService", "{\"displayName\":\"Other\",\"durationMinutes\":60}").statusCode()).isEqualTo(202);
        String serviceId = json.readTree(get(owner, "/api/panel/services").body()).get(0).get("id").asText();
        String foreignServiceId = json.readTree(get(other, "/api/panel/services").body()).get(0).get("id").asText();
        var date = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")))
                .plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        String base = "/api/panel/appointment/available-slot/";
        String path = base + serviceId + "/" + employeeId + "/" + date;
        assertThat(get(owner, path).statusCode()).isEqualTo(400); // Not assigned yet.
        assertThat(post(owner, "/api/panel/employee/assign",
                "{\"employeeId\":\"" + employeeId + "\",\"serviceId\":\"" + serviceId + "\"}").statusCode()).isEqualTo(200);
        var response = get(owner, path);
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body()).get("availableAppointmentSlotDetails");
        assertThat(body.get("employeeId").asText()).isEqualTo(employeeId);
        assertThat(body.get("serviceId").asText()).isEqualTo(serviceId);
        assertThat(body.get("localTimes").size()).isEqualTo(11);
        assertThat(body.get("localTimes").get(0).asText()).startsWith("08:00");
        assertThat(get(other, path).statusCode()).isEqualTo(404);
        assertThat(get(owner, base + foreignServiceId + "/" + employeeId + "/" + date).statusCode()).isEqualTo(404);
        assertThat(get(session(), path).statusCode()).isEqualTo(401);
        assertThat(patch(owner, "/api/panel/employee/" + employeeId + "/status", "{\"active\":false}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, path).body()).get("availableAppointmentSlotDetails").get("localTimes").size()).isZero();
        assertThat(patch(owner, "/api/panel/employee/" + employeeId + "/status", "{\"active\":true}").statusCode()).isEqualTo(204);
        assertThat(mutate(owner, "PUT", "/api/panel/branches/" + branchId + "/schedule/" + date, "{\"workingHours\":[]}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, path).body()).get("availableAppointmentSlotDetails").get("localTimes").size()).isZero();
    }

    @Test
    void missingEmployeeWeekdaysInheritBranchAndExplicitClosuresStayClosed() throws Exception {
        String email = "day-fallback-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(email, "Fallback");
        var owner = loginBrand(email);
        String branchId = json.readTree(post(owner, "/api/panel/branches", branchPayload()).body()).get("id").asText();
        String branchHoursPath = "/api/panel/branches/" + branchId + "/working-hours";
        String week = "{\"workingHours\":[" + Arrays.stream(DayOfWeek.values())
                .map(day -> "{\"dayOfWeek\":\"" + day + "\",\"startTime\":\"08:00\",\"endTime\":\"20:00\"}")
                .collect(Collectors.joining(",")) + "]}";
        assertThat(mutate(owner, "PUT", branchHoursPath, week).statusCode()).isEqualTo(204);
        String employeeId = json.readTree(post(owner, "/api/panel/employee/" + branchId + "/employees",
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"active\":true}").body()).get("id").asText();
        String base = "/api/panel/employee/" + employeeId;
        var monday = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")))
                .plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        String request = """
            {"workingHours":[{"dayOfWeek":"MONDAY","startTime":"10:00","endTime":"12:00"}],
             "closedDays":["WEDNESDAY"]}
            """;
        assertThat(mutate(owner, "PUT", base + "/working-hours", request).statusCode()).isEqualTo(204);
        var weeklyResult = json.readTree(get(owner, base + "/working-hours").body());
        assertThat(weeklyResult.get("workingHours").size()).isEqualTo(6);
        assertThat(weeklyResult.get("configuredHours").size()).isEqualTo(1);
        assertThat(weeklyResult.get("inheritedDays").toString()).contains("TUESDAY").doesNotContain("MONDAY", "WEDNESDAY");
        assertThat(weeklyResult.get("closedDays").get(0).asText()).isEqualTo("WEDNESDAY");
        String mondayPath = base + "/schedule/" + monday;
        String tuesdayPath = base + "/schedule/" + monday.plusDays(1);
        String wednesdayPath = base + "/schedule/" + monday.plusDays(2);
        assertThat(json.readTree(get(owner, mondayPath).body()).get("workingHours").get(0).get("startTime").asText()).startsWith("10:00");
        assertThat(json.readTree(get(owner, tuesdayPath).body()).get("source").asText()).isEqualTo("BRANCH");
        assertThat(json.readTree(get(owner, wednesdayPath).body()).get("workingHours").size()).isZero();
        String dateHours = "{\"workingHours\":[{\"startTime\":\"14:00\",\"endTime\":\"16:00\"}]}";
        // The omitted weekday uses the branch's date override, not merely its weekly hours.
        String branchTuesday = "/api/panel/branches/" + branchId + "/schedule/" + monday.plusDays(1);
        assertThat(mutate(owner, "PUT", branchTuesday, dateHours).statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, tuesdayPath).body()).get("workingHours").get(0).get("startTime").asText()).startsWith("14:00");
        assertThat(mutate(owner, "PUT", tuesdayPath, "{\"workingHours\":[]}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, tuesdayPath).body()).get("workingHours").size()).isZero();
        assertThat(mutate(owner, "DELETE", tuesdayPath, "").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, tuesdayPath).body()).get("source").asText()).isEqualTo("BRANCH");
        // A specific-date setting overrides even an explicitly closed weekly day.
        assertThat(mutate(owner, "PUT", wednesdayPath, dateHours).statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, wednesdayPath).body()).get("workingHours").size()).isEqualTo(1);
        assertThat(mutate(owner, "DELETE", wednesdayPath, "").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, wednesdayPath).body()).get("workingHours").size()).isZero();
        assertThat(mutate(owner, "PUT", base + "/working-hours", request.replace("WEDNESDAY", "MONDAY")).statusCode()).isEqualTo(400);
        assertThat(mutate(owner, "PUT", base + "/working-hours", "{\"workingHours\":[],\"closedDays\":[null]}").statusCode()).isEqualTo(400);
        // Empty employee customization means inheritance, unlike an empty specific-date override.
        assertThat(mutate(owner, "PUT", base + "/working-hours", "{\"workingHours\":[],\"closedDays\":[]}").statusCode()).isEqualTo(204);
        assertThat(json.readTree(get(owner, base + "/working-hours").body()).get("usesBranchHours").asBoolean()).isTrue();
        assertThat(json.readTree(get(owner, wednesdayPath).body()).get("workingHours").size()).isEqualTo(1);
    }

    @MockitoBean
    SmsSender smsSender;

    @Autowired
    AppointmentRepository appointments;

    @Test
    void appointmentCreationPersistsDatesSourceAndEnforcesBranchAndHours() throws Exception {
        String email = "booking-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(email, "Booking Brand");
        var owner = loginBrand(email);
        var customer = session();
        String branchId = json.readTree(post(owner, "/api/panel/branches", branchPayload()).body()).get("id").asText();
        String otherBranchId = json.readTree(post(owner, "/api/panel/branches", branchPayload()).body()).get("id").asText();
        String employeeId = json.readTree(post(owner, "/api/panel/employee/" + branchId + "/employees",
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"active\":true}").body()).get("id").asText();
        assertThat(post(owner, "/api/panel/addService", "{\"displayName\":\"Kesim\",\"durationMinutes\":60}").statusCode()).isEqualTo(202);
        String serviceId = json.readTree(get(owner, "/api/panel/services").body()).get(0).get("id").asText();
        assertThat(post(owner, "/api/panel/employee/assign",
                "{\"employeeId\":\"" + employeeId + "\",\"serviceId\":\"" + serviceId + "\"}").statusCode()).isEqualTo(200);
        var date = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")))
                .plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        String body = """
                {"employeeId":"%s","serviceId":"%s","date":"%s","startTime":"10:00",
                 "customerFirstName":"Ada","customerLastName":"Lovelace","customerPhone":"+905551112233"}
                """.formatted(employeeId, serviceId, date);
        String publicPath = "/api/public/appointment/create/" + branchId;
        assertThat(post(customer, publicPath, body).statusCode()).isEqualTo(403);
        verifyPhone(customer, "+905551112233");
        long countBefore = appointments.count();
        for (var caller : List.of(customer, owner)) {
            String path = caller == customer ? publicPath : "/api/panel/appointment/create";
            var denied = post(caller, path, body);
            assertThat(denied.statusCode()).as(denied.body()).isEqualTo(409);
            assertThat(denied.body()).contains("aboneliği aktif");
        }
        assertThat(get(owner, "/api/panel/brand").statusCode()).isEqualTo(200);
        var account = users.findByEmail(email).orElseThrow();
        account.changeStatus(true, true, clock.instant());
        users.saveAndFlush(account);

        for (var caller : List.of(customer, owner)) {
            String path = caller == customer ? publicPath : "/api/panel/appointment/create";
            var denied = post(caller, path, body);
            assertThat(denied.statusCode()).as(denied.body()).isEqualTo(409);
            assertThat(denied.body()).contains("yeni randevu kabul etmiyor");
        }
        assertThat(appointments.count()).isEqualTo(countBefore);
        assertThat(patch(owner, "/api/panel/branches/" + branchId + "/appointment-acceptance",
                "{\"acceptingAppointments\":true}").statusCode()).isEqualTo(200);
        assertThat(post(customer, "/api/public/appointment/create/" + otherBranchId, body).statusCode()).isEqualTo(400);
        assertThat(post(customer, publicPath, body.replace("10:00", "11:30")).statusCode()).isEqualTo(400);
        assertThat(post(customer, "/api/panel/appointment/create", body).statusCode()).isEqualTo(401);
        String pastBody = body.replace(date.toString(), date.minusWeeks(4).toString());
        assertThat(post(customer, publicPath, pastBody).statusCode()).isEqualTo(400);
        assertThat(post(customer, publicPath, body.replace("+905551112233", "+905551112234")).statusCode()).isEqualTo(403);
        assertThat(patch(owner, "/api/panel/brand/cancellation-policy",
                "{\"cancellationNoticeHours\":3}").statusCode()).isEqualTo(200);
        var created = post(customer, publicPath, body);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(post(customer, publicPath, body.replace("10:00", "15:00")).statusCode()).isEqualTo(403);
        var duplicate = post(owner, "/api/panel/appointment/create", body);
        assertThat(duplicate.statusCode()).as(duplicate.body()).isEqualTo(409);
        assertThat(duplicate.body()).contains("başka bir randevusu var");
        var result = json.readTree(created.body());
        var saved = appointments.findById(UUID.fromString(result.get("id").asText())).orElseThrow();
        assertThat(saved.getCancellationDeadline()).isEqualTo(saved.getStartsAt().minusSeconds(3 * 3600));
        assertThat(result.get("cancellationDeadline").asText()).isEqualTo(saved.getCancellationDeadline().toString());
        assertThat(saved.getSource()).isEqualTo(AppointmentSource.CUSTOMER);
        assertThat(saved.getCustomerEmail()).isNull();
        assertThat(saved.getStartsAt()).isEqualTo(date.atTime(10, 0).atZone(ZoneId.of("Europe/Istanbul")).toInstant());
        assertThat(saved.getEndsAt()).isEqualTo(saved.getStartsAt().plusSeconds(3600));
        assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        // Real PostgreSQL query: excluded statuses must not block availability.
        var pending = AppointmentStatus.PENDING;
        var approved = AppointmentStatus.APPROVED;
        var employeeUuid = UUID.fromString(employeeId);
        assertThat(appointments.findBlockingInRange(employeeUuid, saved.getStartsAt(), saved.getEndsAt(), List.of(approved))).isEmpty();
        assertThat(appointments.findBlockingInRange(employeeUuid, saved.getStartsAt(), saved.getEndsAt(), List.of(pending, approved)))
                .extracting(a -> a.getId()).containsExactly(saved.getId());
        saved.setStatus(approved);
        appointments.saveAndFlush(saved);
        assertThat(appointments.findBlockingInRange(employeeUuid, saved.getStartsAt(), saved.getEndsAt(), List.of(pending))).isEmpty();
        assertThat(appointments.findBlockingInRange(employeeUuid, saved.getStartsAt(), saved.getEndsAt(), List.of(pending, approved)))
                .extracting(a -> a.getId()).containsExactly(saved.getId());

        assertThat(patch(owner, "/api/panel/brand/cancellation-policy",
                "{\"cancellationNoticeHours\":5}").statusCode()).isEqualTo(200);
        assertThat(appointments.findById(saved.getId()).orElseThrow().getCancellationDeadline())
                .isEqualTo(saved.getStartsAt().minusSeconds(3 * 3600));
        var brandCreated = post(owner, "/api/panel/appointment/create", pastBody);
        assertThat(brandCreated.statusCode()).as(brandCreated.body()).isEqualTo(201);
        var brandSaved = appointments.findById(UUID.fromString(json.readTree(brandCreated.body()).get("id").asText())).orElseThrow();
        assertThat(brandSaved.getSource()).isEqualTo(AppointmentSource.BRAND);
        assertThat(brandSaved.getCancellationDeadline()).isEqualTo(brandSaved.getStartsAt().minusSeconds(5 * 3600));
        verifyPhone(customer, "+905551112235");
        // An open branch alone is insufficient; disabling subscription preserves old bookings and panel access.
        account.changeStatus(true, false, clock.instant());
        users.saveAndFlush(account);
        for (var caller : List.of(customer, owner)) {
            String path = caller == customer ? publicPath : "/api/panel/appointment/create";
            var denied = post(caller, path, body.replace("10:00", "15:00").replace("+905551112233", "+905551112235"));
            assertThat(denied.statusCode()).as(denied.body()).isEqualTo(409);
            assertThat(denied.body()).contains("aboneliği aktif");
        }
        assertThat(get(owner, "/api/panel/brand").statusCode()).isEqualTo(200);
        assertThat(appointments.findById(saved.getId())).isPresent();
        assertThat(appointments.findById(brandSaved.getId())).isPresent();

    }

    @Test
    void appointmentDecisionsCheckOwnershipStatusAndConcurrentChanges() throws Exception {
        String email = "decisions-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(email, "Decisions");
        var owner = loginBrand(email);
        String otherEmail = "other-decisions-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(otherEmail, "Other Decisions");
        var other = loginBrand(otherEmail);
        var anonymous = session();

        var account = users.findByEmail(email).orElseThrow();
        account.changeStatus(true, true, clock.instant());
        users.saveAndFlush(account);
        String branchId = json.readTree(post(owner, "/api/panel/branches", branchPayload()).body()).get("id").asText();
        assertThat(patch(owner, "/api/panel/branches/" + branchId + "/appointment-acceptance",
                "{\"acceptingAppointments\":true}").statusCode()).isEqualTo(200);
        String employeeId = json.readTree(post(owner, "/api/panel/employee/" + branchId + "/employees",
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"active\":true}").body()).get("id").asText();
        assertThat(post(owner, "/api/panel/addService",
                "{\"displayName\":\"Kesim\",\"durationMinutes\":60}").statusCode()).isEqualTo(202);
        String serviceId = json.readTree(get(owner, "/api/panel/services").body()).get(0).get("id").asText();
        assertThat(post(owner, "/api/panel/employee/assign",
                "{\"employeeId\":\"" + employeeId + "\",\"serviceId\":\"" + serviceId + "\"}").statusCode()).isEqualTo(200);
        var date = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")))
                .plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        String base = "/api/panel/appointment/";

        for (int scenario = 0; scenario < 4; scenario++) {
            String body = """
                    {"employeeId":"%s","serviceId":"%s","date":"%s","startTime":"10:00",
                     "customerFirstName":"Ada","customerLastName":"Lovelace","customerPhone":"+905551112233"}
                    """.formatted(employeeId, serviceId, date.plusWeeks(scenario));
            var created = post(owner, base + "create", body);
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
            UUID id = UUID.fromString(json.readTree(created.body()).get("id").asText());
            String approve = base + "approve-appointment/" + id;
            String reject = base + "reject-appointment/" + id;
            String cancel = base + "cancel-appointment/" + id;

            for (String path : List.of(approve, reject, cancel)) {
                assertThat(post(anonymous, path, "").statusCode()).isEqualTo(401);
                assertThat(post(other, path, "").statusCode()).isEqualTo(404);
            }
            assertThat(appointments.findById(id).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);

            if (scenario < 2) {
                var response = post(owner, scenario == 0 ? approve : reject, "");
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(response.body()).isEmpty();
                assertThat(appointments.findById(id).orElseThrow().getStatus())
                        .isEqualTo(scenario == 0 ? AppointmentStatus.APPROVED : AppointmentStatus.REJECTED);
            } else if (scenario == 2) {
                var barrier = new CyclicBarrier(2);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var approval = executor.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return post(owner, approve, "").statusCode();
                    });
                    var rejection = executor.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return post(owner, reject, "").statusCode();
                    });
                    int approvalStatus = approval.get(20, TimeUnit.SECONDS);
                    int rejectionStatus = rejection.get(20, TimeUnit.SECONDS);
                    assertThat(List.of(approvalStatus, rejectionStatus)).containsExactlyInAnyOrder(200, 409);
                    assertThat(appointments.findById(id).orElseThrow().getStatus())
                            .isEqualTo(approvalStatus == 200 ? AppointmentStatus.APPROVED : AppointmentStatus.REJECTED);
                }
            }

            var beforeCancellation = appointments.findById(id).orElseThrow();
            if (beforeCancellation.getStatus() == AppointmentStatus.REJECTED) {
                assertThat(post(owner, cancel, "").statusCode()).isEqualTo(409);
                assertThat(appointments.findById(id).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.REJECTED);
            } else {
                var barrier = new CyclicBarrier(2);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return post(owner, cancel, "").statusCode();
                    });
                    var second = executor.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return post(owner, cancel, "").statusCode();
                    });
                    assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                            .containsExactlyInAnyOrder(200, 409);
                }
                assertThat(appointments.findById(id).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
                assertThat(appointments.findBlockingInRange(UUID.fromString(employeeId),
                        beforeCancellation.getStartsAt(), beforeCancellation.getEndsAt(),
                        List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED))).isEmpty();
                var replacement = post(owner, base + "create", body);
                assertThat(replacement.statusCode()).as(replacement.body()).isEqualTo(201);
                UUID replacementId = UUID.fromString(json.readTree(replacement.body()).get("id").asText());
                assertThat(post(owner, cancel, "").statusCode()).isEqualTo(409);
                assertThat(appointments.findById(replacementId).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
            }
            assertThat(post(other, cancel, "").statusCode()).isEqualTo(404);

            for (String path : List.of(approve, reject)) {
                var conflict = post(owner, path, "");
                assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);
                assertThat(conflict.body()).contains("artık onay beklemiyor");
                assertThat(post(other, path, "").statusCode()).isEqualTo(404);
            }
        }
        for (String action : List.of("approve-appointment/", "reject-appointment/", "cancel-appointment/")) {
            assertThat(post(owner, base + action + UUID.randomUUID(), "").statusCode()).isEqualTo(404);
        }
    }

    @Test
    void cancellationPolicyValidatesInputAndOnlyUpdatesLoggedInBrand() throws Exception {
        String email = "policy-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(email, "Policy Brand");
        var owner = loginBrand(email);
        String otherEmail = "policy-other-" + UUID.randomUUID() + "@example.com";
        createVerifiedBrand(otherEmail, "Other Policy Brand");
        var other = loginBrand(otherEmail);
        String path = "/api/panel/brand/cancellation-policy";
        assertThat(json.readTree(get(owner, "/api/panel/brand").body()).get("cancellationNoticeHours").asInt()).isZero();
        assertThat(patch(session(), path, "{\"cancellationNoticeHours\":3}").statusCode()).isEqualTo(401);
        for (String body : List.of("{}", "{\"cancellationNoticeHours\":null}",
                "{\"cancellationNoticeHours\":-1}", "{\"cancellationNoticeHours\":169}")) {
            assertThat(patch(owner, path, body).statusCode()).as(body).isEqualTo(400);
        }
        for (int hours : List.of(168, 3, 0)) {
            var result = patch(owner, path, "{\"cancellationNoticeHours\":" + hours + "}");
            assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
            assertThat(json.readTree(result.body()).get("cancellationNoticeHours").asInt()).isEqualTo(hours);
            assertThat(json.readTree(get(owner, "/api/panel/brand").body()).get("cancellationNoticeHours").asInt()).isEqualTo(hours);
            assertThat(json.readTree(get(other, "/api/panel/brand").body()).get("cancellationNoticeHours").asInt()).isZero();
        }
    }

    private void verifyPhone(TestSession session, String phone) throws Exception {
        var requested = post(session, "/api/public/phone-verifications/request", "{\"phone\":\"" + phone + "\"}");
        assertThat(requested.statusCode()).as(requested.body()).isEqualTo(204);
        var code = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendVerificationCode(eq(phone), code.capture());
        var verified = post(session, "/api/public/phone-verifications/verify",
                "{\"phone\":\"" + phone + "\",\"code\":\"" + code.getValue() + "\"}");
        assertThat(verified.statusCode()).as(verified.body()).isEqualTo(200);
        session.phoneToken = json.readTree(verified.body()).get("verificationToken").asText();
    }

    private String branchPayload() {
        return """
            {"name":"Merkez","address":"İstanbul, Türkiye","googleMapsUrl":"https://maps.google.com/example",
             "workingHours":[
               {"dayOfWeek":"MONDAY","startTime":"08:00","endTime":"12:00"},
               {"dayOfWeek":"MONDAY","startTime":"13:00","endTime":"20:00"}]}
            """;
    }

    private String registration(String email, String name) {
        return """
            {"email":"%s","password":"a-secure-password-123","firstName":"Ada","lastName":"Lovelace",
             "phone":"+905551112233","displayName":"%s"}
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
            .header("Content-Type", "application/json")
            .header(session.csrfHeader, session.csrfToken)
            .header("X-Phone-Verification", session.phoneToken)
            .build();
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

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static final class TestSession {
        final HttpClient client;
        String csrfHeader;
        String csrfToken;
        String phoneToken = "";

        TestSession(HttpClient client, String csrfHeader, String csrfToken) {
            this.client = client;
            this.csrfHeader = csrfHeader;
            this.csrfToken = csrfToken;
        }
    }
}
