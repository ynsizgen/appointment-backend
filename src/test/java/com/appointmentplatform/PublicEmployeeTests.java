package com.appointmentplatform;

import com.appointmentplatform.brand.domain.Brand;
import com.appointmentplatform.brand.persistence.BrandRepository;
import com.appointmentplatform.branch.domain.Branch;
import com.appointmentplatform.branch.persistence.BranchRepository;
import com.appointmentplatform.employee.domain.Employee;
import com.appointmentplatform.employee.domain.EmployeeServiceAssignment;
import com.appointmentplatform.employee.persistence.EmployeeRepository;
import com.appointmentplatform.employee.persistence.EmployeeServiceAssignmentRepository;
import com.appointmentplatform.service.domain.BranchServiceAssignment;
import com.appointmentplatform.service.domain.BranchServiceAssignmentId;
import com.appointmentplatform.service.domain.Service;
import com.appointmentplatform.service.persistence.BranchServiceAssignmentRepository;
import com.appointmentplatform.service.persistence.ServicesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicEmployeeTests {
    @Autowired BrandRepository brands;
    @Autowired BranchRepository branches;
    @Autowired ServicesRepository services;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeServiceAssignmentRepository employeeAssignments;
    @Autowired BranchServiceAssignmentRepository branchAssignments;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    record Fixture(UUID brand, UUID branch, UUID service) {
    }

    private Fixture fixture() {
        UUID brand = brands.saveAndFlush(new Brand(UUID.randomUUID(), "Public employees")).id();
        UUID branch = branches.saveAndFlush(new Branch(UUID.randomUUID(), brand, "Branch", "Istanbul", null)).id();
        UUID service = services.saveAndFlush(new Service(UUID.randomUUID(), brand, "Cut", 30)).getId();
        branchAssignments.saveAndFlush(new BranchServiceAssignment(branch, service, brand));
        return new Fixture(brand, branch, service);
    }

    private UUID employee(UUID branch, UUID service, String firstName, boolean active) {
        UUID id = employees.saveAndFlush(new Employee(UUID.randomUUID(), branch, firstName, "Test", active)).getId();
        if (service != null) {
            employeeAssignments.saveAndFlush(new EmployeeServiceAssignment(UUID.randomUUID(), id, service));
        }
        return id;
    }

    @Test
    void anonymousRequestFiltersBranchServiceAndActiveStatusAndReturnsOrderedPublicFields() throws Exception {
        var f = fixture();
        UUID last = employee(f.branch(), f.service(), "Zeynep", true);
        UUID first = employee(f.branch(), f.service(), "Ada", true);
        employee(f.branch(), f.service(), "Inactive", false);
        employee(f.branch(), null, "Unassigned", true);
        UUID otherService = services.saveAndFlush(new Service(UUID.randomUUID(), f.brand(), "Color", 60)).getId();
        employee(f.branch(), otherService, "Other service", true);
        UUID otherBranch = branches.saveAndFlush(new Branch(UUID.randomUUID(), f.brand(), "Other branch", "Istanbul", null)).id();
        employee(otherBranch, f.service(), "Other branch", true);
        var foreign = fixture();
        employee(foreign.branch(), foreign.service(), "Foreign", true);

        var response = get(f.branch().toString(), f.service().toString());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        var ids = new ArrayList<String>();
        for (var e : body) {
            ids.add(e.get("id").asText());
            assertThat(e.size()).isEqualTo(5);
            assertThat(e.get("branchId").asText()).isEqualTo(f.branch().toString());
            assertThat(e.get("active").asBoolean()).isTrue();
            assertThat(e.get("firstName").isTextual()).isTrue();
            assertThat(e.get("lastName").asText()).isEqualTo("Test");
        }
        assertThat(ids).containsExactly(first.toString(), last.toString());
    }

    @Test
    void emptyListAndRemovedBranchServiceAreDifferent() throws Exception {
        var f = fixture();
        var empty = get(f.branch().toString(), f.service().toString());
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.body()).isEqualTo("[]");
        employee(f.branch(), f.service(), "Ada", true);
        branchAssignments.deleteById(new BranchServiceAssignmentId(f.branch(), f.service()));
        var removed = get(f.branch().toString(), f.service().toString());
        assertThat(removed.statusCode()).isEqualTo(400);
        assertThat(json.readTree(removed.body()).get("detail").asText()).isEqualTo("Bu hizmet seçilen şubede sunulmuyor.");
        branchAssignments.saveAndFlush(new BranchServiceAssignment(f.branch(), f.service(), f.brand()));
        assertThat(json.readTree(get(f.branch().toString(), f.service().toString()).body()).size()).isEqualTo(1);
    }

    @Test
    void invalidIdsAndCrossBrandServiceDoNotExposeEmployees() throws Exception {
        var f = fixture();
        var other = fixture();
        employee(other.branch(), other.service(), "Foreign", true);
        for (String[] ids : new String[][] {
                {f.branch().toString(), other.service().toString()},
                {UUID.randomUUID().toString(), f.service().toString()},
                {f.branch().toString(), UUID.randomUUID().toString()},
                {"invalid", f.service().toString()},
                {f.branch().toString(), "invalid"}
        }) {
            var response = get(ids[0], ids[1]);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain("Foreign");
        }
    }

    private HttpResponse<String> get(String branch, String service) throws Exception {
        var uri = URI.create("http://localhost:" + port + "/api/public/employee/branches/" + branch + "/services/" + service);
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
