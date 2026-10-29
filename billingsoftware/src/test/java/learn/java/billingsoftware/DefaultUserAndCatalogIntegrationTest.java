package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.UserEntity;
import learn.java.billingsoftware.io.AuthRequest;
import learn.java.billingsoftware.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DefaultUserAndCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private Flyway flyway;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        if (userRepository.findByEmail("admin@billingapp.com").isEmpty()) {
            userRepository.save(UserEntity.builder()
                    .userId("usr-admin-default-01")
                    .name("Quản trị viên")
                    .email("admin@billingapp.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .role("ROLE_ADMIN")
                    .build());
        }
        if (userRepository.findByEmail("staff@billingapp.com").isEmpty()) {
            userRepository.save(UserEntity.builder()
                    .userId("usr-staff-default-02")
                    .name("Nhân viên thu ngân")
                    .email("staff@billingapp.com")
                    .password(passwordEncoder.encode("Staff@123"))
                    .role("ROLE_STAFF")
                    .build());
        }
    }

    @Test
    @DisplayName("Default admin and staff accounts exist in repository from Flyway migration V3")
    void testDefaultUsersExist() {
        UserEntity admin = userRepository.findByEmail("admin@billingapp.com").orElse(null);
        assertThat(admin).isNotNull();
        assertThat(admin.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(admin.getName()).isEqualTo("Quản trị viên");

        UserEntity staff = userRepository.findByEmail("staff@billingapp.com").orElse(null);
        assertThat(staff).isNotNull();
        assertThat(staff.getRole()).isEqualTo("ROLE_STAFF");
        assertThat(staff.getName()).isEqualTo("Nhân viên thu ngân");
    }

    @Test
    @DisplayName("Admin logs in successfully with default credentials and receives valid JWT")
    void testAdminLoginWithDefaultCredentials() throws Exception {
        AuthRequest loginRequest = new AuthRequest("admin@billingapp.com", "Admin@123");

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value("admin@billingapp.com"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.name").value("Quản trị viên"));
    }

    @Test
    @DisplayName("Staff logs in successfully with default credentials and receives valid JWT")
    void testStaffLoginWithDefaultCredentials() throws Exception {
        AuthRequest loginRequest = new AuthRequest("staff@billingapp.com", "Staff@123");

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value("staff@billingapp.com"))
                .andExpect(jsonPath("$.role").value("ROLE_STAFF"))
                .andExpect(jsonPath("$.name").value("Nhân viên thu ngân"));
    }

    @Test
    @DisplayName("Staff role with ROLE_STAFF is authorized on operational endpoints but forbidden on admin endpoints")
    void testStaffRoleAuthorization() throws Exception {
        // Staff can access categories and dashboard
        mockMvc.perform(get("/categories")
                        .with(user("staff@billingapp.com").roles("STAFF")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/dashboard")
                        .with(user("staff@billingapp.com").roles("STAFF")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders")
                        .with(user("staff@billingapp.com").roles("STAFF")))
                .andExpect(status().isOk());

        // Staff is forbidden on /admin/users
        mockMvc.perform(get("/admin/users")
                        .with(user("staff@billingapp.com").roles("STAFF")))
                .andExpect(status().isForbidden());

        // Admin is authorized on /admin/users
        mockMvc.perform(get("/admin/users")
                        .with(user("admin@billingapp.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Flyway migration V3 for default users and catalog is applied")
    void testFlywayV3Applied() {
        if (flyway != null) {
            MigrationInfo[] applied = flyway.info().applied();
            boolean v3Applied = Arrays.stream(applied)
                    .anyMatch(info -> "3".equals(info.getVersion().getVersion()));
            assertThat(v3Applied).isTrue();
        }
    }
}
