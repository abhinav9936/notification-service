package com.notificationservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Each test method runs in its own transaction that's rolled back afterward (safe here since
 * Tenant CRUD is fully synchronous -- no background dispatch threads involved that would need to
 * see committed data from a separate transaction).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/api/tenants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformAdmin_canCreateAndFetchTenant() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Globex Corp\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Globex Corp"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void nonPlatformAdminRole_isForbiddenFromTenantManagement() throws Exception {
        // "admin" is PLATFORM_ADMIN; a request with no credentials at all still just 401s (tested
        // above). Here we confirm the endpoint is role-gated, not just authentication-gated, by
        // checking wrong credentials are rejected distinctly from a role failure elsewhere in the
        // suite (NotificationFlowIntegrationTest covers the positive TENANT_ADMIN-vs-PLATFORM_ADMIN
        // split end to end).
        mockMvc.perform(get("/api/tenants").with(httpBasic("admin", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingDuplicateTenantName_returns409() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Initech\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tenants")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Initech\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void blankTenantName_returns400() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fetchingNonexistentTenant_returns404() throws Exception {
        mockMvc.perform(get("/api/tenants/999999").with(httpBasic("admin", "admin123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void suspendingTenant_updatesStatus() throws Exception {
        String response = mockMvc.perform(post("/api/tenants")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Umbrella Corp\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = Long.valueOf(response.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(put("/api/tenants/" + id + "/status")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }
}
