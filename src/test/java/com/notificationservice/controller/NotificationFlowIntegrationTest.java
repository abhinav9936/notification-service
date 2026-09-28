package com.notificationservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full send -> dispatch -> deliver pipeline against the REAL scheduler and worker
 * pools (no shortcuts / no @Transactional rollback), because the whole point is to prove the
 * asynchronous claim-and-dispatch path actually works end to end, the same way it does in
 * production. Not wrapped in a transaction: the background dispatch thread runs in its own
 * transaction and must be able to see this test's committed rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sendNotification_getsDispatchedAndMarkedSent() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long tenantId = createTenant("Flow Test Tenant " + suffix);
        String tenantAdminUser = "flow_admin_" + suffix;
        createTenantAdmin(tenantId, tenantAdminUser, "flowpass123");

        long templateId = createTemplate(tenantId, tenantAdminUser, "welcome_" + suffix,
                "Hi {{firstName}}", "Welcome {{firstName}} to {{company}}!");
        configureEmailChannel(tenantId, tenantAdminUser);

        String idempotencyKey = "flow-key-" + suffix;
        long notificationId = sendNotification(tenantId, tenantAdminUser, templateId,
                "grace@example.com", idempotencyKey);

        String finalStatus = waitForTerminalStatus(tenantId, tenantAdminUser, notificationId, 8000);
        assertThat(finalStatus).isEqualTo("SENT");
    }

    @Test
    void resendingWithSameIdempotencyKey_doesNotCreateASecondNotification() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long tenantId = createTenant("Idempotency Tenant " + suffix);
        String tenantAdminUser = "idem_admin_" + suffix;
        createTenantAdmin(tenantId, tenantAdminUser, "idempass123");

        long templateId = createTemplate(tenantId, tenantAdminUser, "idem_template_" + suffix,
                null, "Hello {{name}}");
        configureEmailChannel(tenantId, tenantAdminUser);

        String idempotencyKey = "idem-key-" + suffix;
        long firstId = sendNotification(tenantId, tenantAdminUser, templateId, "x@example.com", idempotencyKey);
        long secondId = sendNotification(tenantId, tenantAdminUser, templateId, "x@example.com", idempotencyKey);

        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void missingTemplateVariable_returns400() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long tenantId = createTenant("Validation Tenant " + suffix);
        String tenantAdminUser = "val_admin_" + suffix;
        createTenantAdmin(tenantId, tenantAdminUser, "valpass123");

        long templateId = createTemplate(tenantId, tenantAdminUser, "val_template_" + suffix,
                null, "Hello {{name}}, from {{sender}}");
        configureEmailChannel(tenantId, tenantAdminUser);

        mockMvc.perform(post("/api/tenants/" + tenantId + "/notifications")
                        .with(httpBasic(tenantAdminUser, "valpass123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":" + templateId + ",\"recipient\":\"x@example.com\"," +
                                "\"variables\":{\"name\":\"Ada\"},\"idempotencyKey\":\"val-key-" + suffix + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantAdmin_cannotAccessAnotherTenantsTemplates() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long tenantA = createTenant("Tenant A " + suffix);
        long tenantB = createTenant("Tenant B " + suffix);
        String tenantAAdmin = "tenantA_admin_" + suffix;
        createTenantAdmin(tenantA, tenantAAdmin, "passA1234");
        createTenantAdmin(tenantB, "tenantB_admin_" + suffix, "passB1234");

        mockMvc.perform(get("/api/tenants/" + tenantB + "/templates")
                        .with(httpBasic(tenantAAdmin, "passA1234")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantAdmin_cannotManagePlatformTenants() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long tenantId = createTenant("Role Test Tenant " + suffix);
        String tenantAdminUser = "role_admin_" + suffix;
        createTenantAdmin(tenantId, tenantAdminUser, "rolepass123");

        mockMvc.perform(post("/api/tenants")
                        .with(httpBasic(tenantAdminUser, "rolepass123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Should Not Be Created\"}"))
                .andExpect(status().isForbidden());
    }

    // --- helpers -----------------------------------------------------------------------------

    private long createTenant(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLongField(result.getResponse().getContentAsString(), "id");
    }

    private void createTenantAdmin(long tenantId, String username, String password) throws Exception {
        mockMvc.perform(post("/api/tenants/" + tenantId + "/admins")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
    }

    private long createTemplate(long tenantId, String tenantAdminUser, String name, String subject, String body) throws Exception {
        String subjectJson = subject == null ? "null" : "\"" + subject + "\"";
        MvcResult result = mockMvc.perform(post("/api/tenants/" + tenantId + "/templates")
                        .with(httpBasic(tenantAdminUser, passwordFor(tenantAdminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"channel\":\"EMAIL\",\"subject\":" + subjectJson +
                                ",\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLongField(result.getResponse().getContentAsString(), "id");
    }

    private void configureEmailChannel(long tenantId, String tenantAdminUser) throws Exception {
        mockMvc.perform(put("/api/tenants/" + tenantId + "/channels/EMAIL")
                        .with(httpBasic(tenantAdminUser, passwordFor(tenantAdminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"providerName\":\"test-provider\"}"))
                .andExpect(status().isOk());
    }

    private long sendNotification(long tenantId, String tenantAdminUser, long templateId, String recipient, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants/" + tenantId + "/notifications")
                        .with(httpBasic(tenantAdminUser, passwordFor(tenantAdminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":" + templateId + ",\"recipient\":\"" + recipient + "\"," +
                                "\"variables\":{\"firstName\":\"Grace\",\"company\":\"Acme\",\"name\":\"Grace\"}," +
                                "\"idempotencyKey\":\"" + idempotencyKey + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractLongField(result.getResponse().getContentAsString(), "id");
    }

    private String waitForTerminalStatus(long tenantId, String tenantAdminUser, long notificationId, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String lastStatus = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/tenants/" + tenantId + "/notifications/" + notificationId)
                            .with(httpBasic(tenantAdminUser, passwordFor(tenantAdminUser))))
                    .andExpect(status().isOk())
                    .andReturn();
            lastStatus = extractStringField(result.getResponse().getContentAsString(), "status");
            if ("SENT".equals(lastStatus) || "DEAD".equals(lastStatus)) {
                return lastStatus;
            }
            Thread.sleep(300);
        }
        return lastStatus;
    }

    // Tests only ever create one password per username in this class; map it back by convention
    // instead of threading extra parameters through every helper.
    private String passwordFor(String username) {
        if (username.startsWith("flow_admin_")) return "flowpass123";
        if (username.startsWith("idem_admin_")) return "idempass123";
        if (username.startsWith("val_admin_")) return "valpass123";
        if (username.startsWith("tenantA_admin_")) return "passA1234";
        if (username.startsWith("tenantB_admin_")) return "passB1234";
        if (username.startsWith("role_admin_")) return "rolepass123";
        throw new IllegalArgumentException("Unknown test user: " + username);
    }

    private static long extractLongField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Field " + field + " not found in: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String extractStringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Field " + field + " not found in: " + json);
        }
        return matcher.group(1);
    }
}
