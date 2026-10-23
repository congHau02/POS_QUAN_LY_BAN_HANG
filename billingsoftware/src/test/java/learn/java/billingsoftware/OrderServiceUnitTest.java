package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.CustomerEntity;
import vn.payos.model.webhooks.WebhookData;
import learn.java.billingsoftware.entity.InventoryTransactionEntity;
import learn.java.billingsoftware.entity.OrderEntity;
import learn.java.billingsoftware.entity.OrderItemEntity;
import learn.java.billingsoftware.entity.VariantEntity;
import learn.java.billingsoftware.io.*;
import learn.java.billingsoftware.repository.*;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.PromotionService;
import learn.java.billingsoftware.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import learn.java.billingsoftware.entity.TransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock
    private OrderEntityRepository orderEntityRepository;
    @Mock
    private PayOS payOS;
    @Mock
    private PaymentRequestsService paymentRequestsService;
    @Mock
    private VariantRepository variantRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock
    private PromotionService promotionService;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ActivityLogService activityLogService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return mock(TransactionStatus.class);
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });

        orderService = new OrderServiceImpl(
                orderEntityRepository,
                payOS,
                variantRepository,
                inventoryTransactionRepository,
                promotionService,
                promotionRepository,
                customerRepository,
                activityLogService,
                objectMapper,
                transactionTemplate
        );
        ReflectionTestUtils.setField(orderService, "payosReturnUrl", "https://app.com/success");
        ReflectionTestUtils.setField(orderService, "payosCancelUrl", "https://app.com/cancel");
    }

    @Test
    @DisplayName("Downstream references (PayOS descriptions, activity logs, inventory transactions) consume the enhanced unique order code")
    void testDownstreamReferencesConsumeEnhancedOrderCode() throws Exception {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse mockPayOsResponse = CreatePaymentLinkResponse.builder()
                .bin("970403")
                .accountNumber("123456789")
                .accountName("TEST STORE")
                .amount(110000L)
                .description("TEST")
                .orderCode(888L)
                .currency("VND")
                .paymentLinkId("LINK-001")
                .checkoutUrl("https://payos.vn/checkout")
                .qrCode("000201...")
                .status(PaymentLinkStatus.PENDING)
                .build();
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(mockPayOsResponse);

        PromotionEvaluationResponse evalResponse = PromotionEvaluationResponse.builder()
                .subtotal(BigDecimal.valueOf(100000.0))
                .discountAmount(BigDecimal.ZERO)
                .tax(BigDecimal.valueOf(10000.0))
                .grandTotal(BigDecimal.valueOf(110000.0))
                .build();
        when(promotionService.evaluatePromotion(any(PromotionEvaluationRequest.class))).thenReturn(evalResponse);

        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(888L);
            }
            if (entity.getOrderId() == null) {
                entity.onCreate();
            }
            return entity;
        });

        VariantEntity variant = VariantEntity.builder()
                .variantId("VAR-123")
                .cachedStockQuantity(20)
                .build();
        when(variantRepository.findByVariantId("VAR-123")).thenReturn(Optional.of(variant));

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setItemId("ITEM-1");
        itemReq.setVariantId("VAR-123");
        itemReq.setName("Test Product");
        itemReq.setPrice(50000.0);
        itemReq.setQuantity(2);

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Nguyen Van A");
        request.setPhoneNumber("0912345678");
        request.setPaymentMethod("PAYOS");
        request.setCartItems(List.of(itemReq));

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response.getOrderId());
        assertTrue(response.getOrderId().startsWith("ORD"));
        String generatedOrderId = response.getOrderId();

        // 1. Verify Inventory Transaction referenceId consumes the enhanced unique order code
        ArgumentCaptor<InventoryTransactionEntity> invCaptor = ArgumentCaptor.forClass(InventoryTransactionEntity.class);
        verify(inventoryTransactionRepository).saveAndFlush(invCaptor.capture());
        InventoryTransactionEntity savedTx = invCaptor.getValue();
        assertEquals(generatedOrderId, savedTx.getReferenceId(), "Inventory transaction referenceId must match orderId");
        assertTrue(savedTx.getNote().contains(generatedOrderId));

        // 2. Verify Activity Log entityId consumes the enhanced unique order code
        verify(activityLogService).logActivity(
                eq("CREATE"),
                eq("ORDER"),
                eq(generatedOrderId),
                contains(generatedOrderId)
        );

        // 3. Verify PayOS CreatePaymentLinkRequest description consumes the enhanced unique order code without overflow (<= 25 chars)
        ArgumentCaptor<CreatePaymentLinkRequest> payOsCaptor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(paymentRequestsService).create(payOsCaptor.capture());
        CreatePaymentLinkRequest payOsReq = payOsCaptor.getValue();

        assertNotNull(payOsReq.getDescription());
        assertTrue(payOsReq.getDescription().length() <= 25,
                "PayOS description must not exceed 25 characters to avoid PayOS 400 rejection");
        assertEquals(generatedOrderId, payOsReq.getDescription(),
                "PayOS description should consume enhanced orderId without length overflow");

        // 4. Verify cachedStockQuantity is decremented directly without calculating ledger sum
        assertEquals(18, variant.getCachedStockQuantity(), "Stock should be decremented directly from 20 to 18");
        verify(inventoryTransactionRepository, never()).calculateStockByVariantId(any());
    }

    @Test
    @DisplayName("PayOS description is safely truncated to 25 characters if orderId exceeds 25 characters")
    void testPayOsDescriptionTruncationOnOverflow() throws Exception {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse mockPayOsResponse = CreatePaymentLinkResponse.builder()
                .bin("970403")
                .accountNumber("123456789")
                .accountName("TEST STORE")
                .amount(50000L)
                .description("TEST")
                .orderCode(999L)
                .currency("VND")
                .paymentLinkId("LINK-002")
                .checkoutUrl("https://payos.vn/checkout")
                .qrCode("000201...")
                .status(PaymentLinkStatus.PENDING)
                .build();
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(mockPayOsResponse);

        PromotionEvaluationResponse evalResponse = PromotionEvaluationResponse.builder()
                .subtotal(BigDecimal.valueOf(50000.0))
                .discountAmount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .grandTotal(BigDecimal.valueOf(50000.0))
                .build();
        when(promotionService.evaluatePromotion(any(PromotionEvaluationRequest.class))).thenReturn(evalResponse);

        String longOrderId = "ORD-EXTREMELY-LONG-ORDER-IDENTIFIER-EXCEEDING-25-CHARS";
        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            entity.setId(999L);
            entity.setOrderId(longOrderId);
            return entity;
        });

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Tran Van B");
        request.setPhoneNumber("0988888888");
        request.setPaymentMethod("PAYOS");
        request.setCartItems(List.of());

        OrderResponse response = orderService.createOrder(request);

        assertEquals(longOrderId, response.getOrderId());

        ArgumentCaptor<CreatePaymentLinkRequest> payOsCaptor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(paymentRequestsService).create(payOsCaptor.capture());
        CreatePaymentLinkRequest payOsReq = payOsCaptor.getValue();

        assertNotNull(payOsReq.getDescription());
        assertEquals(25, payOsReq.getDescription().length(), "Description should be truncated to exactly 25 chars");
        assertEquals(longOrderId.substring(0, 25), payOsReq.getDescription());
    }
    @Test
    @DisplayName("PayOS payment link details are updated onto the order when gateway returns successfully")
    void testCreateOrderWithPayOS_SuccessUpdatesPaymentDetails() throws Exception {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse mockPayOsResponse = CreatePaymentLinkResponse.builder()
                .bin("970403")
                .accountNumber("123456789")
                .accountName("TEST STORE")
                .amount(50000L)
                .description("TEST SUCCESS")
                .orderCode(777L)
                .currency("VND")
                .paymentLinkId("LINK-PAYOS-777")
                .checkoutUrl("https://payos.vn/checkout/777")
                .qrCode("00020101...")
                .status(PaymentLinkStatus.PENDING)
                .build();
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(mockPayOsResponse);

        PromotionEvaluationResponse evalResponse = PromotionEvaluationResponse.builder()
                .subtotal(BigDecimal.valueOf(50000.0))
                .discountAmount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .grandTotal(BigDecimal.valueOf(50000.0))
                .build();
        when(promotionService.evaluatePromotion(any(PromotionEvaluationRequest.class))).thenReturn(evalResponse);

        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(777L);
            }
            if (entity.getOrderId() == null) {
                entity.onCreate();
            }
            return entity;
        });

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Nguyen Van C");
        request.setPhoneNumber("0977777777");
        request.setPaymentMethod("PAYOS");
        request.setCartItems(List.of());

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response.getPaymentDetails());
        assertEquals("LINK-PAYOS-777", response.getPaymentDetails().getPaymentLinkId());
        assertEquals("https://payos.vn/checkout/777", response.getPaymentDetails().getCheckoutUrl());
        assertEquals("00020101...", response.getPaymentDetails().getQrCode());
        assertEquals(PaymentDetails.PaymentStatus.PENDING, response.getPaymentDetails().getStatus());
    }

    @Test
    @DisplayName("PayOS gateway failure triggers automated compensating stock reversal and throws 502 Bad Gateway")
    void testCreateOrderWithPayOS_GatewayFailureReleasesAndCompensatesStock() throws Exception {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class)))
                .thenThrow(new RuntimeException("PayOS network timeout"));

        PromotionEvaluationResponse evalResponse = PromotionEvaluationResponse.builder()
                .subtotal(BigDecimal.valueOf(60000.0))
                .discountAmount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .grandTotal(BigDecimal.valueOf(60000.0))
                .build();
        when(promotionService.evaluatePromotion(any(PromotionEvaluationRequest.class))).thenReturn(evalResponse);

        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(555L);
            }
            if (entity.getOrderId() == null) {
                entity.onCreate();
            }
            return entity;
        });

        VariantEntity variant = VariantEntity.builder()
                .variantId("VAR-999")
                .cachedStockQuantity(10)
                .build();
        when(variantRepository.findByVariantId("VAR-999")).thenReturn(Optional.of(variant));

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setItemId("ITEM-999");
        itemReq.setVariantId("VAR-999");
        itemReq.setName("Failed Item");
        itemReq.setPrice(60000.0);
        itemReq.setQuantity(2);

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Failed User");
        request.setPhoneNumber("0955555555");
        request.setPaymentMethod("PAYOS");
        request.setCartItems(List.of(itemReq));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            orderService.createOrder(request);
        });

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Lỗi khi tạo mã thanh toán PayOS"));

        // Verify that compensating IN transaction was recorded
        ArgumentCaptor<InventoryTransactionEntity> invCaptor = ArgumentCaptor.forClass(InventoryTransactionEntity.class);
        verify(inventoryTransactionRepository, atLeast(2)).saveAndFlush(invCaptor.capture());

        List<InventoryTransactionEntity> savedTxs = invCaptor.getAllValues();
        boolean hasCompensatingInTx = savedTxs.stream()
                .anyMatch(tx -> tx.getTransactionType() == TransactionType.IN && tx.getQuantity() == 2);
        assertTrue(hasCompensatingInTx, "Must record a compensating IN transaction to restore reserved stock");

        // Verify activity log captured cancellation
        verify(activityLogService).logActivity(
                eq("CANCEL"),
                eq("ORDER"),
                anyString(),
                contains("due to PayOS failure")
        );

        // Verify cached stock restored directly without calling calculateStockByVariantId
        assertEquals(10, variant.getCachedStockQuantity(), "Stock should be restored to 10 after compensating auto-reversal");
        verify(inventoryTransactionRepository, never()).calculateStockByVariantId(any());
    }

    @Test
    @DisplayName("PayOS payload uses single aggregated item when discounts or taxes cause raw item sum to deviate from grandTotal")
    void testCreateOrderWithPayOS_AggregatesItemWhenDiscountOrTaxDeviatesFromTotal() throws Exception {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse mockPayOsResponse = CreatePaymentLinkResponse.builder()
                .bin("970403")
                .accountNumber("123456789")
                .accountName("TEST STORE")
                .amount(99000L)
                .description("TEST AGGREGATED")
                .orderCode(12345L)
                .currency("VND")
                .paymentLinkId("LINK-PAYOS-12345")
                .checkoutUrl("https://payos.vn/checkout/12345")
                .qrCode("00020101...")
                .status(PaymentLinkStatus.PENDING)
                .build();
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(mockPayOsResponse);

        // Subtotal = 100,000, Discount = 10,000, Tax = 9,000, GrandTotal = 99,000
        PromotionEvaluationResponse evalResponse = PromotionEvaluationResponse.builder()
                .subtotal(BigDecimal.valueOf(100000.0))
                .discountAmount(BigDecimal.valueOf(10000.0))
                .tax(BigDecimal.valueOf(9000.0))
                .grandTotal(BigDecimal.valueOf(99000.0))
                .build();
        when(promotionService.evaluatePromotion(any(PromotionEvaluationRequest.class))).thenReturn(evalResponse);

        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(12345L);
            }
            if (entity.getOrderId() == null) {
                entity.onCreate();
            }
            return entity;
        });

        VariantEntity variant1 = VariantEntity.builder().variantId("VAR-1").cachedStockQuantity(10).build();
        VariantEntity variant2 = VariantEntity.builder().variantId("VAR-2").cachedStockQuantity(10).build();
        when(variantRepository.findByVariantId("VAR-1")).thenReturn(Optional.of(variant1));
        when(variantRepository.findByVariantId("VAR-2")).thenReturn(Optional.of(variant2));

        OrderRequest.OrderItemRequest item1 = new OrderRequest.OrderItemRequest();
        item1.setItemId("ITEM-1");
        item1.setVariantId("VAR-1");
        item1.setName("Product 1");
        item1.setPrice(60000.0);
        item1.setQuantity(1);

        OrderRequest.OrderItemRequest item2 = new OrderRequest.OrderItemRequest();
        item2.setItemId("ITEM-2");
        item2.setVariantId("VAR-2");
        item2.setName("Product 2");
        item2.setPrice(40000.0);
        item2.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Discounted User");
        request.setPhoneNumber("0912345678");
        request.setPaymentMethod("PAYOS");
        request.setCartItems(List.of(item1, item2));

        orderService.createOrder(request);

        ArgumentCaptor<CreatePaymentLinkRequest> captor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(paymentRequestsService).create(captor.capture());
        CreatePaymentLinkRequest capturedReq = captor.getValue();

        assertEquals(99000L, capturedReq.getAmount());
        assertNotNull(capturedReq.getItems());
        assertEquals(1, capturedReq.getItems().size(), "Must collapse to 1 aggregated item when discounted/taxed");

        PaymentLinkItem aggregatedItem = capturedReq.getItems().get(0);
        assertEquals("Don hang #12345", aggregatedItem.getName());
        assertEquals(99000L, aggregatedItem.getPrice());
        assertEquals(1, aggregatedItem.getQuantity());

        // Verify total line sum strictly balances with grandTotal amount
        long itemTotal = aggregatedItem.getPrice() * aggregatedItem.getQuantity();
        assertEquals(capturedReq.getAmount(), itemTotal, "Line items sum must equal total amount");
    }

    @Test
    @DisplayName("PayOS payload retains full itemized breakdown when no discounts or taxes alter item sums")
    void testCreateOrderWithPayOS_RetainsItemizedBreakdownWhenNoDiscountsOrTaxes() throws Exception {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse mockPayOsResponse = CreatePaymentLinkResponse.builder()
                .bin("970403")
                .accountNumber("123456789")
                .accountName("TEST STORE")
                .amount(100000L)
                .description("TEST ITEMIZED")
                .orderCode(54321L)
                .currency("VND")
                .paymentLinkId("LINK-PAYOS-54321")
                .checkoutUrl("https://payos.vn/checkout/54321")
                .qrCode("00020101...")
                .status(PaymentLinkStatus.PENDING)
                .build();
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(mockPayOsResponse);

        // Subtotal = 100,000, Discount = 0, Tax = 0, GrandTotal = 100,000
        PromotionEvaluationResponse evalResponse = PromotionEvaluationResponse.builder()
                .subtotal(BigDecimal.valueOf(100000.0))
                .discountAmount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .grandTotal(BigDecimal.valueOf(100000.0))
                .build();
        when(promotionService.evaluatePromotion(any(PromotionEvaluationRequest.class))).thenReturn(evalResponse);

        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(54321L);
            }
            if (entity.getOrderId() == null) {
                entity.onCreate();
            }
            return entity;
        });

        VariantEntity variant1 = VariantEntity.builder().variantId("VAR-1").cachedStockQuantity(10).build();
        VariantEntity variant2 = VariantEntity.builder().variantId("VAR-2").cachedStockQuantity(10).build();
        when(variantRepository.findByVariantId("VAR-1")).thenReturn(Optional.of(variant1));
        when(variantRepository.findByVariantId("VAR-2")).thenReturn(Optional.of(variant2));

        OrderRequest.OrderItemRequest item1 = new OrderRequest.OrderItemRequest();
        item1.setItemId("ITEM-1");
        item1.setVariantId("VAR-1");
        item1.setName("Product 1");
        item1.setPrice(60000.0);
        item1.setQuantity(1);

        OrderRequest.OrderItemRequest item2 = new OrderRequest.OrderItemRequest();
        item2.setItemId("ITEM-2");
        item2.setVariantId("VAR-2");
        item2.setName("Product 2");
        item2.setPrice(20000.0);
        item2.setQuantity(2);

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Regular User");
        request.setPhoneNumber("0912345678");
        request.setPaymentMethod("PAYOS");
        request.setCartItems(List.of(item1, item2));

        orderService.createOrder(request);

        ArgumentCaptor<CreatePaymentLinkRequest> captor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(paymentRequestsService).create(captor.capture());
        CreatePaymentLinkRequest capturedReq = captor.getValue();

        assertEquals(100000L, capturedReq.getAmount());
        assertNotNull(capturedReq.getItems());
        assertEquals(2, capturedReq.getItems().size(), "Must retain 2 items when no discounts or taxes alter sum");

        PaymentLinkItem capturedItem1 = capturedReq.getItems().get(0);
        assertEquals("Product 1", capturedItem1.getName());
        assertEquals(60000L, capturedItem1.getPrice());
        assertEquals(1, capturedItem1.getQuantity());

        PaymentLinkItem capturedItem2 = capturedReq.getItems().get(1);
        assertEquals("Product 2", capturedItem2.getName());
        assertEquals(20000L, capturedItem2.getPrice());
        assertEquals(2, capturedItem2.getQuantity());

        long sumItems = capturedReq.getItems().stream()
                .mapToLong(i -> i.getPrice() * i.getQuantity())
                .sum();
        assertEquals(capturedReq.getAmount(), sumItems, "Itemized sum must balance with grandTotal");
    }

    @Test
    @DisplayName("Creating a PENDING PayOS order does not accrue customer spending or order count")
    void testCreatePendingOrderDoesNotAccrueCustomerSpending() throws Exception {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse mockPayOsResponse = CreatePaymentLinkResponse.builder()
                .bin("970403")
                .accountNumber("123456789")
                .accountName("TEST STORE")
                .amount(110000L)
                .description("TEST")
                .orderCode(888L)
                .currency("VND")
                .paymentLinkId("LINK-001")
                .checkoutUrl("https://payos.vn/checkout")
                .qrCode("000201...")
                .status(PaymentLinkStatus.PENDING)
                .build();
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(mockPayOsResponse);

        PromotionEvaluationResponse evalResponse = PromotionEvaluationResponse.builder()
                .subtotal(BigDecimal.valueOf(100000.0))
                .discountAmount(BigDecimal.ZERO)
                .tax(BigDecimal.valueOf(10000.0))
                .grandTotal(BigDecimal.valueOf(110000.0))
                .build();
        when(promotionService.evaluatePromotion(any(PromotionEvaluationRequest.class))).thenReturn(evalResponse);

        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(888L);
            }
            if (entity.getOrderId() == null) {
                entity.onCreate();
            }
            return entity;
        });

        VariantEntity variant = VariantEntity.builder()
                .variantId("VAR-123")
                .cachedStockQuantity(20)
                .build();
        when(variantRepository.findByVariantId("VAR-123")).thenReturn(Optional.of(variant));

        CustomerEntity customer = CustomerEntity.builder()
                .customerId("CUST-1")
                .name("Nguyen Van A")
                .phoneNumber("0912345678")
                .orderCount(2)
                .totalSpent(150000.0)
                .build();
        when(customerRepository.findByCustomerId("CUST-1")).thenReturn(Optional.of(customer));

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setItemId("ITEM-1");
        itemReq.setVariantId("VAR-123");
        itemReq.setName("Test Product");
        itemReq.setPrice(50000.0);
        itemReq.setQuantity(2);

        OrderRequest request = new OrderRequest();
        request.setCustomerId("CUST-1");
        request.setCustomerName("Nguyen Van A");
        request.setPhoneNumber("0912345678");
        request.setPaymentMethod("PAYOS");
        request.setCartItems(List.of(itemReq));

        orderService.createOrder(request);

        assertEquals(2, customer.getOrderCount(), "Order count must remain unchanged for PENDING order");
        assertEquals(150000.0, customer.getTotalSpent(), 0.001, "Total spent must remain unchanged for PENDING order");
    }

    @Test
    @DisplayName("Creating a CASH order accrues customer spending and order count immediately")
    void testCreateCashOrderAccruesCustomerSpendingImmediately() throws Exception {
        PromotionEvaluationResponse evalResponse = PromotionEvaluationResponse.builder()
                .subtotal(BigDecimal.valueOf(100000.0))
                .discountAmount(BigDecimal.ZERO)
                .tax(BigDecimal.valueOf(10000.0))
                .grandTotal(BigDecimal.valueOf(110000.0))
                .build();
        when(promotionService.evaluatePromotion(any(PromotionEvaluationRequest.class))).thenReturn(evalResponse);

        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(777L);
            }
            if (entity.getOrderId() == null) {
                entity.onCreate();
            }
            return entity;
        });

        VariantEntity variant = VariantEntity.builder()
                .variantId("VAR-123")
                .cachedStockQuantity(20)
                .build();
        when(variantRepository.findByVariantId("VAR-123")).thenReturn(Optional.of(variant));

        CustomerEntity customer = CustomerEntity.builder()
                .customerId("CUST-1")
                .name("Nguyen Van A")
                .phoneNumber("0912345678")
                .orderCount(2)
                .totalSpent(150000.0)
                .build();
        when(customerRepository.findByCustomerId("CUST-1")).thenReturn(Optional.of(customer));

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setItemId("ITEM-1");
        itemReq.setVariantId("VAR-123");
        itemReq.setName("Test Product");
        itemReq.setPrice(50000.0);
        itemReq.setQuantity(2);

        OrderRequest request = new OrderRequest();
        request.setCustomerId("CUST-1");
        request.setCustomerName("Nguyen Van A");
        request.setPhoneNumber("0912345678");
        request.setPaymentMethod("CASH");
        request.setCartItems(List.of(itemReq));

        orderService.createOrder(request);

        assertEquals(3, customer.getOrderCount(), "Order count must increment for CASH order");
        assertEquals(260000.0, customer.getTotalSpent(), 0.001, "Total spent must accrue for CASH order");
        verify(customerRepository).save(customer);
    }

    @Test
    @DisplayName("handlePaymentWebhook with code 00 accrues customer spending idempotently")
    void testHandlePaymentWebhookAccruesCustomerSpending() {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.PENDING);

        OrderEntity order = OrderEntity.builder()
                .id(999L)
                .orderId("ORD-999")
                .customerId("CUST-1")
                .customerName("Nguyen Van A")
                .phoneNumber("0912345678")
                .grandTotal(110000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(pd)
                .build();
        when(orderEntityRepository.findById(999L)).thenReturn(Optional.of(order));

        CustomerEntity customer = CustomerEntity.builder()
                .customerId("CUST-1")
                .name("Nguyen Van A")
                .phoneNumber("0912345678")
                .orderCount(1)
                .totalSpent(50000.0)
                .build();
        when(customerRepository.findByCustomerId("CUST-1")).thenReturn(Optional.of(customer));

        WebhookData webhookData = WebhookData.builder()
                .orderCode(999L)
                .amount(110000L)
                .code("00")
                .description("Payment for ORD-999")
                .desc("Payment for ORD-999")
                .accountNumber("123456789")
                .reference("REF123")
                .transactionDateTime("2026-10-23 16:30:00")
                .currency("VND")
                .paymentLinkId("LINK123")
                .build();

        orderService.handlePaymentWebhook(webhookData);

        assertEquals(2, customer.getOrderCount());
        assertEquals(160000.0, customer.getTotalSpent(), 0.001);
        assertEquals(PaymentDetails.PaymentStatus.COMPLETED, order.getPaymentDetails().getStatus());

        // Idempotency: call again
        orderService.handlePaymentWebhook(webhookData);
        assertEquals(2, customer.getOrderCount());
        assertEquals(160000.0, customer.getTotalSpent(), 0.001);
    }

    @Test
    @DisplayName("switchToCash accrues customer spending and marks order COMPLETED")
    void testSwitchToCashAccruesCustomerSpending() {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.PENDING);

        OrderEntity order = OrderEntity.builder()
                .id(555L)
                .orderId("ORD-555")
                .customerId("CUST-1")
                .customerName("Nguyen Van A")
                .phoneNumber("0912345678")
                .grandTotal(75000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(pd)
                .build();
        when(orderEntityRepository.findByOrderId("ORD-555")).thenReturn(Optional.of(order));
        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerEntity customer = CustomerEntity.builder()
                .customerId("CUST-1")
                .name("Nguyen Van A")
                .phoneNumber("0912345678")
                .orderCount(0)
                .totalSpent(0.0)
                .build();
        when(customerRepository.findByCustomerId("CUST-1")).thenReturn(Optional.of(customer));

        orderService.switchToCash("ORD-555");

        assertEquals(PaymentMethod.CASH, order.getPaymentMethod());
        assertEquals(PaymentDetails.PaymentStatus.COMPLETED, order.getPaymentDetails().getStatus());
        assertEquals(1, customer.getOrderCount());
        assertEquals(75000.0, customer.getTotalSpent(), 0.001);
        verify(customerRepository).save(customer);
    }

    @Test
    @DisplayName("cancelOrder does not revert customer spending for PENDING orders")
    void testCancelOrderDoesNotRevertCustomerSpendingForPendingOrder() {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.PENDING);

        OrderEntity order = OrderEntity.builder()
                .id(444L)
                .orderId("ORD-444")
                .customerId("CUST-1")
                .customerName("Nguyen Van A")
                .phoneNumber("0912345678")
                .grandTotal(75000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(pd)
                .build();
        when(orderEntityRepository.findByOrderId("ORD-444")).thenReturn(Optional.of(order));
        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerEntity customer = CustomerEntity.builder()
                .customerId("CUST-1")
                .name("Nguyen Van A")
                .phoneNumber("0912345678")
                .orderCount(2)
                .totalSpent(200000.0)
                .build();

        orderService.cancelOrder("ORD-444");

        assertEquals(PaymentDetails.PaymentStatus.CANCELLED, order.getPaymentDetails().getStatus());
        // Customer spending must not be reverted
        verify(customerRepository, never()).save(any(CustomerEntity.class));
        assertEquals(2, customer.getOrderCount());
        assertEquals(200000.0, customer.getTotalSpent(), 0.001);
    }

    @Test
    @DisplayName("cancelOrder increments variant cachedStockQuantity directly in O(1) without querying calculateStockByVariantId")
    void testCancelOrderRestoresCachedStockQuantityDirectly() {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.PENDING);

        VariantEntity variant = VariantEntity.builder()
                .variantId("VAR-COMP")
                .cachedStockQuantity(8)
                .build();
        when(variantRepository.findByVariantId("VAR-COMP")).thenReturn(Optional.of(variant));

        OrderItemEntity orderItem = OrderItemEntity.builder()
                .variantId("VAR-COMP")
                .quantity(2)
                .build();

        OrderEntity order = OrderEntity.builder()
                .id(445L)
                .orderId("ORD-445")
                .items(List.of(orderItem))
                .paymentMethod(PaymentMethod.CASH)
                .paymentDetails(pd)
                .build();
        when(orderEntityRepository.findByOrderId("ORD-445")).thenReturn(Optional.of(order));
        when(orderEntityRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.cancelOrder("ORD-445");

        assertEquals(10, variant.getCachedStockQuantity(), "Stock should be restored from 8 to 10 directly");
        verify(inventoryTransactionRepository, never()).calculateStockByVariantId(any());
        verify(variantRepository).save(variant);
    }
}
