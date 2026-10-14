package learn.java.billingsoftware;

import learn.java.billingsoftware.entity.ActivityLogEntity;
import learn.java.billingsoftware.entity.OrderEntity;
import learn.java.billingsoftware.io.PaymentDetails;
import learn.java.billingsoftware.io.PaymentMethod;
import learn.java.billingsoftware.repository.ActivityLogRepository;
import learn.java.billingsoftware.repository.OrderEntityRepository;
import learn.java.billingsoftware.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.webhooks.WebhookData;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class PaymentWebhookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderEntityRepository orderEntityRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    private OrderEntity testOrder;

    @BeforeEach
    void setUp() {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.PENDING);

        testOrder = OrderEntity.builder()
                .orderId(UUID.randomUUID().toString())
                .customerName("Webhook Test Customer")
                .phoneNumber("0987654321")
                .subtotal(100000.0)
                .discountAmount(0.0)
                .tax(8000.0)
                .grandTotal(108000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(pd)
                .build();
        testOrder = orderEntityRepository.save(testOrder);
    }

    private WebhookData createWebhookData(Long orderCode, Long amount, String code, String description) {
        return WebhookData.builder()
                .orderCode(orderCode)
                .amount(amount != null ? amount : 0L)
                .description(description != null ? description : "Thanh toan")
                .desc(description != null ? description : "Thanh toan")
                .accountNumber("123456789")
                .reference("REF123")
                .transactionDateTime("2026-10-14 00:00:00")
                .currency("VND")
                .paymentLinkId("LINK123")
                .code(code != null ? code : "00")
                .build();
    }

    @Test
    void testHandlePaymentWebhookUpdatesOrderToCompletedAndLogsActivity() {
        WebhookData data = createWebhookData(testOrder.getId(), 108000L, "00", "Thanh toan don " + testOrder.getId());

        orderService.handlePaymentWebhook(data);

        OrderEntity updated = orderEntityRepository.findById(testOrder.getId()).orElseThrow();
        assertNotNull(updated.getPaymentDetails());
        assertEquals(PaymentDetails.PaymentStatus.COMPLETED, updated.getPaymentDetails().getStatus());

        List<ActivityLogEntity> logs = activityLogRepository.findAll().stream()
                .filter(l -> "ORDER".equals(l.getEntityType()) && testOrder.getOrderId().equals(l.getEntityId()))
                .filter(l -> "PayOS-Webhook".equals(l.getUserEmail()))
                .toList();

        assertEquals(1, logs.size());
        assertEquals("UPDATE", logs.get(0).getAction());
    }

    @Test
    void testHandlePaymentWebhookIsIdempotent() {
        WebhookData data = createWebhookData(testOrder.getId(), 108000L, "00", "Thanh toan don " + testOrder.getId());

        // Call once
        orderService.handlePaymentWebhook(data);

        // Call again with same webhook payload
        orderService.handlePaymentWebhook(data);

        OrderEntity updated = orderEntityRepository.findById(testOrder.getId()).orElseThrow();
        assertEquals(PaymentDetails.PaymentStatus.COMPLETED, updated.getPaymentDetails().getStatus());

        // Should only have 1 log from the initial completion
        List<ActivityLogEntity> logs = activityLogRepository.findAll().stream()
                .filter(l -> "ORDER".equals(l.getEntityType()) && testOrder.getOrderId().equals(l.getEntityId()))
                .filter(l -> "PayOS-Webhook".equals(l.getUserEmail()))
                .toList();

        assertEquals(1, logs.size());
    }

    @Test
    void testHandlePaymentWebhookTestPingDoesNotFail() {
        WebhookData testPing = createWebhookData(123L, 0L, "00", "Test ping");

        orderService.handlePaymentWebhook(testPing);
    }

    @Test
    void testPostWebhookWithoutValidSignatureReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"00\",\"desc\":\"test\",\"data\":{\"orderCode\":123}}"))
                .andExpect(status().isBadRequest());
    }
}
