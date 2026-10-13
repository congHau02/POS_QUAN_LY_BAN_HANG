package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.UserEntity;
import learn.java.billingsoftware.io.UserRequest;
import learn.java.billingsoftware.repository.UserRepository;
import learn.java.billingsoftware.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ExceptionHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Test
    void testGetNonExistentOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/orders/non-existent-order-id-12345")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void testRegisterDuplicateEmailReturnsConflict() throws Exception {
        String email = "duplicate_" + UUID.randomUUID() + "@example.com";
        userRepository.save(UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .name("Existing User")
                .email(email)
                .password("encoded_pass")
                .role("ROLE_STAFF")
                .build());

        UserRequest req = new UserRequest();
        req.setEmail(email);
        req.setPassword("123456");
        req.setName("Duplicate User");
        req.setRole("ROLE_STAFF");

        mockMvc.perform(post("/admin/register")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void testDeleteNonExistentUserReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/admin/users/non-existent-user-id")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteNonExistentCategoryReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/admin/categories/non-existent-category-id")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void testFindByEmailNonExistentThrowsResponseStatusExceptionNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            userService.findByEmail("non-existent-" + UUID.randomUUID() + "@example.com");
        });
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void testGetUserRoleNonExistentThrowsResponseStatusExceptionNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            userService.getUserRole("non-existent-" + UUID.randomUUID() + "@example.com");
        });
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
