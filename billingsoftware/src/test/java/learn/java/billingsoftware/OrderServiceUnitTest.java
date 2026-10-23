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
        orderService = new OrderServiceImpl(
                orderEntityRepository,
                payOS,
                variantRepository,
                inventoryTransactionRepository,
                promotionService,
                promotionRepository,
                customerRepository,
                activityLogService,
                objectMapper
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
}
