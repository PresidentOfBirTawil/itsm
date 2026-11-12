package com.itsm;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each role sees the right incidents on its pages: an employee the ones they
 * reported, a technician the ones assigned to them, the administrator all of them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IncidentPagesTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private SlaPolicyRepository slaPolicies;

    private String assignedCode;
    private String unassignedCode;

    /** emp1 reports two incidents: one assigned to tech1 with a priority, one still new. */
    @BeforeEach
    void createIncidents() {
        Employee reporter = (Employee) users.findByEmail("emp1@itsm.local").orElseThrow();
        Technician technician = (Technician) users.findByEmail("tech1@itsm.local").orElseThrow();

        Incident assigned = new Incident("Cannot connect to the VPN", "The VPN client times out.", reporter,
                services.findAllByOrderByCriticalityDescNameAsc().getFirst(), Impact.HIGH, Urgency.HIGH);
        assigned.setCategory(categories.findByNameIgnoreCase("Network").orElseThrow());
        assigned.applySla(slaPolicies.findByPriority(Priority.P1).orElseThrow());
        assigned.setAssignee(technician);
        assigned.setStatus(Status.ASSIGNED);
        assignedCode = incidents.save(assigned).getCode();

        Incident unassigned = new Incident("Shared drive is almost full", "The team folder is 98% used.", reporter,
                services.findAllByOrderByCriticalityDescNameAsc().getLast(), Impact.LOW, Urgency.LOW);
        unassignedCode = incidents.save(unassigned).getCode();
    }

    @Test
    void employeeSeesTheIncidentsTheyReported() throws Exception {
        MockHttpSession reporter = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);

        for (String page : new String[] {"/employee/dashboard", "/employee/incidents"}) {
            mvc.perform(get(page).session(reporter))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(assignedCode)))
                    .andExpect(content().string(containsString(unassignedCode)))
                    .andExpect(content().string(containsString("P1 · Critical")));
        }
    }

    @Test
    void anotherEmployeeDoesNotSeeThem() throws Exception {
        MockHttpSession someoneElse = signIn("emp2@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);

        mvc.perform(get("/employee/incidents").session(someoneElse))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(assignedCode))))
                .andExpect(content().string(containsString("You have not reported any incidents yet.")));
    }

    @Test
    void technicianSeesOnlyTheIncidentsAssignedToThem() throws Exception {
        MockHttpSession assignee = signIn("tech1@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);

        for (String page : new String[] {"/technician/dashboard", "/technician/incidents"}) {
            mvc.perform(get(page).session(assignee))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(assignedCode)))
                    .andExpect(content().string(not(containsString(unassignedCode))));
        }

        MockHttpSession otherTechnician = signIn("tech2@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);
        mvc.perform(get("/technician/incidents").session(otherTechnician))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(assignedCode))));
    }

    @Test
    void administratorSeesEveryIncident() throws Exception {
        MockHttpSession admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/admin/incidents").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(assignedCode)))
                .andExpect(content().string(containsString(unassignedCode)))
                .andExpect(content().string(containsString("Ravi Kumar")))
                .andExpect(content().string(containsString("Priya Sharma")));
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
    }
}
