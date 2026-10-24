package com.appointmentplatform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.flywaydb.core.Flyway;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppointmentPlatformApplicationTests {
    @LocalServerPort
    int port;
    @Autowired
    Flyway flyway;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void migrationsRunAgainstRealPostgres() {
        assertThat(flyway.info().applied()).hasSize(24);
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("24");
        flyway.validate();
    }

    @Test
    void healthIsPublicWithoutDatabaseDetails() throws Exception {
        var result = get("/actuator/health");
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains("UP").doesNotContain("components", "jdbc", "PostgreSQL");
    }

    @Test
    void businessAndManagementPathsAreClosedByDefault() throws Exception {
        for (String path : new String[]{"/api/appointments", "/admin", "/actuator/env"}) {
            var result = get(path);
            assertThat(result.statusCode()).isEqualTo(401);
            assertThat(result.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
            assertThat(result.body()).doesNotContain("exception", "trace");
        }
    }

    @Test
    void mutationWithoutCsrfTokenIsRejected() throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/appointments"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json").build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void otpEndpointsRequireCsrfEvenWithoutLogin() throws Exception {
        for (String path : new String[]{"/api/public/phone-verifications/request", "/api/public/phone-verifications/verify"}) {
            var request = HttpRequest.newBuilder(uri(path))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .header("Content-Type", "application/json")
                    .build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
