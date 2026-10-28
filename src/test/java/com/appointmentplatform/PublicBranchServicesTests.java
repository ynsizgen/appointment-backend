package com.appointmentplatform;

import com.appointmentplatform.brand.domain.Brand;
import com.appointmentplatform.brand.persistence.BrandRepository;
import com.appointmentplatform.branch.domain.Branch;
import com.appointmentplatform.branch.persistence.BranchRepository;
import com.appointmentplatform.service.domain.Service;
import com.appointmentplatform.service.persistence.ServicesRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicBranchServicesTests {
    @Autowired BrandRepository brands;
    @Autowired BranchRepository branches;
    @Autowired ServicesRepository services;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @Test
    void anonymousListingIncludesOnlyAssignedServicesAndHandlesEmptyAndUnknownBranches() throws Exception {
        UUID brand = brands.saveAndFlush(new Brand(UUID.randomUUID(), "Public services test")).id();
        UUID first = branch(brand);
        UUID second = branch(brand);
        UUID empty = branch(brand);
        UUID cut = service(brand, "Cut", 30);
        UUID color = service(brand, "Color", 90);
        service(brand, "Unassigned", 60);
        assign(first, cut, brand);
        assign(second, color, brand);

        var response = get(first);
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).size()).isEqualTo(3);
        assertThat(body.get(0).get("id").asText()).isEqualTo(cut.toString());
        assertThat(body.get(0).get("displayName").asText()).isEqualTo("Cut");
        assertThat(body.get(0).get("durationMinutes").asInt()).isEqualTo(30);
        assertThat(json.readTree(get(second).body()).get(0).get("id").asText()).isEqualTo(color.toString());
        assertThat(get(empty).statusCode()).isEqualTo(200);
        assertThat(get(empty).body()).isEqualTo("[]");
        assertThat(get(UUID.randomUUID()).statusCode()).isEqualTo(404);
    }

    @Test
    void databaseRejectsDuplicateAndCrossBrandAssignments() {
        UUID brand = brands.saveAndFlush(new Brand(UUID.randomUUID(), "Owner")).id();
        UUID other = brands.saveAndFlush(new Brand(UUID.randomUUID(), "Other")).id();
        UUID branch = branch(brand);
        UUID service = service(brand, "Cut", 30);
        UUID foreign = service(other, "Foreign", 30);
        assign(branch, service, brand);
        assertThatThrownBy(() -> assign(branch, service, brand)).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> assign(branch, foreign, brand)).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> assign(branch, foreign, other)).isInstanceOf(PersistenceException.class);
    }

    private UUID branch(UUID brand) {
        return branches.saveAndFlush(new Branch(UUID.randomUUID(), brand, "Branch", "Istanbul", null)).id();
    }

    private UUID service(UUID brand, String name, int duration) {
        return services.saveAndFlush(new Service(UUID.randomUUID(), brand, name, duration)).getId();
    }

    private void assign(UUID branch, UUID service, UUID brand) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    INSERT INTO appointment.branch_services (branch_id, service_id, brand_id)
                    VALUES (:branch, :service, :brand)
                    """)
                    .setParameter("branch", branch)
                    .setParameter("service", service)
                    .setParameter("brand", brand)
                    .executeUpdate();
        });
    }

    private HttpResponse<String> get(UUID branch) throws Exception {
        var uri = URI.create("http://localhost:" + port + "/api/public/branches/" + branch + "/services");
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
