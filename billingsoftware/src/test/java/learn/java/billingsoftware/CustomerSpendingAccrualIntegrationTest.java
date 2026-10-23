package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.entity.CustomerEntity;
import learn.java.billingsoftware.entity.InventoryTransactionEntity;
import learn.java.billingsoftware.entity.ItemEntity;
import learn.java.billingsoftware.entity.OrderEntity;
import learn.java.billingsoftware.entity.TransactionType;
import learn.java.billingsoftware.entity.VariantEntity;
import learn.java.billingsoftware.io.OrderRequest;
import learn.java.billingsoftware.io.PaymentDetails;
import learn.java.billingsoftware.io.PaymentMethod;
import learn.java.billingsoftware.repository.ActivityLogRepository;
import learn.java.billingsoftware.repository.CategoryRepository;
import learn.java.billingsoftware.repository.CustomerRepository;
import learn.java.billingsoftware.repository.InventoryTransactionRepository;
import learn.java.billingsoftware.repository.ItemRepository;
import learn.java.billingsoftware.repository.OrderEntityRepository;
import learn.java.billingsoftware.repository.VariantRepository;
import learn.java.billingsoftware.service.OrderService;
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
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CustomerSpendingAccrualIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

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
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private PayOS payOS;

    @MockitoBean
    private PaymentRequestsService paymentRequestsService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private CustomerEntity testCustomer;
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
                    .accountName("TEST STORE")
                    .amount(req.getAmount())
                    .description("TEST PAYOS")
                    .orderCode(req.getOrderCode())
                    .currency("VND")
                    .paymentLinkId("LINK-" + req.getOrderCode())
                    .status(PaymentLinkStatus.PENDING)
                    .checkoutUrl("https://payos.vn/checkout/" + req.getOrderCode())
                    .qrCode("00020101...")
                    .build();
        });

        testCustomer = customerRepository.save(CustomerEntity.builder()
                .customerId("CUST-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Tran Van An")
                .phoneNumber("09" + (System.currentTimeMillis() % 100000000))
                .email("an.tran@example.com")
                .orderCount(0)
                .totalSpent(0.0)
                .build());

        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryId("CAT-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Coffee")
                .bgColor("#555555")
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId("ITEM-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Espresso")
                .price(new BigDecimal("50000.00"))
                .category(category)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-ESPRESSO")
                .basePrice(new BigDecimal("50000.00"))
                .cachedStockQuantity(50)
                .item(testItem)
                .build());

        inventoryTransactionRepository.saveAndFlush(InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(testVariant)
                .transactionType(TransactionType.IN)
                .quantity(50)
                .referenceId("INIT-STOCK")
                .note("Initial stock")
                .build());
    }

    @AfterEach
    void tearDown() {
        activityLogRepository.deleteAll();
        inventoryTransactionRepository.deleteAll();
        orderEntityRepository.deleteAll();
        variantRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();
        customerRepository.deleteAll();
    }

    private OrderRequest buildOrderRequest(String paymentMethod) {
        OrderRequest.OrderItemRequest itemReq = OrderRequest.OrderItemRequest.builder()
                .itemId(testItem.getItemId())
                .variantId(testVariant.getVariantId())
                .name(testItem.getName())
                .price(50000.0)
                .quantity(2)
                .build();

        return OrderRequest.builder()
                .customerId(testCustomer.getCustomerId())
                .customerName(testCustomer.getName())
                .phoneNumber(testCustomer.getPhoneNumber())
                .paymentMethod(paymentMethod)
                .cartItems(List.of(itemReq))
                .build();
    }

    @Test
    @DisplayName("1. PENDING order creation (PAYOS) does NOT accrue customer totalSpent or orderCount")
    void testPendingOrderDoesNotAccrueCustomerSpending() throws Exception {
        OrderRequest request = buildOrderRequest("PAYOS");

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.paymentDetails.status", is("PENDING")))
                .andExpect(jsonPath("$.grandTotal", is(110000.0)));

        CustomerEntity customerAfterOrder = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(0, customerAfterOrder.getOrderCount(), "Order count must NOT increment on PENDING order");
        assertEquals(0.0, customerAfterOrder.getTotalSpent(), 0.001, "Total spent must NOT accrue on PENDING order");
    }

    @Test
    @DisplayName("2. CASH order checkout accrues customer metrics immediately upon creation")
    void testCashOrderAccruesCustomerSpendingImmediately() throws Exception {
        OrderRequest request = buildOrderRequest("CASH");

        mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentDetails.status", is("COMPLETED")))
                .andExpect(jsonPath("$.grandTotal", is(110000.0)));

        CustomerEntity customerAfterOrder = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(1, customerAfterOrder.getOrderCount(), "Order count must increment immediately on CASH order");
        assertEquals(110000.0, customerAfterOrder.getTotalSpent(), 0.001, "Total spent must accrue immediately on CASH order");
    }

    @Test
    @DisplayName("3. PayOS Webhook confirms payment (code 00) and accrues customer metrics idempotently")
    void testPayOSWebhookAccruesCustomerMetricsUponCompletion() throws Exception {
        OrderRequest request = buildOrderRequest("PAYOS");

        MvcResult result = mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText();
        OrderEntity savedOrder = orderEntityRepository.findByOrderId(orderId).orElseThrow();

        // Customer metrics are still 0 before webhook
        CustomerEntity initialCustomer = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(0, initialCustomer.getOrderCount());
        assertEquals(0.0, initialCustomer.getTotalSpent(), 0.001);

        // Webhook arrives confirming payment
        WebhookData webhookData = WebhookData.builder()
                .orderCode(savedOrder.getId())
                .amount(110000L)
                .description("Payment for " + orderId)
                .desc("Payment for " + orderId)
                .accountNumber("123456789")
                .reference("REF123")
                .transactionDateTime("2026-10-23 16:30:00")
                .currency("VND")
                .paymentLinkId("LINK123")
                .code("00")
                .build();

        orderService.handlePaymentWebhook(webhookData);

        // Customer metrics must be accrued
        CustomerEntity customerAfterWebhook = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(1, customerAfterWebhook.getOrderCount());
        assertEquals(110000.0, customerAfterWebhook.getTotalSpent(), 0.001);

        // Second duplicate webhook delivery must NOT accrue duplicate metrics
        orderService.handlePaymentWebhook(webhookData);

        CustomerEntity customerAfterDuplicate = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(1, customerAfterDuplicate.getOrderCount(), "Duplicate webhook must not increment order count again");
        assertEquals(110000.0, customerAfterDuplicate.getTotalSpent(), 0.001, "Duplicate webhook must not accrue total spent again");
    }

    @Test
    @DisplayName("4. switchToCash transitions in-flight PENDING order to COMPLETED and accrues customer metrics")
    void testSwitchToCashAccruesCustomerSpending() throws Exception {
        OrderRequest request = buildOrderRequest("PAYOS");

        MvcResult result = mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText();

        // Customer metrics are still 0 while PENDING
        CustomerEntity customerBeforeSwitch = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(0, customerBeforeSwitch.getOrderCount());
        assertEquals(0.0, customerBeforeSwitch.getTotalSpent(), 0.001);

        // Switch to cash
        mockMvc.perform(post("/orders/" + orderId + "/switch-to-cash")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod", is("CASH")))
                .andExpect(jsonPath("$.paymentDetails.status", is("COMPLETED")));

        CustomerEntity customerAfterSwitch = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(1, customerAfterSwitch.getOrderCount(), "Order count must accrue on switchToCash");
        assertEquals(110000.0, customerAfterSwitch.getTotalSpent(), 0.001, "Total spent must accrue on switchToCash");
    }

    @Test
    @DisplayName("5. Cancelling or deleting a PENDING order leaves customer metrics untouched without redundant deduction")
    void testCancelPendingOrderDoesNotAlterCustomerMetrics() throws Exception {
        // Customer already has 2 previous completed orders and 200,000 spent
        testCustomer.setOrderCount(2);
        testCustomer.setTotalSpent(200000.0);
        customerRepository.save(testCustomer);

        // Create a new PENDING order
        OrderRequest request = buildOrderRequest("PAYOS");

        MvcResult result = mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText();

        // Verify customer metrics remain at baseline (2 orders, 200000 spent)
        CustomerEntity customerWhilePending = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(2, customerWhilePending.getOrderCount());
        assertEquals(200000.0, customerWhilePending.getTotalSpent(), 0.001);

        // Cancel the pending order
        mockMvc.perform(post("/orders/" + orderId + "/cancel")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentDetails.status", is("CANCELLED")));

        // Customer metrics must still remain unchanged (2 orders, 200000 spent) - NOT decremented
        CustomerEntity customerAfterCancel = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(2, customerAfterCancel.getOrderCount(), "Order count must remain unchanged when cancelling PENDING order");
        assertEquals(200000.0, customerAfterCancel.getTotalSpent(), 0.001, "Total spent must remain unchanged when cancelling PENDING order");
    }

    @Test
    @DisplayName("6. Late PayOS Webhook on CANCELLED order is ignored and does NOT accrue customer metrics")
    void testLateWebhookOnCancelledOrderIsIgnored() throws Exception {
        OrderRequest request = buildOrderRequest("PAYOS");

        MvcResult result = mockMvc.perform(post("/orders")
                        .with(user("cashier").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText();
        OrderEntity savedOrder = orderEntityRepository.findByOrderId(orderId).orElseThrow();

        // Cancel the pending order
        mockMvc.perform(post("/orders/" + orderId + "/cancel")
                        .with(user("cashier").roles("USER")))
                .andExpect(status().isOk());

        // Late webhook arrives after cancellation
        WebhookData webhookData = WebhookData.builder()
                .orderCode(savedOrder.getId())
                .amount(110000L)
                .description("Late payment for " + orderId)
                .desc("Late payment for " + orderId)
                .accountNumber("123456789")
                .reference("REF123")
                .transactionDateTime("2026-10-23 16:30:00")
                .currency("VND")
                .paymentLinkId("LINK123")
                .code("00")
                .build();

        orderService.handlePaymentWebhook(webhookData);

        // Order remains CANCELLED and customer metrics remain 0
        OrderEntity orderAfterWebhook = orderEntityRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentDetails.PaymentStatus.CANCELLED, orderAfterWebhook.getPaymentDetails().getStatus());

        CustomerEntity customerAfterWebhook = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        assertEquals(0, customerAfterWebhook.getOrderCount());
        assertEquals(0.0, customerAfterWebhook.getTotalSpent(), 0.001);
    }
}
