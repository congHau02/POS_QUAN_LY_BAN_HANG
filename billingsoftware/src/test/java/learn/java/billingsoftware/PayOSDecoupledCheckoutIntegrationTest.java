package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.entity.InventoryTransactionEntity;
import learn.java.billingsoftware.entity.ItemEntity;
import learn.java.billingsoftware.entity.OrderEntity;
import learn.java.billingsoftware.entity.TransactionType;
import learn.java.billingsoftware.entity.VariantEntity;
import learn.java.billingsoftware.io.OrderRequest;
import learn.java.billingsoftware.io.PaymentDetails;
import learn.java.billingsoftware.io.PaymentMethod;
import learn.java.billingsoftware.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import learn.java.billingsoftware.entity.PromotionEntity;
import learn.java.billingsoftware.entity.PromotionType;
import learn.java.billingsoftware.entity.DiscountType;
import learn.java.billingsoftware.repository.PromotionRepository;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PayOSDecoupledCheckoutIntegrationTest {

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
    private PromotionRepository promotionRepository;

    @MockitoBean
    private PayOS payOS;

    @MockitoBean
    private PaymentRequestsService paymentRequestsService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ItemEntity testItem;
    private VariantEntity testVariant;

    @BeforeEach
    void setUp() {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryId("CAT-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Beverages")
                .bgColor("#444444")
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId("ITEM-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Matcha Latte")
                .price(new BigDecimal("60000.00"))
                .category(category)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-MATCHA-L")
                .basePrice(new BigDecimal("60000.00"))
                .cachedStockQuantity(10)
                .item(testItem)
                .build());

        // Initial inventory transaction setting stock to 10
        inventoryTransactionRepository.saveAndFlush(InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(testVariant)
                .transactionType(TransactionType.IN)
                .quantity(10)
                .referenceId("INIT-STOCK")
                .note("Initial stock for testing")
                .build());
    }

    @AfterEach
    void tearDown() {
        inventoryTransactionRepository.deleteAll();
        orderEntityRepository.deleteAll();
        promotionRepository.deleteAll();
        variantRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @DisplayName("PayOS call executes outside active database transaction and successfully updates payment details")
    void testPayOSCallExecutesOutsideActiveTransactionAndUpdatesPaymentDetails() throws Exception {
        AtomicBoolean wasTransactionActiveDuringGatewayCall = new AtomicBoolean(true);

        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenAnswer(invocation -> {
            // Assert that the HikariCP database transaction is NOT held open during remote HTTP call
            wasTransactionActiveDuringGatewayCall.set(TransactionSynchronizationManager.isActualTransactionActive());

            return CreatePaymentLinkResponse.builder()
                    .bin("970403")
                    .accountNumber("123456789")
                    .accountName("TEST STORE")
                    .amount(66000L)
                    .description("TEST PAYOS")
                    .orderCode(101L)
                    .currency("VND")
                    .paymentLinkId("LINK-PAYOS-101")
                    .status(PaymentLinkStatus.PENDING)
                    .checkoutUrl("https://payos.vn/checkout/101")
                    .qrCode("00020101...")
                    .build();
        });

        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(60000.00)
                .quantity(1)
                .build();

        OrderRequest request = OrderRequest.builder()
                .customerName("Checkout User")
                .phoneNumber("0911222333")
                .paymentMethod("PAYOS")
                .cartItems(List.of(itemRequest))
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.paymentMethod", is("PAYOS")))
                .andExpect(jsonPath("$.paymentDetails.status", is("PENDING")))
                .andExpect(jsonPath("$.paymentDetails.paymentLinkId", is("LINK-PAYOS-101")))
                .andExpect(jsonPath("$.paymentDetails.checkoutUrl", is("https://payos.vn/checkout/101")))
                .andExpect(jsonPath("$.paymentDetails.qrCode", is("00020101...")));

        assertFalse(wasTransactionActiveDuringGatewayCall.get(),
                "Remote PayOS HTTP call must execute outside of open database transaction to prevent HikariCP starvation");

        // Verify variant cached stock decremented from 10 to 9
        VariantEntity updatedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(9, updatedVariant.getCachedStockQuantity());
    }

    @Test
    @DisplayName("PayOS gateway failure triggers automated compensating stock reversal and returns 502 Bad Gateway")
    void testPayOSGatewayFailureTriggersCompensatingStockReversalAndReturnsBadGateway() throws Exception {
        AtomicBoolean wasTransactionActiveDuringGatewayCall = new AtomicBoolean(true);

        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenAnswer(invocation -> {
            wasTransactionActiveDuringGatewayCall.set(TransactionSynchronizationManager.isActualTransactionActive());
            throw new RuntimeException("PayOS upstream gateway timed out");
        });

        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(60000.00)
                .quantity(2)
                .build();

        OrderRequest request = OrderRequest.builder()
                .customerName("Failing User")
                .phoneNumber("0999888777")
                .paymentMethod("PAYOS")
                .cartItems(List.of(itemRequest))
                .build();

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway());

        assertFalse(wasTransactionActiveDuringGatewayCall.get(),
                "Remote PayOS call should execute outside active database transaction");

        // Verify that the order was persisted and marked CANCELLED
        List<OrderEntity> allOrders = orderEntityRepository.findAll();
        assertFalse(allOrders.isEmpty(), "Order should be persisted in database");
        OrderEntity cancelledOrder = allOrders.get(0);
        assertNotNull(cancelledOrder.getPaymentDetails());
        assertEquals(PaymentDetails.PaymentStatus.CANCELLED, cancelledOrder.getPaymentDetails().getStatus(),
                "Failed PayOS order must be marked CANCELLED");

        // Verify stock is restored to 10
        VariantEntity revertedVariant = variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow();
        assertEquals(10, revertedVariant.getCachedStockQuantity(),
                "Reserved stock must be replenished back to 10 via automated compensation");

        // Verify compensating IN ledger transaction exists for the cancelled order
        List<InventoryTransactionEntity> transactions = inventoryTransactionRepository
                .findByVariant_VariantIdOrderByCreatedAtDesc(testVariant.getVariantId());
        boolean hasCompensatingInTx = transactions.stream()
                .anyMatch(tx -> tx.getTransactionType() == TransactionType.IN
                        && cancelledOrder.getOrderId().equals(tx.getReferenceId())
                        && tx.getQuantity() == 2);
        assertTrue(hasCompensatingInTx, "Must record a compensating IN transaction matching the cancelled order");
    }

    @Test
    @DisplayName("PayOS payload balances with grandTotal using aggregated single item for discounted and taxed cart")
    void testPayOSPayloadBalancesWithGrandTotalForDiscountedAndTaxedCart() throws Exception {
        AtomicReference<CreatePaymentLinkRequest> capturedRequest = new AtomicReference<>();

        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenAnswer(invocation -> {
            CreatePaymentLinkRequest req = invocation.getArgument(0);
            capturedRequest.set(req);

            return CreatePaymentLinkResponse.builder()
                    .bin("970403")
                    .accountNumber("123456789")
                    .accountName("TEST STORE")
                    .amount(req.getAmount())
                    .description(req.getDescription())
                    .orderCode(req.getOrderCode())
                    .currency("VND")
                    .paymentLinkId("LINK-PAYOS-" + req.getOrderCode())
                    .status(PaymentLinkStatus.PENDING)
                    .checkoutUrl("https://payos.vn/checkout/" + req.getOrderCode())
                    .qrCode("00020101...")
                    .build();
        });

        // Setup promotion: 10,000 VND fixed discount coupon
        promotionRepository.save(PromotionEntity.builder()
                .promotionId("PROMO-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Discount 10k")
                .type(PromotionType.COUPON)
                .code("DISC10")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(10000.0))
                .isActive(true)
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(1))
                .build());

        OrderRequest.OrderItemRequest itemRequest = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(60000.00)
                .quantity(2)
                .build();

        OrderRequest request = OrderRequest.builder()
                .customerName("Discounted Customer")
                .phoneNumber("0912345678")
                .couponCode("DISC10")
                .paymentMethod("PAYOS")
                .cartItems(List.of(itemRequest))
                .build();

        // Subtotal = 120,000; Discount = 10,000; DiscountedSubtotal = 110,000; Tax (10%) = 11,000; GrandTotal = 121,000
        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.subtotal", is(120000.0)))
                .andExpect(jsonPath("$.discountAmount", is(10000.0)))
                .andExpect(jsonPath("$.tax", is(11000.0)))
                .andExpect(jsonPath("$.grandTotal", is(121000.0)))
                .andExpect(jsonPath("$.paymentDetails.status", is("PENDING")));

        CreatePaymentLinkRequest req = capturedRequest.get();
        assertNotNull(req, "PayOS request should have been dispatched");
        assertEquals(121000L, req.getAmount(), "Payment request amount must match grandTotal");

        assertNotNull(req.getItems());
        assertEquals(1, req.getItems().size(), "Discounted and taxed cart must be collapsed into 1 aggregated item");
        PaymentLinkItem aggregatedItem = req.getItems().get(0);
        assertEquals("Don hang #" + req.getOrderCode(), aggregatedItem.getName());
        assertEquals(121000L, aggregatedItem.getPrice());
        assertEquals(1, aggregatedItem.getQuantity());

        // Strict PayOS balancing invariant: sum(price * quantity) == amount
        long lineSum = aggregatedItem.getPrice() * aggregatedItem.getQuantity();
        assertEquals(req.getAmount(), lineSum, "PayOS payload item sum must balance exactly with amount");
    }
}
