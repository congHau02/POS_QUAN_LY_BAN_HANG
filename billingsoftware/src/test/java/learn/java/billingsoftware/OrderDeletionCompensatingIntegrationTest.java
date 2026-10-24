package learn.java.billingsoftware;

import learn.java.billingsoftware.entity.*;
import learn.java.billingsoftware.io.InventoryTransactionRequest;
import learn.java.billingsoftware.io.PaymentDetails;
import learn.java.billingsoftware.io.PaymentMethod;
import learn.java.billingsoftware.repository.*;
import learn.java.billingsoftware.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class OrderDeletionCompensatingIntegrationTest {

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

    private ItemEntity testItem;
    private VariantEntity testVariant;
    private CustomerEntity testCustomer;
    private PromotionEntity testCoupon;

    @BeforeEach
    void setUp() {
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryId(UUID.randomUUID().toString())
                .name("Drink")
                .bgColor("#FFF")
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name("Tra Dao")
                .price(new BigDecimal("50000"))
                .category(category)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId(UUID.randomUUID().toString())
                .sku("SKU-TD-DEL")
                .basePrice(new BigDecimal("50000"))
                .cachedStockQuantity(0)
                .item(testItem)
                .build());

        inventoryService.recordTransaction(InventoryTransactionRequest.builder()
                .variantId(testVariant.getVariantId())
                .transactionType(TransactionType.IN)
                .quantity(10)
                .note("Initial Stock")
                .build());

        testCustomer = customerRepository.save(CustomerEntity.builder()
                .customerId(UUID.randomUUID().toString())
                .name("Nguyen Van Test")
                .phoneNumber("09" + (System.currentTimeMillis() % 100000000))
                .email("test" + System.currentTimeMillis() + "@test.com")
                .totalSpent(0.0)
                .orderCount(0)
                .build());

        testCoupon = promotionRepository.save(PromotionEntity.builder()
                .promotionId(UUID.randomUUID().toString())
                .name("Giam 20k")
                .code("COUPON20K_" + System.currentTimeMillis())
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("20000.0"))
                .type(PromotionType.COUPON)
                .timesUsed(0)
                .usageLimit(100)
                .isActive(true)
                .build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteCompletedOrder_returnsBadRequest400() throws Exception {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.COMPLETED);

        OrderEntity completedOrder = orderEntityRepository.save(OrderEntity.builder()
                .orderId("ORD-COMPLETED-" + UUID.randomUUID().toString().substring(0, 8))
                .grandTotal(100000.0)
                .paymentDetails(pd)
                .build());

        mockMvc.perform(delete("/orders/" + completedOrder.getOrderId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeletePendingOrder_compensatesInventoryAndDeletesOrder() throws Exception {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.PENDING);

        OrderEntity pendingOrder = OrderEntity.builder()
                .orderId("ORD-PENDING-" + UUID.randomUUID().toString().substring(0, 8))
                .customerId(testCustomer.getCustomerId())
                .customerName(testCustomer.getName())
                .phoneNumber(testCustomer.getPhoneNumber())
                .subtotal(100000.0)
                .discountAmount(20000.0)
                .tax(8000.0)
                .grandTotal(88000.0)
                .promotionId(testCoupon.getPromotionId())
                .paymentMethod(PaymentMethod.CASH)
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

        inventoryService.recordTransaction(InventoryTransactionRequest.builder()
                .variantId(testVariant.getVariantId())
                .transactionType(TransactionType.OUT)
                .quantity(2)
                .referenceId(pendingOrder.getOrderId())
                .build());

        testCustomer.setOrderCount(1);
        testCustomer.setTotalSpent(88000.0);
        customerRepository.save(testCustomer);

        testCoupon.setTimesUsed(1);
        promotionRepository.save(testCoupon);

        assertEquals(8, variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow().getCachedStockQuantity());

        mockMvc.perform(delete("/orders/" + pendingOrder.getOrderId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertTrue(orderEntityRepository.findByOrderId(pendingOrder.getOrderId()).isEmpty());
        assertEquals(10, variantRepository.findByVariantId(testVariant.getVariantId()).orElseThrow().getCachedStockQuantity());
        assertEquals(0, promotionRepository.findByPromotionId(testCoupon.getPromotionId()).orElseThrow().getTimesUsed());

        CustomerEntity customerAfterDelete = customerRepository.findByCustomerId(testCustomer.getCustomerId()).orElseThrow();
        // Customer metrics are not reversed on PENDING order deletion because PENDING orders do not accrue spending (Ticket 04)
        assertEquals(1, customerAfterDelete.getOrderCount());
        assertEquals(88000.0, customerAfterDelete.getTotalSpent(), 0.01);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteCancelledOrder_deletesWithoutDuplicateCompensation() throws Exception {
        PaymentDetails pd = new PaymentDetails();
        pd.setStatus(PaymentDetails.PaymentStatus.CANCELLED);

        OrderEntity cancelledOrder = orderEntityRepository.save(OrderEntity.builder()
                .orderId("ORD-CANCELLED-" + UUID.randomUUID().toString().substring(0, 8))
                .grandTotal(50000.0)
                .paymentDetails(pd)
                .build());

        int initialTxCount = inventoryTransactionRepository.findAll().size();

        mockMvc.perform(delete("/orders/" + cancelledOrder.getOrderId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertTrue(orderEntityRepository.findByOrderId(cancelledOrder.getOrderId()).isEmpty());
        assertEquals(initialTxCount, inventoryTransactionRepository.findAll().size());
    }
}
