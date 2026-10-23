package learn.java.billingsoftware;

import learn.java.billingsoftware.entity.*;
import learn.java.billingsoftware.io.OrderRequest;
import learn.java.billingsoftware.repository.*;
import learn.java.billingsoftware.service.InventoryService;
import learn.java.billingsoftware.service.OrderService;
import learn.java.billingsoftware.io.InventoryTransactionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class PromotionConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PromotionRepository promotionRepository;

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
    private OrderItemEntityRepository orderItemEntityRepository;

    private CategoryEntity testCategory;
    private ItemEntity testItem;
    private VariantEntity testVariant;
    private PromotionEntity testCoupon;
    private final List<String> createdOrderIds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        testCategory = categoryRepository.save(CategoryEntity.builder()
                .categoryId("CAT-" + randomSuffix)
                .name("Cat-" + randomSuffix)
                .bgColor("#112233")
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId("ITEM-" + randomSuffix)
                .name("Item-" + randomSuffix)
                .price(new BigDecimal("100000.00"))
                .category(testCategory)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId("VAR-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-CONCUR-" + UUID.randomUUID().toString().substring(0, 4))
                .basePrice(new BigDecimal("100000.00"))
                .cachedStockQuantity(0)
                .item(testItem)
                .build());

        inventoryService.recordTransaction(InventoryTransactionRequest.builder()
                .variantId(testVariant.getVariantId())
                .transactionType(TransactionType.IN)
                .quantity(100)
                .referenceId("INIT-CONCUR-STOCK")
                .build());

        testCoupon = promotionRepository.save(PromotionEntity.builder()
                .promotionId(UUID.randomUUID().toString())
                .name("Limited 2 Usage Voucher")
                .type(PromotionType.COUPON)
                .code("LIMIT2-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase())
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(10000.0))
                .minOrderAmount(BigDecimal.valueOf(50000.0))
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .usageLimit(2)
                .timesUsed(0)
                .isActive(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        for (String orderId : createdOrderIds) {
            orderEntityRepository.findByOrderId(orderId).ifPresent(order -> {
                orderEntityRepository.delete(order);
            });
        }
        if (testCoupon != null) {
            promotionRepository.delete(testCoupon);
        }
        if (testVariant != null) {
            inventoryTransactionRepository.deleteAll(inventoryTransactionRepository.findByVariant_VariantIdOrderByCreatedAtDesc(testVariant.getVariantId()));
            variantRepository.delete(testVariant);
        }
        if (testItem != null) {
            itemRepository.delete(testItem);
        }
        if (testCategory != null) {
            categoryRepository.delete(testCategory);
        }
    }

    @Test
    @DisplayName("Concurrent checkout requests cannot exceed configured promotion usageLimit")
    void testConcurrentCheckouts_enforcePromotionUsageLimitAtomically() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger quotaExceededCount = new AtomicInteger(0);
        AtomicInteger otherFailureCount = new AtomicInteger(0);

        List<Throwable> unexpectedExceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    OrderRequest request = OrderRequest.builder()
                            .couponCode(testCoupon.getCode())
                            .cartItems(List.of(
                                    OrderRequest.OrderItemRequest.builder()
                                            .itemId(testItem.getItemId())
                                            .variantId(testVariant.getVariantId())
                                            .name(testItem.getName())
                                            .price(100000.00)
                                            .quantity(1)
                                            .build()
                            ))
                            .paymentMethod("CASH")
                            .build();

                    var response = orderService.createOrder(request);
                    if (response != null && response.getOrderId() != null) {
                        createdOrderIds.add(response.getOrderId());
                        successCount.incrementAndGet();
                    }
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() == HttpStatus.BAD_REQUEST &&
                            e.getReason() != null &&
                            (e.getReason().contains("giới hạn lượt sử dụng") || e.getReason().contains("hết lượt sử dụng"))) {
                        quotaExceededCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(e);
                        otherFailureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpectedExceptions.add(e);
                    otherFailureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "All threads should be ready");
        startLatch.countDown(); // Fire all concurrent checkouts
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "All threads should finish within timeout");
        executor.shutdown();

        if (!unexpectedExceptions.isEmpty()) {
            unexpectedExceptions.forEach(Throwable::printStackTrace);
        }

        PromotionEntity updatedCoupon = promotionRepository.findByPromotionId(testCoupon.getPromotionId()).orElseThrow();

        assertEquals(0, otherFailureCount.get(), "No unexpected exceptions should occur");
        assertEquals(2, successCount.get(), "Exactly 2 concurrent orders should succeed for usageLimit=2");
        assertEquals(8, quotaExceededCount.get(), "Remaining 8 orders should fail with quota exceeded error");
        assertEquals(2, updatedCoupon.getTimesUsed(), "Database timesUsed should never exceed usageLimit of 2");
    }
}
