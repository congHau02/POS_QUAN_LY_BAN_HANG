package learn.java.billingsoftware;

import learn.java.billingsoftware.entity.ActivityLogEntity;
import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.entity.ItemEntity;
import learn.java.billingsoftware.entity.UserEntity;
import learn.java.billingsoftware.repository.ActivityLogRepository;
import learn.java.billingsoftware.repository.CategoryRepository;
import learn.java.billingsoftware.repository.ItemRepository;
import learn.java.billingsoftware.repository.UserRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void testEncodeEndpointIsBlockedForUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCorsAllowedOriginsAcceptsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/categories")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void testAdminSelfDeletionIsPreventedWithHttpBadRequest() throws Exception {
        String adminEmail = "admin." + UUID.randomUUID().toString().substring(0, 8) + "@billing.com";
        UserEntity admin = userRepository.save(UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .name("Admin User")
                .email(adminEmail)
                .password("encoded_pass")
                .role("ROLE_ADMIN")
                .build());

        String otherEmail = "staff." + UUID.randomUUID().toString().substring(0, 8) + "@billing.com";
        UserEntity other = userRepository.save(UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .name("Other Staff")
                .email(otherEmail)
                .password("encoded_pass")
                .role("ROLE_STAFF")
                .build());

        // Self deletion must be rejected with 400 Bad Request
        mockMvc.perform(delete("/admin/users/" + admin.getUserId())
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByUserId(admin.getUserId())).isPresent();

        // Deleting another user must succeed with 204 No Content
        mockMvc.perform(delete("/admin/users/" + other.getUserId())
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUserId(other.getUserId())).isEmpty();
    }

    @Test
    void testCategoryDeleteBlockedWhenItemsExist() throws Exception {
        CategoryEntity categoryWithItem = categoryRepository.save(CategoryEntity.builder()
                .categoryId(UUID.randomUUID().toString())
                .name("Cat-With-Items-" + UUID.randomUUID().toString().substring(0, 8))
                .build());

        itemRepository.save(ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name("Attached Item")
                .price(BigDecimal.valueOf(100.0))
                .category(categoryWithItem)
                .build());

        CategoryEntity emptyCategory = categoryRepository.save(CategoryEntity.builder()
                .categoryId(UUID.randomUUID().toString())
                .name("Empty-Cat-" + UUID.randomUUID().toString().substring(0, 8))
                .build());

        // Attempting to delete category that has items must fail with 400 Bad Request
        mockMvc.perform(delete("/admin/categories/" + categoryWithItem.getCategoryId())
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        assertThat(categoryRepository.findByCategoryId(categoryWithItem.getCategoryId())).isPresent();

        // Attempting to delete category with 0 items must succeed with 204 No Content
        mockMvc.perform(delete("/admin/categories/" + emptyCategory.getCategoryId())
                        .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findByCategoryId(emptyCategory.getCategoryId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void testActivityLogPersistenceIsIsolatedWhenCallerTransactionRollsBack() {
        String testEntityId = "isolate-" + UUID.randomUUID();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        try {
            template.execute(status -> {
                activityLogService.logActivity("admin@test.com", "TEST_ISOLATE", "SECURITY_TEST", testEntityId, "Audit log in isolated tx");
                throw new RuntimeException("Simulated business failure to trigger rollback");
            });
        } catch (RuntimeException ignored) {
            // expected failure to roll back the outer transaction
        }

        List<ActivityLogEntity> logs = activityLogRepository.findAll().stream()
                .filter(l -> testEntityId.equals(l.getEntityId()))
                .toList();

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getDescription()).isEqualTo("Audit log in isolated tx");

        // Clean up
        activityLogRepository.deleteAll(logs);
    }
}
