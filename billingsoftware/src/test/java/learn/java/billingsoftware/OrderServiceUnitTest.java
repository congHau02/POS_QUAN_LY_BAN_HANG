package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.InventoryTransactionEntity;
import learn.java.billingsoftware.entity.OrderEntity;
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
        when(inventoryTransactionRepository.calculateStockByVariantId("VAR-123")).thenReturn(18);

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
        when(inventoryTransactionRepository.calculateStockByVariantId("VAR-999")).thenReturn(10);

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
    }
}
