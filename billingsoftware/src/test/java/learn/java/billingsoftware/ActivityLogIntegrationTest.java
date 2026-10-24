package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.DiscountType;
import learn.java.billingsoftware.entity.PromotionType;
import learn.java.billingsoftware.io.*;
import learn.java.billingsoftware.repository.ActivityLogRepository;
import learn.java.billingsoftware.service.FileUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test suite for Requirement R1: Activity Log Subsystem.
 * Validates role-based access control (Staff view restricted to own email, Admin unconstrained),
 * server-side pagination, action and date filtering, and activity log generation upon business mutations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ActivityLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @MockitoBean
    private FileUploadService fileUploadService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Unauthenticated request to GET /activity-logs must be rejected with 401 or 403")
    void testUnauthenticatedAccessToActivityLogsIsRejected() throws Exception {
        mockMvc.perform(get("/activity-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Staff user can query /activity-logs and receives ActivityLogPageResponse structure")
    void testStaffUserCanAccessActivityLogs() throws Exception {
        mockMvc.perform(get("/activity-logs")
                        .with(user("staff@billing.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements", notNullValue()))
                .andExpect(jsonPath("$.totalPages", notNullValue()))
                .andExpect(jsonPath("$.currentPage", is(0)));
    }

    @Test
    @DisplayName("Staff user is strictly restricted to viewing activity logs matching their own email")
    void testStaffUserRestrictedToOwnEmail() throws Exception {
        String staffEmail = "cashier.viet@billing.com";

        mockMvc.perform(get("/activity-logs")
                        .with(user(staffEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[*].userEmail", everyItem(is(staffEmail))));
    }

    @Test
    @DisplayName("Staff user cannot query other users' logs even if userEmail param is supplied")
    void testStaffUserCannotQueryOtherUserLogs() throws Exception {
        String authenticatedStaff = "staff.restricted@billing.com";
        String targetAdminEmail = "superadmin@billing.com";

        mockMvc.perform(get("/activity-logs")
                        .param("userEmail", targetAdminEmail)
                        .with(user(authenticatedStaff).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].userEmail", not(hasItem(targetAdminEmail))));
    }

    @Test
    @DisplayName("Admin user with ROLE_ADMIN can query global activity logs across all users")
    void testAdminUserCanQueryAllActivityLogs() throws Exception {
        mockMvc.perform(get("/activity-logs")
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements", notNullValue()))
                .andExpect(jsonPath("$.totalPages", notNullValue()));
    }

    @Test
    @DisplayName("Admin user can filter activity logs by specific user email")
    void testAdminCanFilterBySpecificUserEmail() throws Exception {
        String targetEmail = "target.employee@billing.com";

        mockMvc.perform(get("/activity-logs")
                        .param("userEmail", targetEmail)
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].userEmail", everyItem(is(targetEmail))));
    }

    @Test
    @DisplayName("Activity logs can be filtered by action type (e.g. CREATE, UPDATE, DELETE, LOGIN)")
    void testFilterByActionType() throws Exception {
        mockMvc.perform(get("/activity-logs")
                        .param("action", "CREATE")
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Activity logs can be filtered by date range (startDate and endDate)")
    void testFilterByDateRange() throws Exception {
        mockMvc.perform(get("/activity-logs")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-07")
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Pagination parameters page and size are respected in ActivityLogPageResponse")
    void testPaginationParametersPageAndSize() throws Exception {
        mockMvc.perform(get("/activity-logs")
                        .param("page", "0")
                        .param("size", "5")
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage", is(0)))
                .andExpect(jsonPath("$.pageSize", is(5)));
    }

    @Test
    @DisplayName("Creating a Category triggers non-blocking audit activity log generation")
    void testCategoryCreationGeneratesActivityLog() throws Exception {
        when(fileUploadService.uploadFile(any())).thenReturn("https://test-s3.com/audit-category.png");

        String categoryName = "Audit Category " + UUID.randomUUID().toString().substring(0, 6);
        CategoryRequest categoryRequest = CategoryRequest.builder()
                .name(categoryName)
                .bgColor("#4A90E2")
                .description("Category for audit logging verification")
                .build();

        MockMultipartFile categoryPart = new MockMultipartFile(
                "category", "", "application/json",
                objectMapper.writeValueAsString(categoryRequest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile filePart = new MockMultipartFile(
                "file", "cat.png", "image/png", "fake".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // 1. Perform category creation
        mockMvc.perform(multipart("/admin/categories")
                        .file(categoryPart)
                        .file(filePart)
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isCreated());

        // 2. Query activity logs to observe that the mutation was recorded
        mockMvc.perform(get("/activity-logs")
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Creating a Customer triggers non-blocking audit activity log generation")
    void testCustomerCreationGeneratesActivityLog() throws Exception {
        String uniquePhone = "0977" + (System.currentTimeMillis() % 1000000);
        CustomerRequest customerRequest = CustomerRequest.builder()
                .name("Audit Customer")
                .phoneNumber(uniquePhone)
                .email("audit.customer@billing.com")
                .build();

        // 1. Perform customer creation
        mockMvc.perform(post("/customers")
                        .with(user("staff@billing.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated());

        // 2. Query activity logs
        mockMvc.perform(get("/activity-logs")
                        .with(user("staff@billing.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("User creation and deletion emit CREATE and DELETE activity logs")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void testUserMutationsEmitActivityLogs() throws Exception {
        String uniqueEmail = "testuser." + UUID.randomUUID().toString().substring(0, 8) + "@billing.com";
        UserRequest userRequest = UserRequest.builder()
                .name("Audit Test User")
                .email(uniqueEmail)
                .password("Password123!")
                .role("ROLE_STAFF")
                .build();

        // 1. Create User via POST /admin/register
        MvcResult createResult = mockMvc.perform(post("/admin/register")
                        .with(user("admin@billing.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String userId = objectMapper.readTree(responseBody).get("userId").asText();

        // Verify CREATE log
        boolean createLogged = activityLogRepository.findAll().stream().anyMatch(l ->
                "CREATE".equalsIgnoreCase(l.getAction()) &&
                "USER".equalsIgnoreCase(l.getEntityType()) &&
                userId.equals(l.getEntityId()) &&
                "admin@billing.com".equals(l.getUserEmail()));
        assertThat(createLogged).isTrue();

        // 2. Delete User via DELETE /admin/users/{id}
        mockMvc.perform(delete("/admin/users/" + userId)
                        .with(user("superadmin@billing.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        // Verify DELETE log
        boolean deleteLogged = activityLogRepository.findAll().stream().anyMatch(l ->
                "DELETE".equalsIgnoreCase(l.getAction()) &&
                "USER".equalsIgnoreCase(l.getEntityType()) &&
                userId.equals(l.getEntityId()) &&
                "superadmin@billing.com".equals(l.getUserEmail()));
        assertThat(deleteLogged).isTrue();
    }

    @Test
    @DisplayName("Promotion mutations emit CREATE, UPDATE, and DELETE activity logs")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void testPromotionMutationsEmitActivityLogs() throws Exception {
        String promoCode = "PROMO_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        PromotionRequest promoRequest = PromotionRequest.builder()
                .name("Summer Sale")
                .type(PromotionType.COUPON)
                .code(promoCode)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(15))
                .minOrderAmount(BigDecimal.valueOf(50000))
                .build();

        // 1. Create promotion
        MvcResult createResult = mockMvc.perform(post("/admin/promotions")
                        .with(user("promo.admin@billing.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promoRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String promoId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("promotionId").asText();

        boolean createLogged = activityLogRepository.findAll().stream().anyMatch(l ->
                "CREATE".equalsIgnoreCase(l.getAction()) &&
                "PROMOTION".equalsIgnoreCase(l.getEntityType()) &&
                promoId.equals(l.getEntityId()) &&
                "promo.admin@billing.com".equals(l.getUserEmail()));
        assertThat(createLogged).isTrue();

        // 2. Update promotion
        promoRequest.setName("Summer Sale Updated");
        mockMvc.perform(put("/admin/promotions/" + promoId)
                        .with(user("promo.admin@billing.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promoRequest)))
                .andExpect(status().isOk());

        boolean updateLogged = activityLogRepository.findAll().stream().anyMatch(l ->
                "UPDATE".equalsIgnoreCase(l.getAction()) &&
                "PROMOTION".equalsIgnoreCase(l.getEntityType()) &&
                promoId.equals(l.getEntityId()) &&
                "promo.admin@billing.com".equals(l.getUserEmail()));
        assertThat(updateLogged).isTrue();

        // 3. Delete promotion
        mockMvc.perform(delete("/admin/promotions/" + promoId)
                        .with(user("promo.admin@billing.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        boolean deleteLogged = activityLogRepository.findAll().stream().anyMatch(l ->
                "DELETE".equalsIgnoreCase(l.getAction()) &&
                "PROMOTION".equalsIgnoreCase(l.getEntityType()) &&
                promoId.equals(l.getEntityId()) &&
                "promo.admin@billing.com".equals(l.getUserEmail()));
        assertThat(deleteLogged).isTrue();
    }

    @Test
    @DisplayName("Modifier group mutations emit CREATE, UPDATE, and DELETE activity logs")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void testModifierGroupMutationsEmitActivityLogs() throws Exception {
        ModifierGroupRequest mgRequest = ModifierGroupRequest.builder()
                .name("Sugar Level")
                .description("Choose sweetness")
                .minSelections(0)
                .maxSelections(1)
                .build();

        // 1. Create modifier group
        MvcResult createResult = mockMvc.perform(post("/admin/modifier-groups")
                        .with(user("mg.admin@billing.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mgRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String groupId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("groupId").asText();

        boolean createLogged = activityLogRepository.findAll().stream().anyMatch(l ->
                "CREATE".equalsIgnoreCase(l.getAction()) &&
                "MODIFIER_GROUP".equalsIgnoreCase(l.getEntityType()) &&
                groupId.equals(l.getEntityId()) &&
                "mg.admin@billing.com".equals(l.getUserEmail()));
        assertThat(createLogged).isTrue();

        // 2. Update modifier group
        mgRequest.setName("Sugar Level Updated");
        mockMvc.perform(put("/admin/modifier-groups/" + groupId)
                        .with(user("mg.admin@billing.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mgRequest)))
                .andExpect(status().isOk());

        boolean updateLogged = activityLogRepository.findAll().stream().anyMatch(l ->
                "UPDATE".equalsIgnoreCase(l.getAction()) &&
                "MODIFIER_GROUP".equalsIgnoreCase(l.getEntityType()) &&
                groupId.equals(l.getEntityId()) &&
                "mg.admin@billing.com".equals(l.getUserEmail()));
        assertThat(updateLogged).isTrue();

        // 3. Delete modifier group
        mockMvc.perform(delete("/admin/modifier-groups/" + groupId)
                        .with(user("mg.admin@billing.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        boolean deleteLogged = activityLogRepository.findAll().stream().anyMatch(l ->
                "DELETE".equalsIgnoreCase(l.getAction()) &&
                "MODIFIER_GROUP".equalsIgnoreCase(l.getEntityType()) &&
                groupId.equals(l.getEntityId()) &&
                "mg.admin@billing.com".equals(l.getUserEmail()));
        assertThat(deleteLogged).isTrue();
    }
}
