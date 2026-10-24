package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.CustomerEntity;
import learn.java.billingsoftware.io.CustomerRequest;
import learn.java.billingsoftware.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class CustomerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private CustomerEntity existingCustomer;
    private String uniquePhone;

    @BeforeEach
    void setUp() {
        uniquePhone = "09" + System.currentTimeMillis() % 100000000;
        existingCustomer = customerRepository.save(CustomerEntity.builder()
                .customerId(UUID.randomUUID().toString())
                .name("Nguyen Van An")
                .phoneNumber(uniquePhone)
                .email("an.nguyen@example.com")
                .totalSpent(150000.0)
                .orderCount(3)
                .build());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testCreateCustomerSuccess() throws Exception {
        String newPhone = "0987" + (System.currentTimeMillis() % 1000000);
        CustomerRequest request = CustomerRequest.builder()
                .name("Tran Thi B")
                .phoneNumber(newPhone)
                .email("b.tran@example.com")
                .build();

        mockMvc.perform(post("/customers")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId", notNullValue()))
                .andExpect(jsonPath("$.name", is("Tran Thi B")))
                .andExpect(jsonPath("$.phoneNumber", is(newPhone)))
                .andExpect(jsonPath("$.totalSpent", is(0.0)))
                .andExpect(jsonPath("$.orderCount", is(0)));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testLookupCustomerByPhone() throws Exception {
        mockMvc.perform(get("/customers/by-phone/" + uniquePhone)
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(existingCustomer.getCustomerId())))
                .andExpect(jsonPath("$.name", is("Nguyen Van An")))
                .andExpect(jsonPath("$.phoneNumber", is(uniquePhone)))
                .andExpect(jsonPath("$.totalSpent", is(150000.0)))
                .andExpect(jsonPath("$.orderCount", is(3)));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testLookupCustomerById() throws Exception {
        mockMvc.perform(get("/customers/" + existingCustomer.getCustomerId())
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(existingCustomer.getCustomerId())))
                .andExpect(jsonPath("$.name", is("Nguyen Van An")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testDuplicatePhoneNumberThrowsConflict() throws Exception {
        CustomerRequest duplicateRequest = CustomerRequest.builder()
                .name("Duplicate User")
                .phoneNumber(uniquePhone)
                .build();

        mockMvc.perform(post("/customers")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testSearchCustomersByNameOrPhone() throws Exception {
        mockMvc.perform(get("/customers")
                        .with(user("cashier").roles("USER"))
                        .param("query", "Van An"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].name", is("Nguyen Van An")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testValidationMissingRequiredFields() throws Exception {
        CustomerRequest invalidRequest = CustomerRequest.builder()
                .name("")
                .phoneNumber("")
                .build();

        mockMvc.perform(post("/customers")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateCustomer() throws Exception {
        CustomerRequest updateRequest = CustomerRequest.builder()
                .name("Nguyen Van An Updated")
                .phoneNumber(uniquePhone)
                .email("updated.an@example.com")
                .build();

        mockMvc.perform(put("/customers/" + existingCustomer.getCustomerId())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Nguyen Van An Updated")))
                .andExpect(jsonPath("$.email", is("updated.an@example.com")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteCustomer() throws Exception {
        mockMvc.perform(delete("/customers/" + existingCustomer.getCustomerId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/customers/" + existingCustomer.getCustomerId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
