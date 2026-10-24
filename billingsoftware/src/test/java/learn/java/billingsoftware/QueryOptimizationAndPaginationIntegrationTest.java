package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.*;
import learn.java.billingsoftware.io.PaymentDetails;
import learn.java.billingsoftware.io.PaymentMethod;
import learn.java.billingsoftware.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test suite for Ticket 08: Query Optimization and Pagination Invariants.
 * Verifies:
 * 1. Optimized item fetching loads category and variants.
 * 2. Customer listing endpoint enforces pagination (Pageable defaults and slices).
 * 3. Inventory transactions endpoint enforces pagination (Pageable defaults and slices).
 * 4. Latest orders endpoint enforces a strict limit of 10 recent orders.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class QueryOptimizationAndPaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private OrderEntityRepository orderEntityRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private CategoryEntity category;
    private ItemEntity item;
    private VariantEntity variant1;
    private VariantEntity variant2;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(CategoryEntity.builder()
                .categoryId("CAT-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Beverages")
                .description("Drinks")
                .bgColor("#336699")
                .build());

        item = itemRepository.save(ItemEntity.builder()
                .itemId("ITEM-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Iced Latte")
                .description("Cold espresso milk")
                .price(new BigDecimal("45000.00"))
                .category(category)
                .build());

        variant1 = variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-LATTE-M-" + UUID.randomUUID().toString().substring(0, 5))
                .basePrice(new BigDecimal("45000.00"))
                .cachedStockQuantity(50)
                .attributes(Map.of("size", "M"))
                .item(item)
                .build());

        variant2 = variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-LATTE-L-" + UUID.randomUUID().toString().substring(0, 5))
                .basePrice(new BigDecimal("55000.00"))
                .cachedStockQuantity(30)
                .attributes(Map.of("size", "L"))
                .item(item)
                .build());

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("GET /items loads items with categories and variants without duplication")
    @WithMockUser(username = "cashier", roles = {"USER"})
    void testGetItemsLoadsCategoryAndVariantsEfficiently() throws Exception {
        mockMvc.perform(get("/items")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].itemId", is(item.getItemId())))
                .andExpect(jsonPath("$[0].categoryName", is("Beverages")))
                .andExpect(jsonPath("$[0].variants", hasSize(2)));
    }

    @Test
    @DisplayName("GET /items loads multiple catalog items with variants without Cartesian explosion")
    @WithMockUser(username = "cashier", roles = {"USER"})
    void testGetItemsMultipleCatalogItemsWithoutDuplication() throws Exception {
        // Create 2nd item with 2 variants
        ItemEntity item2 = itemRepository.save(ItemEntity.builder()
                .itemId("ITEM-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Hot Cappuccino")
                .description("Warm steamed milk")
                .price(new BigDecimal("50000.00"))
                .category(category)
                .build());

        variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-CAP-M-" + UUID.randomUUID().toString().substring(0, 5))
                .basePrice(new BigDecimal("50000.00"))
                .cachedStockQuantity(20)
                .attributes(Map.of("size", "M"))
                .item(item2)
                .build());

        variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-CAP-L-" + UUID.randomUUID().toString().substring(0, 5))
                .basePrice(new BigDecimal("60000.00"))
                .cachedStockQuantity(15)
                .attributes(Map.of("size", "L"))
                .item(item2)
                .build());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/items")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].variants", hasSize(2)))
                .andExpect(jsonPath("$[1].variants", hasSize(2)));
    }

    @Test
    @DisplayName("GET /customers returns Page response with default pagination (size 20, page 0)")
    @WithMockUser(username = "staff", roles = {"USER"})
    void testGetCustomersDefaultPagination() throws Exception {
        // Seed 3 customers
        for (int i = 1; i <= 3; i++) {
            customerRepository.save(CustomerEntity.builder()
                    .customerId("CUST-PAGE-" + i + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .name("Page Customer " + i)
                    .phoneNumber("097700000" + i)
                    .email("cust" + i + "@example.com")
                    .totalSpent(0.0)
                    .orderCount(0)
                    .build());
        }

        mockMvc.perform(get("/customers")
                        .with(user("staff").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", isA(java.util.List.class)))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.number", is(0)));
    }

    @Test
    @DisplayName("GET /customers supports custom page and size query parameters")
    @WithMockUser(username = "staff", roles = {"USER"})
    void testGetCustomersCustomPageAndSize() throws Exception {
        for (int i = 1; i <= 5; i++) {
            customerRepository.save(CustomerEntity.builder()
                    .customerId("CUST-SLICE-" + i + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .name("Slice Customer " + i)
                    .phoneNumber("098800000" + i)
                    .email("slice" + i + "@example.com")
                    .totalSpent(0.0)
                    .orderCount(0)
                    .build());
        }

        mockMvc.perform(get("/customers")
                        .param("page", "0")
                        .param("size", "2")
                        .with(user("staff").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("GET /customers with search query applies pagination and filtering properly")
    @WithMockUser(username = "staff", roles = {"USER"})
    void testGetCustomersSearchQueryWithPagination() throws Exception {
        for (int i = 1; i <= 4; i++) {
            customerRepository.save(CustomerEntity.builder()
                    .customerId("CUST-SRCH-" + i + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .name("TargetSearch " + i)
                    .phoneNumber("096600000" + i)
                    .email("target" + i + "@example.com")
                    .totalSpent(0.0)
                    .orderCount(0)
                    .build());
        }

        mockMvc.perform(get("/customers")
                        .param("query", "TargetSearch")
                        .param("page", "0")
                        .param("size", "2")
                        .with(user("staff").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(4)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.content[0].name", startsWith("TargetSearch")));
    }

    @Test
    @DisplayName("GET /admin/inventory/transactions returns Page response with pagination support")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testGetInventoryTransactionsPagination() throws Exception {
        // Seed 4 inventory transactions
        for (int i = 1; i <= 4; i++) {
            inventoryTransactionRepository.save(InventoryTransactionEntity.builder()
                    .transactionId("TX-PAGE-" + i + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .variant(variant1)
                    .transactionType(TransactionType.IN)
                    .quantity(10 * i)
                    .referenceId("REF-" + i)
                    .note("Batch " + i)
                    .build());
        }

        // Test default pagination
        mockMvc.perform(get("/admin/inventory/transactions")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", isA(java.util.List.class)))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.number", is(0)));

        // Test custom page and size
        mockMvc.perform(get("/admin/inventory/transactions")
                        .param("page", "0")
                        .param("size", "2")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.number", is(0)));
    }

    @Test
    @DisplayName("GET /orders/latest returns at most 10 most recent orders")
    @WithMockUser(username = "cashier", roles = {"USER"})
    void testGetLatestOrdersEnforcesLimitOfTen() throws Exception {
        // Seed 15 orders created at staggered timestamps
        LocalDateTime baseTime = LocalDateTime.now().minusHours(20);
        for (int i = 1; i <= 15; i++) {
            orderEntityRepository.save(OrderEntity.builder()
                    .orderId("ORD-LIMIT-" + String.format("%02d", i) + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .customerName("Customer " + i)
                    .phoneNumber("09000000" + String.format("%02d", i))
                    .subtotal(100000.0)
                    .tax(10000.0)
                    .grandTotal(110000.0)
                    .paymentMethod(PaymentMethod.CASH)
                    .paymentDetails(PaymentDetails.builder()
                            .status(PaymentDetails.PaymentStatus.COMPLETED)
                            .build())
                    .createdAt(baseTime.plusMinutes(i * 10))
                    .build());
        }

        mockMvc.perform(get("/orders/latest")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$", hasSize(10)))
                // Verify descending order: the newest order (Order 15) should be first
                .andExpect(jsonPath("$[0].orderId", startsWith("ORD-LIMIT-15")));
    }
}
