package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.UserEntity;
import learn.java.billingsoftware.io.UserRequest;
import learn.java.billingsoftware.io.UserResponse;
import learn.java.billingsoftware.repository.ActivityLogRepository;
import learn.java.billingsoftware.repository.UserRepository;
import learn.java.billingsoftware.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class UserUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Admin can update user name, email, role and password")
    void testAdminCanUpdateUser() throws Exception {
        String email1 = "user." + UUID.randomUUID().toString().substring(0, 8) + "@billing.com";
        UserRequest createReq = UserRequest.builder()
                .name("Original Name")
                .email(email1)
                .password("OriginalPass123!")
                .role("ROLE_STAEF")
                .build();
        UserResponse created = userService.createUser(createReq);
        String userId = created.getUserId();

        // 1. Staff is rejected with 403 Forbidden
        UserRequest updateReq = UserRequest.builder()
                .name("Updated Name")
                .email("new." + email1)
                .role("ROLE_ADMIN")
                .password("NewPass456!")
                .build();

        mockMvc.perform(put("/admin/users/" + userId)
                .with(user("staff@billing.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // 2. Admin successfully updates user with new password
        mockMvc.perform(put("/admin/users/" + userId)
                .with(user("admin@billing.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.email").value("new." + email1))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));

        UserEntity entity = userRepository.findByUserId(userId).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456!", entity.getPassword())).isTrue();

        boolean logged = activityLogRepository.findAll().stream().anyMatch(l ->
                "UPDATE".equalsIgnoreCase(l.getAction()) &&
                "USER".equalsIgnoreCase(l.getEntityType()) &&
                userId.equals(l.getEntityId()) &&
                "admin@billing.com".equals(l.getUserEmail()));
        assertThat(logged).isTrue();

        // 3. Update without new password -> preserves existing password
        UserRequest updateWithoutPass = UserRequest.builder()
                .name("Final Name")
                .email("new." + email1)
                .role("ROLE_ADMIN")
                .password("")
                .build();

        mockMvc.perform(put("/admin/users/" + userId)
                .with(user("admin@billing.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateWithoutPass)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Final Name"));

        UserEntity entityAfter = userRepository.findByUserId(userId).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456!", entityAfter.getPassword())).isTrue();
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Updating user to already existing email returns 409 Conflict")
    void testEmailConflictReturns409() throws Exception {
        String emailA = "userA." + UUID.randomUUID().toString().substring(0, 8) + "@billing.com";
        String emailB = "userB." + UUID.randomUUID().toString().substring(0, 8) + "@billing.com";

        UserResponse userA = userService.createUser(UserRequest.builder()
                .name("User A").email(emailA).password("PassA123!").role("ROLE_STAFF").build());
        UserResponse userB = userService.createUser(UserRequest.builder()
                .name("User B").email(emailB).password("PassB123!").role("ROLE_STAFF").build());

        UserRequest conflictReq = UserRequest.builder()
                .name("User B")
                .email(emailA)
                .role("ROLE_STAFF")
                .build();

        mockMvc.perform(put("/admin/users/" + userB.getUserId())
                .with(user("admin@billing.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Updating non-existent user returns 404 Not Found")
    void testUpdateNonExistentUserReturns404() throws Exception {
        UserRequest req = UserRequest.builder()
                .name("NonExistent")
                .email("notfound@billing.com")
                .build();

        mockMvc.perform(put("/admin/users/" + UUID.randomUUID())
                .with(user("admin@billing.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
