package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.*;
import learn.java.billingsoftware.io.*;
import learn.java.billingsoftware.repository.*;
import learn.java.billingsoftware.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ticket 11: End-to-End Integration Verification and Regression Testing.
 * Comprehensive test suite verifying all Phase 11 remediations:
 * 1. Concurrent checkouts across POS terminals succeed without duplicate key errors and with collision-proof order IDs.
 * 2. Decoupled PayOS gateway error handling with automated inventory compensation and 502 status.
 * 3. Deferred customer spending lifecycle (does not accrue on PENDING, accrues on COMPLETED).
 * 4. Dashboard sales revenue strictly aggregates COMPLETED orders.
 * 5. Atomic database promotion usage quotas under concurrent load.
 * 6. Admin self-deletion and category deletion restrictions return HTTP 400.
 * 7. Pagination contracts for customers, inventory ledger, and latest orders.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class EndToEndRegressionIntegrationTest {

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("^ORD\\d+-[0-9A-Z]{4}$");

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

    @Autowired
    private OrderEntityRepository orderEntityRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private InventoryService inventoryService;

    @MockitoBean
    private PayOS payOS;

    @MockitoBean
    private PaymentRequestsService paymentRequestsService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private CategoryEntity testCategory;
    private ItemEntity testItem;
    private VariantEntity testVariant;

    @BeforeEach
    void setUp() {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenAnswer(invocation -> {
            CreatePaymentLinkRequest req = invocation.getArgument(0);
            return CreatePaymentLinkResponse.builder()
                    .bin("970403")
                    .accountNumber("123456789")
                    .accountName("REGRESSION TEST STORE")
                    .amount(req.getAmount())
                    .description("REGRESSION PAYOS")
                    .orderCode(req.getOrderCode())
                    .currency("VND")
                    .paymentLinkId("LINK-" + req.getOrderCode())
                    .status(PaymentLinkStatus.PENDING)
                    .checkoutUrl("https://payos.vn/checkout/" + req.getOrderCode())
                    .qrCode("00020101...")
                    .build();
        });

        String rand = UUID.randomUUID().toString().substring(0, 8);
        testCategory = categoryRepository.save(CategoryEntity.builder()
                .categoryId("CAT-" + rand)
                .name("Cat-" + rand)
                .bgColor("#4B5563")
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId("ITEM-" + rand)
                .name("Item-" + rand)
                .price(new BigDecimal("50000.00"))
                .category(testCategory)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + rand)
                .sku("SKU-" + rand)
                .basePrice(new BigDecimal("50000.00"))
                .cachedStockQuantity(0)
                .item(testItem)
                .build());

        // Stock in 500 units for regression tests
        inventoryService.recordTransaction(InventoryTransactionRequest.builder()
                .variantId(testVariant.getVariantId())
                .transactionType(TransactionType.IN)
                .quantity(500)
                .referenceId("PO-REGRESSION-INIT")
                .build());
    }

    @AfterEach
    void tearDown() {
        activityLogRepository.deleteAll();
        inventoryTransactionRepository.deleteAll();
        orderEntityRepository.deleteAll();
        promotionRepository.deleteAll();
        variantRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Concurrent checkouts succeed without duplicate key errors and with collision-proof order IDs")
    void testConcurrentCheckouts_SucceedWithoutDuplicateKeyErrors() throws Exception {
        int threadCount = 10;

        // Set up 10 variants for the 10 concurrent cashier checkout terminals
        List<VariantEntity> terminalVariants = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String vSuffix = UUID.randomUUID().toString().substring(0, 8);
            VariantEntity v = variantRepository.save(VariantEntity.builder()
                    .variantId("VAR-TERM-" + i + "-" + vSuffix)
                    .sku("SKU-TERM-" + i + "-" + vSuffix)
                    .basePrice(new BigDecimal("50000.00"))
                    .cachedStockQuantity(0)
                    .item(testItem)
                    .build());
            inventoryService.recordTransaction(InventoryTransactionRequest.builder()
                    .variantId(v.getVariantId())
                    .transactionType(TransactionType.IN)
                    .quantity(50)
                    .referenceId("PO-TERM-INIT-" + i)
                    .build());
            terminalVariants.add(v);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<String> createdOrderIds = new CopyOnWriteArrayList<>();
        List<Throwable> unexpectedExceptions = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            final VariantEntity terminalVariant = terminalVariants.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    OrderRequest.OrderItemRequest itemReq = OrderRequest.OrderItemRequest.builder()
                            .itemId(testItem.getItemId())
                            .variantId(terminalVariant.getVariantId())
                            .name(testItem.getName())
                            .price(50000.00)
                            .quantity(1)
                            .build();

                    OrderRequest request = OrderRequest.builder()
                            .customerName("Cashier Terminal " + index)
                            .phoneNumber("090000000" + index)
                            .cartItems(List.of(itemReq))
                            .paymentMethod("CASH")
                            .build();

                    MvcResult result = mockMvc.perform(post("/orders")
                                    .with(user("cashier_" + index).roles("USER"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int statusCode = result.getResponse().getStatus();
                    if (statusCode == 201) {
                        String respJson = result.getResponse().getContentAsString();
                        JsonNode jsonNode = objectMapper.readTree(respJson);
                        String orderId = jsonNode.get("orderId").asText();
                        createdOrderIds.add(orderId);
                        successCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(new AssertionError("Expected 201 Created but got " + statusCode + ": " + result.getResponse().getContentAsString()));
                    }
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "All threads ready");
        startLatch.countDown(); // Fire simultaneous checkout requests across POS terminals
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "All threads completed within timeout");
        executor.shutdown();

        if (!unexpectedExceptions.isEmpty()) {
            unexpectedExceptions.get(0).printStackTrace();
        }
        assertEquals(0, unexpectedExceptions.size(), "No unexpected failures in concurrent checkouts");
        assertEquals(threadCount, successCount.get(), "All " + threadCount + " concurrent checkouts must return 201 Created");

        // Verify all order codes are unique (no duplicate key collisions on tbl_orders.order_code)
        Set<String> uniqueCodes = new HashSet<>(createdOrderIds);
        assertEquals(threadCount, uniqueCodes.size(), "All generated order codes must be distinct (zero duplicate key collisions)");

        for (String orderId : createdOrderIds) {
            assertTrue(ORDER_CODE_PATTERN.matcher(orderId).matches(),
                    "Order ID '" + orderId + "' must adhere to collision-proof pattern ORD<timestamp>-<suffix>");
        }

        // Verify each variant cached stock quantity was correctly decremented from 50 down to 49
        for (int i = 0; i < threadCount; i++) {
            VariantEntity updatedVar = variantRepository.findByVariantId(terminalVariants.get(i).getVariantId()).orElseThrow();
            assertEquals(49, updatedVar.getCachedStockQuantity(),
                    "Terminal variant " + i + " cached stock quantity must accurately decrement by 1");
        }
    }

    @Test
    @DisplayName("2a. PayOS balanced payload aggregates cart items into a single item matching grandTotal when VAT or discounts are present")
    void testPayOSBalancedPayload_AggregatesWhenTaxOrDiscountsPresent() throws Exception {
        OrderRequest.OrderItemRequest taxedItemReq = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(2)
                .build();

        OrderRequest successPayosReq = OrderRequest.builder()
                .customerName("Balanced Test Customer")
                .phoneNumber("0987654321")
                .paymentMethod("PAYOS")
                .cartItems(List.of(taxedItemReq))
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(successPayosReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grandTotal", is(110000.0)));

        ArgumentCaptor<CreatePaymentLinkRequest> captor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(paymentRequestsService, atLeastOnce()).create(captor.capture());
        CreatePaymentLinkRequest capturedReq = captor.getValue();
        assertEquals(110000L, capturedReq.getAmount(), "Payment request amount must match grandTotal");
        assertEquals(1, capturedReq.getItems().size(), "PayOS items must be collapsed into 1 balanced item due to VAT");
        assertEquals(110000L, capturedReq.getItems().get(0).getPrice(), "Item price must equal grand total");
        assertTrue(capturedReq.getItems().get(0).getName().startsWith("Don hang #"), "Aggregated item name must be Don hang #<code");
    }

    @Test
    @DisplayName("2b. Decoupled PayOS gateway error triggers automated compensating stock reversal and returns 502")
    void testPayOSGatewayError_TriggersCleanInventoryReplenishment() throws Exception {
        // Mock PayOS gateway throwing upstream timeout
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class)))
                .thenThrow(new RuntimeException("PayOS upstream gateway timed out"));

        OrderRequest.OrderItemRequest itemReq = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(3)
                .build();

        OrderRequest request = OrderRequest.builder()
                .customerName("Payment Test User")
                .phoneNumber("0912345678")
                .cartItems(List.of(itemReq))
                .paymentMethod("PAYOS")
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway());

        // Verify the order is saved with CANCELLED status
        List<OrderEntity> orders = orderEntityRepository.findAll();
        assertFalse(orders.isEmpty(), "Order entity must be saved in database");
        OrderEntity cancelledOrder = orders.get(0);
        assertEquals(PaymentDetails.PaymentStatus.CANCELLED, cancelledOrder.getPaymentDetails().getStatus(),
                "Order paymentDetails status must be CANCELLED");

        // Verify stock is restored to 500
        VariantEntity revertedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(500, revertedVariant.getCachedStockQuantity(),
                "Stock must be fully replenished back to 500 via compensation");

        // Verify compensating IN ledger transaction exists
        List<InventoryTransactionEntity> transactions = inventoryTransactionRepository
                .findByVariant_VariantIdOrderByCreatedAtDesc(testVariant.getVariantId());
        boolean hasCompensatingInTx = transactions.stream()
                .anyMatch(tx -> tx.getTransactionType() == TransactionType.IN
                        && cancelledOrder.getOrderId().equals(tx.getReferenceId())
                        && tx.getQuantity() == 3);
        assertTrue(hasCompensatingInTx, "Compensating IN transaction must be recorded for cancelled order");
    }

    @Test
    @DisplayName("3. Customer spending does not accrue on uncompleted pending orders, only on completed orders")
    void testCustomerSpendingLifecycle_DeferredUntilOrderCompleted() throws Exception {
        CustomerEntity customer = customerRepository.save(CustomerEntity.builder()
                .customerId("CUST-E2E-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Nguyen Van A")
                .phoneNumber("0988776655")
                .orderCount(0)
                .totalSpent(0.0)
                .build());

        OrderRequest.OrderItemRequest itemReq = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(2)
                .build();

        OrderRequest pendingPayosRequest = OrderRequest.builder()
                .customerId(customer.getCustomerId())
                .customerName(customer.getName())
                .phoneNumber(customer.getPhoneNumber())
                .paymentMethod("PAYOS")
                .cartItems(List.of(itemReq))
                .build();

        // 3a. Create PENDING PayOS Order
        MvcResult pendingResult = mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pendingPayosRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentDetails.status", is("PENDING")))
                .andExpect(jsonPath("$.grandTotal", is(110000.0)))
                .andReturn();

        String pendingOrderId = objectMapper.readTree(pendingResult.getResponse().getContentAsString()).get("orderId").asText();

        // Verify customer CRM metrics did NOT accrue
        CustomerEntity customerAfterPending = customerRepository.findByCustomerId(customer.getCustomerId()).orElseThrow();
        assertEquals(0, customerAfterPending.getOrderCount(), "Order count must NOT increment on PENDING order");
        assertEquals(0.0, customerAfterPending.getTotalSpent(), 0.001, "Total spent must NOT accrue on PENDING order");

        // 3b. Cancel the pending order via DELETE /orders/{orderId}
        mockMvc.perform(delete("/orders/" + pendingOrderId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        // Verify customer CRM metrics remain 0 (no false decrement or reversal)
        CustomerEntity customerAfterCancel = customerRepository.findByCustomerId(customer.getCustomerId()).orElseThrow();
        assertEquals(0, customerAfterCancel.getOrderCount(), "Order count must remain 0 after pending order deletion");
        assertEquals(0.0, customerAfterCancel.getTotalSpent(), 0.001, "Total spent must remain 0 after pending order deletion");

        // 3c. Create CASH order (immediately COMPLETED)
        OrderRequest cashRequest = OrderRequest.builder()
                .customerId(customer.getCustomerId())
                .customerName(customer.getName())
                .phoneNumber(customer.getPhoneNumber())
                .paymentMethod("CASH")
                .cartItems(List.of(itemReq))
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentDetails.status", is("COMPLETED")))
                .andExpect(jsonPath("$.grandTotal", is(110000.0)));

        // Verify customer CRM metrics now accrued
        CustomerEntity customerAfterCash = customerRepository.findByCustomerId(customer.getCustomerId()).orElseThrow();
        assertEquals(1, customerAfterCash.getOrderCount(), "Order count must be 1 after COMPLETED cash order");
        assertEquals(110000.0, customerAfterCash.getTotalSpent(), 0.001, "Total spent must be 110,000 VND after COMPLETED cash order");

        // 3d. Switch in-flight PayOS order to CASH and verify customer spending accrues
        MvcResult payos2Result = mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pendingPayosRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentDetails.status", is("PENDING")))
                .andReturn();

        String payos2OrderId = objectMapper.readTree(payos2Result.getResponse().getContentAsString()).get("orderId").asText();

        mockMvc.perform(post("/orders/" + payos2OrderId + "/switch-to-cash")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod", is("CASH")))
                .andExpect(jsonPath("$.paymentDetails.status", is("COMPLETED")));

        CustomerEntity customerAfterSwitch = customerRepository.findByCustomerId(customer.getCustomerId()).orElseThrow();
        assertEquals(2, customerAfterSwitch.getOrderCount(), "Order count must be 2 after switching in-flight order to CASH");
        assertEquals(220000.0, customerAfterSwitch.getTotalSpent(), 0.001, "Total spent must be 220,000 VND after switching in-flight order to CASH");
    }

    @Test
    @DisplayName("4. Dashboard sales revenue strictly aggregates COMPLETED orders, excluding PENDING and CANCELLED")
    void testDashboardSalesRevenue_StrictlyAggregatesCompletedOrders() throws Exception {
        // Create 1 COMPLETED CASH order
        OrderRequest.OrderItemRequest itemReq = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(2) // 100,000 + 10% tax = 110,000
                .build();

        OrderRequest cashOrder = OrderRequest.builder()
                .customerName("Cash Buyer")
                .phoneNumber("0911111111")
                .paymentMethod("CASH")
                .cartItems(List.of(itemReq))
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashOrder)))
                .andExpect(status().isCreated());

        // Create 1 PENDING PAYOS order
        OrderRequest payosOrder = OrderRequest.builder()
                .customerName("Pending PayOS Buyer")
                .phoneNumber("0922222222")
                .paymentMethod("PAYOS")
                .cartItems(List.of(itemReq))
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payosOrder)))
                .andExpect(status().isCreated());

        // Directly persist 1 CANCELLED order
        orderEntityRepository.save(OrderEntity.builder()
                .orderId("ORD-CANCELLED-" + UUID.randomUUID().toString().substring(0, 8))
                .customerName("Cancelled Order")
                .phoneNumber("0933333333")
                .subtotal(50000.0)
                .tax(5000.0)
                .grandTotal(55000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(PaymentDetails.builder()
                        .status(PaymentDetails.PaymentStatus.CANCELLED)
                        .build())
                .createdAt(java.time.LocalDateTime.now())
                .build());

        // Fetch Dashboard
        mockMvc.perform(get("/dashboard")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayOrderCount", is(1)))
                .andExpect(jsonPath("$.todaySales", is(110000.0)));
    }

    @Test
    @DisplayName("5. Promotion usage quotas cannot be exceeded under concurrent load")
    void testPromotionUsageLimit_AtomicEnforcementUnderConcurrentLoad() throws Exception {
        PromotionEntity coupon = promotionRepository.save(PromotionEntity.builder()
                .promotionId(UUID.randomUUID().toString())
                .name("Quota Limit 2 Coupon")
                .type(PromotionType.COUPON)
                .code("QUOTA2-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase())
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(10000.0))
                .minOrderAmount(BigDecimal.valueOf(50000.0))
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .usageLimit(2)
                .timesUsed(0)
                .isActive(true)
                .build());

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger quotaExceededCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    OrderRequest.OrderItemRequest itemReq = OrderRequest.OrderItemRequest.builder()
                            .itemId(testItem.getItemId())
                            .variantId(testVariant.getVariantId())
                            .name(testItem.getName())
                            .price(50000.00)
                            .quantity(2)
                            .build();

                    OrderRequest request = OrderRequest.builder()
                            .customerName("Promo Tester")
                            .phoneNumber("0977889900")
                            .couponCode(coupon.getCode())
                            .paymentMethod("CASH")
                            .cartItems(List.of(itemReq))
                            .build();

                    MvcResult result = mockMvc.perform(post("/orders")
                                    .with(user("cashier").roles("USER"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    String content = result.getResponse().getContentAsString();
                    String errorMsg = result.getResponse().getErrorMessage();
                    boolean isQuotaMsg = (content != null && content.contains("giới hạn lượt sử dụng"))
                            || (errorMsg != null && errorMsg.contains("giới hạn lượt sử dụng"));

                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 400 && isQuotaMsg) {
                        quotaExceededCount.incrementAndGet();
                    } else {
                        unexpectedErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Threads ready");
        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Threads completed");
        executor.shutdown();

        assertEquals(0, unexpectedErrorCount.get(), "No unexpected errors should occur");
        assertEquals(2, successCount.get(), "Exactly 2 concurrent checkouts must succeed for usageLimit=2");
        assertEquals(6, quotaExceededCount.get(), "Remaining 6 checkouts must be rejected with 400 Bad Request");

        PromotionEntity updatedCoupon = promotionRepository.findByPromotionId(coupon.getPromotionId()).orElseThrow();
        assertEquals(2, updatedCoupon.getTimesUsed(), "Database timesUsed must not exceed 2");
    }

    @Test
    @DisplayName("6. Administrative safety guards: admin self-deletion and category deletion restrictions return HTTP 400")
    void testAdministrativeSafetyGuards_ReturnBadRequest() throws Exception {
        // 6a. Admin self-deletion restriction
        String adminEmail = "root.admin." + UUID.randomUUID().toString().substring(0, 6) + "@test.com";
        UserEntity adminUser = userRepository.save(UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .name("Super Admin")
                .email(adminEmail)
                .password("encoded_secret")
                .role("ROLE_ADMIN")
                .build());

        mockMvc.perform(delete("/admin/users/" + adminUser.getUserId())
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason(containsString("Không thể tự xóa tài khoản đang đăng nhập")));

        // 6b. Category deletion restriction when items exist
        mockMvc.perform(delete("/admin/categories/" + testCategory.getCategoryId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason(containsString("Không thể xóa danh mục đang chứa")));

        // 6c. Verify Unified Activity Logging preserves audit entries
        assertFalse(activityLogRepository.findAll().isEmpty(), "Activity logs must be recorded during system operations");
    }

    @Test
    @DisplayName("7. Pagination contracts for customers, inventory transactions, and latest orders")
    void testPaginationContracts() throws Exception {
        // 7a. Customer pagination
        for (int i = 1; i <= 5; i++) {
            customerRepository.save(CustomerEntity.builder()
                    .customerId("CUST-PAG-" + i + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .name("Page Customer " + i)
                    .phoneNumber("091234500" + i)
                    .orderCount(0)
                    .totalSpent(0.0)
                    .build());
        }

        mockMvc.perform(get("/customers")
                        .param("page", "0")
                        .param("size", "2")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.pageable.pageNumber", is(0)))
                .andExpect(jsonPath("$.pageable.pageSize", is(2)))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(5)));

        // 7b. Inventory transactions pagination
        mockMvc.perform(get("/admin/inventory/transactions")
                        .param("page", "0")
                        .param("size", "1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.pageable.pageNumber", is(0)))
                .andExpect(jsonPath("$.pageable.pageSize", is(1)));

        // 7c. Latest orders fixed top-10 limit (seed 12 orders and assert strict 10-record truncation)
        for (int i = 0; i < 12; i++) {
            orderEntityRepository.save(OrderEntity.builder()
                    .orderId("ORD-PAG-" + i + "-" + UUID.randomUUID().toString().substring(0, 4))
                    .subtotal(100000.0)
                    .grandTotal(110000.0)
                    .tax(10000.0)
                    .discountAmount(0.0)
                    .paymentMethod(PaymentMethod.CASH)
                    .paymentDetails(PaymentDetails.builder()
                            .orderId("ORD-PAG-" + i)
                            .status(PaymentDetails.PaymentStatus.COMPLETED)
                            .build())
                    .createdAt(LocalDateTime.now().minusMinutes(i))
                    .items(new ArrayList<>())
                    .build());
        }

        mockMvc.perform(get("/orders/latest")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", is(10)));
    }
}
