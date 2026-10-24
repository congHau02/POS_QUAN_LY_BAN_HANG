package learn.java.billingsoftware;

import learn.java.billingsoftware.entity.OrderEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class OrderEntityTest {

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("^ORD\\d+-[0-9A-Z]{4}$");

    @Test
    @DisplayName("onCreate generates collision-proof orderId with timestamp and 4-char uppercase alphanumeric suffix")
    void testOnCreate_generatesOrderIdWithTimestampAndSecure4CharSuffix() {
        OrderEntity order = new OrderEntity();
        order.onCreate();

        assertNotNull(order.getOrderId(), "orderId should not be null after onCreate");
        assertTrue(ORDER_CODE_PATTERN.matcher(order.getOrderId()).matches(),
                "orderId '" + order.getOrderId() + "' should match pattern ORD<timestamp>-<suffix>");
        assertNotNull(order.getCreatedAt(), "createdAt should be populated");
    }

    @Test
    @DisplayName("onCreate preserves pre-existing orderId if already populated")
    void testOnCreate_preservesExistingOrderId() {
        OrderEntity order = OrderEntity.builder()
                .orderId("ORD-PRESET-999")
                .build();

        order.onCreate();

        assertEquals("ORD-PRESET-999", order.getOrderId(), "Pre-set orderId should be preserved");
        assertNotNull(order.getCreatedAt());
    }

    @Test
    @DisplayName("Concurrent order creations never generate colliding order codes")
    void testConcurrentOrderCreation_noCollisionsUnderHeavyLoad() throws InterruptedException, ExecutionException {
        int threadCount = 50;
        int ordersPerThread = 20;
        int totalOrders = threadCount * ordersPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalOrders);
        Set<String> generatedCodes = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < totalOrders; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    OrderEntity entity = new OrderEntity();
                    entity.onCreate();
                    generatedCodes.add(entity.getOrderId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Release all threads at the exact same moment to maximize millisecond collisions
        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(totalOrders, generatedCodes.size(),
                "Expected " + totalOrders + " unique order codes, but got " + generatedCodes.size() + " (collisions occurred!)");

        for (String code : generatedCodes) {
            assertTrue(ORDER_CODE_PATTERN.matcher(code).matches(),
                    "Generated code '" + code + "' should match format");
        }
    }
}
