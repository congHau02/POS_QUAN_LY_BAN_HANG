package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.*;
import learn.java.billingsoftware.io.*;
import learn.java.billingsoftware.repository.*;
import learn.java.billingsoftware.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class OrderCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private OrderEntityRepository orderEntityRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ItemEntity testItem;
    private VariantEntity testVariant;
    private CustomerEntity testCustomer;
    private PromotionEntity testCoupon;

    @BeforeEach
    void setUp() {
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryId("CAT-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Coffee")
                .bgColor("#333333")
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId("ITEM-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Cold Brew")
                .price(new BigDecimal("50000.00"))
                .category(category)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-CB-M")
                .basePrice(new BigDecimal("50000.00"))
                .cachedStockQuantity(0)
                .attributes(Map.of("Size", "M"))
                .item(testItem)
                .build());

        // In 10 units
        inventoryService.recordTransaction(InventoryTransactionRequest.builder()
                .variantId(testVariant.getVariantId())
                .transactionType(TransactionType.IN)
                .quantity(10)
                .referenceId("INIT-PO")
                .build());

        testCustomer = customerRepository.save(CustomerEntity.builder()
                .customerId(UUID.randomUUID().toString())
                .name("Alice Wonderland")
                .phoneNumber("0918889999")
                .totalSpent(0.0)
                .orderCount(0)
                .build());

        testCoupon = promotionRepository.save(PromotionEntity.builder()
                .promotionId(UUID.randomUUID().toString())
                .name("Save 20K")
                .type(PromotionType.COUPON)
                .code("SAVE20K")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(20000.0))
                .minOrderAmount(BigDecimal.valueOf(50000.0))
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .usageLimit(5)
                .timesUsed(0)
                .isActive(true)
                .build());
    }

    @Test
    void testCheckout_withLinkedCustomer_updatesCustomerSpendAndOrderCount() throws Exception {
        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(2)
                .build();

        OrderRequest request = OrderRequest.builder()
                .customerName("Alice Wonderland")
                .phoneNumber("0918889999")
                .cartItems(List.of(itemRequest))
                .paymentMethod("CASH")
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.customerId", is(testCustomer.getCustomerId())))
                .andExpect(jsonPath("$.customerName", is("Alice Wonderland")))
                .andExpect(jsonPath("$.subtotal", is(100000.00)))
                .andExpect(jsonPath("$.discountAmount", is(0.0)))
                .andExpect(jsonPath("$.tax", is(10000.00)))
                .andExpect(jsonPath("$.grandTotal", is(110000.00)));

        // Verify customer CRM sync
        CustomerEntity updatedCustomer = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(1, updatedCustomer.getOrderCount());
        assertEquals(110000.00, updatedCustomer.getTotalSpent(), 0.01);
    }

    @Test
    void testCheckout_withCouponPromotion_appliesDiscountAndIncrementsUsage() throws Exception {
        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(2)
                .build();

        OrderRequest request = OrderRequest.builder()
                .couponCode("SAVE20K")
                .cartItems(List.of(itemRequest))
                .paymentMethod("CASH")
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.subtotal", is(100000.00)))
                .andExpect(jsonPath("$.discountAmount", is(20000.00)))
                .andExpect(jsonPath("$.tax", is(8000.00)))
                .andExpect(jsonPath("$.grandTotal", is(88000.00)))
                .andExpect(jsonPath("$.promotionId", is(testCoupon.getPromotionId())))
                .andExpect(jsonPath("$.promotionName", is("Save 20K")));

        // Verify atomic usage counter increment
        PromotionEntity updatedPromo = promotionRepository.findByPromotionId(testCoupon.getPromotionId()).orElseThrow();
        assertEquals(1, updatedPromo.getTimesUsed());
    }

    @Test
    void testCheckout_recalculatesDiscountServerSide_ignoringClientTamperedTotals() throws Exception {
        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(2)
                .build();

        // Client maliciously attempts to tamper with totals
        OrderRequest request = OrderRequest.builder()
                .couponCode("SAVE20K")
                .cartItems(List.of(itemRequest))
                .subtotal(1000.00) // Tampered
                .discountAmount(99000.00) // Tampered
                .tax(100.00) // Tampered
                .grandTotal(500.00) // Tampered
                .paymentMethod("CASH")
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal", is(100000.00))) // Overridden by server
                .andExpect(jsonPath("$.discountAmount", is(20000.00))) // Overridden by server
                .andExpect(jsonPath("$.tax", is(8000.00))) // Overridden by server
                .andExpect(jsonPath("$.grandTotal", is(88000.00))); // Overridden by server
    }

    @Test
    void testCheckout_couponUsageLimitExceeded_blocksOrder() throws Exception {
        PromotionEntity limitedPromo = promotionRepository.save(PromotionEntity.builder()
                .promotionId(UUID.randomUUID().toString())
                .name("Single Use Promo")
                .type(PromotionType.COUPON)
                .code("SINGLE")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(10000.0))
                .usageLimit(1)
                .timesUsed(1) // Already at limit
                .isActive(true)
                .build());

        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(1)
                .build();

        OrderRequest request = OrderRequest.builder()
                .couponCode("SINGLE")
                .cartItems(List.of(itemRequest))
                .paymentMethod("CASH")
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PROMOTION_USAGE_EXHAUSTED")))
                .andExpect(jsonPath("$.message", is("Mã giảm giá đã hết lượt sử dụng")));
    }

    @Test
    void testCheckout_withCustomerAndCoupon_updatesBothSimultaneously() throws Exception {
        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(2)
                .build();

        OrderRequest request = OrderRequest.builder()
                .customerId(testCustomer.getCustomerId())
                .phoneNumber("0918889999")
                .couponCode("SAVE20K")
                .cartItems(List.of(itemRequest))
                .paymentMethod("CASH")
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId", is(testCustomer.getCustomerId())))
                .andExpect(jsonPath("$.discountAmount", is(20000.00)))
                .andExpect(jsonPath("$.grandTotal", is(88000.00)));

        // Verify customer updated
        CustomerEntity updatedCust = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(1, updatedCust.getOrderCount());
        assertEquals(88000.00, updatedCust.getTotalSpent(), 0.01);

        // Verify promo updated
        PromotionEntity updatedPromo = promotionRepository.findByPromotionId(testCoupon.getPromotionId()).orElseThrow();
        assertEquals(1, updatedPromo.getTimesUsed());

        // Verify stock deducted
        VariantEntity updatedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(8, updatedVariant.getCachedStockQuantity()); // 10 - 2 = 8
    }

    @Test
    void testCheckout_deductsInventoryLedgerAndAllowsNegativeStock() throws Exception {
        // Current stock is 10. Order 15 items -> stock becomes -5
        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(15)
                .build();

        OrderRequest request = OrderRequest.builder()
                .cartItems(List.of(itemRequest))
                .paymentMethod("CASH")
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        VariantEntity updatedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(-5, updatedVariant.getCachedStockQuantity());
    }

    @Test
    void testCancelOrder_revertsInventoryPromotionAndCustomerMetrics() throws Exception {
        // 1. Create a pending order with customer and coupon
        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.00)
                .quantity(2)
                .build();

        // Stock starts at 10. We create a pending order directly
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.PENDING);

        OrderEntity pendingOrder = OrderEntity.builder()
                .orderId("ORD-TEST-PENDING")
                .customerId(testCustomer.getCustomerId())
                .customerName(testCustomer.getName())
                .phoneNumber(testCustomer.getPhoneNumber())
                .subtotal(100000.0)
                .discountAmount(20000.0)
                .tax(8000.0)
                .grandTotal(88000.0)
                .promotionId(testCoupon.getPromotionId())
                .promotionName(testCoupon.getName())
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(pd)
                .build();

        OrderItemEntity orderItem = OrderItemEntity.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.0)
                .quantity(2)
                .order(pendingOrder)
                .build();
        pendingOrder.setItems(List.of(orderItem));
        pendingOrder = orderEntityRepository.save(pendingOrder);

        // Deduct inventory to simulate order creation state
        inventoryService.recordTransaction(InventoryTransactionRequest.builder()
                .variantId(testVariant.getVariantId())
                .transactionType(TransactionType.OUT)
                .quantity(2)
                .referenceId(pendingOrder.getOrderId())
                .build());

        // Increment customer metrics
        testCustomer.setOrderCount(1);
        testCustomer.setTotalSpent(88000.0);
        customerRepository.save(testCustomer);

        // Increment coupon timesUsed
        testCoupon.setTimesUsed(1);
        promotionRepository.save(testCoupon);

        // Verify precondition: stock is 8 (10 - 2)
        assertEquals(8, variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow().getCachedStockQuantity());

        // 2. Perform Cancel API
        mockMvc.perform(post("/orders/" + pendingOrder.getOrderId() + "/cancel")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(pendingOrder.getOrderId())))
                .andExpect(jsonPath("$.paymentDetails.status", is("CANCELLED")));

        // 3. Verify stock restored back to 10
        VariantEntity revertedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(10, revertedVariant.getCachedStockQuantity());

        // Verify compensating IN ledger transaction exists for this cancelled order
        final String cancelledOrderId = pendingOrder.getOrderId();
        List<InventoryTransactionEntity> transactions = inventoryTransactionRepository.findByVariant_VariantIdOrderByCreatedAtDesc(testVariant.getVariantId());
        boolean hasCompensatingInTx = transactions.stream()
                .anyMatch(tx -> tx.getTransactionType() == TransactionType.IN
                        && cancelledOrderId.equals(tx.getReferenceId())
                        && tx.getQuantity() == 2);
        assertTrue(hasCompensatingInTx, "Must record a compensating IN transaction for cancelled order");

        // 4. Verify promotion timesUsed decremented to 0
        PromotionEntity revertedPromo = promotionRepository.findByPromotionId(testCoupon.getPromotionId()).orElseThrow();
        assertEquals(0, revertedPromo.getTimesUsed());

        // 5. Verify customer metrics remain unchanged (pending orders do not accrue spending per Ticket 04)
        CustomerEntity customerAfterCancel = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(1, customerAfterCancel.getOrderCount());
        assertEquals(88000.0, customerAfterCancel.getTotalSpent(), 0.01);
    }

    @Test
    void testCancelOrder_rejectsNonPendingOrder() throws Exception {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.COMPLETED);

        OrderEntity completedOrder = OrderEntity.builder()
                .orderId("ORD-TEST-COMPLETED")
                .grandTotal(50000.0)
                .paymentMethod(PaymentMethod.CASH)
                .paymentDetails(pd)
                .build();
        completedOrder = orderEntityRepository.save(completedOrder);

        mockMvc.perform(post("/orders/" + completedOrder.getOrderId() + "/cancel")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSwitchToCash_convertsPendingOrderToCashAndCompleted() throws Exception {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.PENDING);

        OrderEntity pendingOrder = OrderEntity.builder()
                .orderId("ORD-TEST-SWITCH")
                .grandTotal(50000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(pd)
                .build();
        pendingOrder = orderEntityRepository.save(pendingOrder);

        // Precondition stock is 10
        int stockBefore = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow().getCachedStockQuantity();

        mockMvc.perform(post("/orders/" + pendingOrder.getOrderId() + "/switch-to-cash")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod", is("CASH")))
                .andExpect(jsonPath("$.paymentDetails.status", is("COMPLETED")));

        // Verify stock was not deducted again
        int stockAfter = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow().getCachedStockQuantity();
        assertEquals(stockBefore, stockAfter);
    }

    @Test
    void testSwitchToCash_rejectsNonPendingOrder() throws Exception {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.CANCELLED);

        OrderEntity cancelledOrder = OrderEntity.builder()
                .orderId("ORD-TEST-CANCELLED")
                .grandTotal(50000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(pd)
                .build();
        cancelledOrder = orderEntityRepository.save(cancelledOrder);

        mockMvc.perform(post("/orders/" + cancelledOrder.getOrderId() + "/switch-to-cash")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isBadRequest());
    }
}
