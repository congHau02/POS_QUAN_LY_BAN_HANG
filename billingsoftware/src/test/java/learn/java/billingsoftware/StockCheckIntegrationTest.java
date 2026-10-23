package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.entity.InventoryTransactionEntity;
import learn.java.billingsoftware.entity.ItemEntity;
import learn.java.billingsoftware.entity.TransactionType;
import learn.java.billingsoftware.entity.VariantEntity;
import learn.java.billingsoftware.io.StockCheckRequest;
import learn.java.billingsoftware.repository.CategoryRepository;
import learn.java.billingsoftware.repository.InventoryTransactionRepository;
import learn.java.billingsoftware.repository.ItemRepository;
import learn.java.billingsoftware.repository.VariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class StockCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CategoryEntity testCategory;
    private ItemEntity testItem;
    private VariantEntity testVariant;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.save(CategoryEntity.builder()
                .categoryId("CAT-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Test Category")
                .description("Category for stock check")
                .bgColor("#FFFFFF")
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId("ITEM-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Stock Check Test Shirt")
                .price(new BigDecimal("200000.00"))
                .description("Shirt for stock audit")
                .category(testCategory)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-CHECK-L-BLUE")
                .basePrice(new BigDecimal("200000.00"))
                .cachedStockQuantity(0)
                .attributes(Map.of("Color", "Blue", "Size", "L"))
                .item(testItem)
                .build());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testStockCheckSurplusCreatesPositiveAdjustment() throws Exception {
        // Initial stock: 10
        inventoryTransactionRepository.saveAndFlush(InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(testVariant)
                .transactionType(TransactionType.IN)
                .quantity(10)
                .referenceId("INIT-PO")
                .build());
        testVariant.setCachedStockQuantity(10);
        variantRepository.save(testVariant);

        // Counted stock: 15 (surplus +5)
        StockCheckRequest request = StockCheckRequest.builder()
                .variantId(testVariant.getVariantId())
                .actualCount(15)
                .referenceId("AUDIT-2026-001")
                .note("Found 5 additional units in backroom")
                .build();

        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId", notNullValue()))
                .andExpect(jsonPath("$.variantId", is(testVariant.getVariantId())))
                .andExpect(jsonPath("$.transactionType", is("ADJUSTMENT")))
                .andExpect(jsonPath("$.quantity", is(5)))
                .andExpect(jsonPath("$.balanceAfter", is(15)))
                .andExpect(jsonPath("$.referenceId", is("AUDIT-2026-001")))
                .andExpect(jsonPath("$.note", is("Found 5 additional units in backroom")));

        VariantEntity updatedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(15, updatedVariant.getCachedStockQuantity());

        List<InventoryTransactionEntity> txs = inventoryTransactionRepository
                .findByVariant_VariantIdOrderByCreatedAtDesc(testVariant.getVariantId());
        assertEquals(2, txs.size());
        assertEquals(TransactionType.ADJUSTMENT, txs.get(0).getTransactionType());
        assertEquals(5, txs.get(0).getQuantity());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testStockCheckShortageCreatesNegativeAdjustment() throws Exception {
        // Initial stock: 20
        inventoryTransactionRepository.saveAndFlush(InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(testVariant)
                .transactionType(TransactionType.IN)
                .quantity(20)
                .referenceId("INIT-PO")
                .build());
        testVariant.setCachedStockQuantity(20);
        variantRepository.save(testVariant);

        // Counted stock: 14 (shortage -6)
        StockCheckRequest request = StockCheckRequest.builder()
                .variantId(testVariant.getVariantId())
                .actualCount(14)
                .referenceId("AUDIT-2026-002")
                .note("6 units damaged / missing")
                .build();

        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType", is("ADJUSTMENT")))
                .andExpect(jsonPath("$.quantity", is(-6)))
                .andExpect(jsonPath("$.balanceAfter", is(14)))
                .andExpect(jsonPath("$.referenceId", is("AUDIT-2026-002")));

        VariantEntity updatedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(14, updatedVariant.getCachedStockQuantity());

        List<InventoryTransactionEntity> txs = inventoryTransactionRepository
                .findByVariant_VariantIdOrderByCreatedAtDesc(testVariant.getVariantId());
        assertEquals(2, txs.size());
        assertEquals(TransactionType.ADJUSTMENT, txs.get(0).getTransactionType());
        assertEquals(-6, txs.get(0).getQuantity());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testStockCheckExactMatchCreatesZeroAdjustmentAudit() throws Exception {
        // Initial stock: 10
        inventoryTransactionRepository.saveAndFlush(InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(testVariant)
                .transactionType(TransactionType.IN)
                .quantity(10)
                .referenceId("INIT-PO")
                .build());
        testVariant.setCachedStockQuantity(10);
        variantRepository.save(testVariant);

        // Counted stock: 10 (exact match -> quantity 0)
        StockCheckRequest request = StockCheckRequest.builder()
                .variantId(testVariant.getVariantId())
                .actualCount(10)
                .referenceId("AUDIT-2026-003")
                .note("Audit verified matching count")
                .build();

        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType", is("ADJUSTMENT")))
                .andExpect(jsonPath("$.quantity", is(0)))
                .andExpect(jsonPath("$.balanceAfter", is(10)));

        VariantEntity updatedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(10, updatedVariant.getCachedStockQuantity());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testStockCheckFromNegativeStockToPositive() throws Exception {
        // Initial stock: -3 (negative stock due to offline checkout)
        inventoryTransactionRepository.saveAndFlush(InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(testVariant)
                .transactionType(TransactionType.OUT)
                .quantity(-3)
                .referenceId("OFFLINE-ORDER")
                .build());
        testVariant.setCachedStockQuantity(-3);
        variantRepository.save(testVariant);

        // Counted stock: 8 -> Discrepancy = 8 - (-3) = +11
        StockCheckRequest request = StockCheckRequest.builder()
                .variantId(testVariant.getVariantId())
                .actualCount(8)
                .referenceId("AUDIT-2026-004")
                .note("Reconciling negative stock with physical reality")
                .build();

        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType", is("ADJUSTMENT")))
                .andExpect(jsonPath("$.quantity", is(11)))
                .andExpect(jsonPath("$.balanceAfter", is(8)));

        VariantEntity updatedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(8, updatedVariant.getCachedStockQuantity());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testStockCheckReconcilesDriftBetweenCachedStockAndLedger() throws Exception {
        // 1. Initial ledger transaction: IN 10 -> Ledger sum = 10
        inventoryTransactionRepository.saveAndFlush(InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(testVariant)
                .transactionType(TransactionType.IN)
                .quantity(10)
                .referenceId("INIT-PO")
                .build());

        // 2. Simulate drift: cachedStockQuantity is set to 12 (drift of +2 from ledger)
        testVariant.setCachedStockQuantity(12);
        variantRepository.save(testVariant);

        // 3. Physical stock check counts 15 units
        StockCheckRequest request = StockCheckRequest.builder()
                .variantId(testVariant.getVariantId())
                .actualCount(15)
                .referenceId("AUDIT-DRIFT-RECONCILE")
                .note("Audit reconciling cached drift with true physical reality")
                .build();

        // Discrepancy against true ledger (10) must be: 15 - 10 = +5.
        // If discrepancy were incorrectly calculated against cachedStock (12), it would be +3, leaving ledger at 13.
        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType", is("ADJUSTMENT")))
                .andExpect(jsonPath("$.quantity", is(5)))
                .andExpect(jsonPath("$.balanceAfter", is(15)));

        VariantEntity updatedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(15, updatedVariant.getCachedStockQuantity());

        Integer finalLedgerSum = inventoryTransactionRepository.calculateStockByVariantId(testVariant.getVariantId());
        assertEquals(15, finalLedgerSum, "Historical ledger sum must perfectly match actual physical count (15)");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testStockCheckValidationErrors() throws Exception {
        // Missing variantId
        StockCheckRequest reqNoVariant = StockCheckRequest.builder()
                .actualCount(5)
                .build();

        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNoVariant)))
                .andExpect(status().isBadRequest());

        // Negative actual count
        StockCheckRequest reqNegCount = StockCheckRequest.builder()
                .variantId(testVariant.getVariantId())
                .actualCount(-1)
                .build();

        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNegCount)))
                .andExpect(status().isBadRequest());

        // Non-existent variantId
        StockCheckRequest reqUnknownVariant = StockCheckRequest.builder()
                .variantId("VAR-UNKNOWN-999")
                .actualCount(5)
                .build();

        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUnknownVariant)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testStockCheckUnauthorizedForNonAdmin() throws Exception {
        StockCheckRequest request = StockCheckRequest.builder()
                .variantId(testVariant.getVariantId())
                .actualCount(10)
                .build();

        // USER role should be forbidden (403) from accessing /admin/inventory/stock-check
        mockMvc.perform(post("/admin/inventory/stock-check")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
