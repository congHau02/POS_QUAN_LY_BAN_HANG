package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.OrderEntity;
import learn.java.billingsoftware.io.DashboarResponse;
import learn.java.billingsoftware.io.PaymentDetails;
import learn.java.billingsoftware.io.PaymentMethod;
import learn.java.billingsoftware.repository.OrderEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Requirement R4: Dashboard Order Metric Synchronization.
 * Verifies that GET /dashboard provides accurate todayOrderCount and todaySales,
 * enforces role-based security, and handles empty/boundary states gracefully.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DashboardMetricIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderEntityRepository orderEntityRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Unauthenticated request to /dashboard should be rejected with 401 or 403")
    void testUnauthenticatedAccessToDashboardIsRejected() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Staff user with ROLE_USER can access /dashboard and receive valid metric structure")
    void testStaffUserCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard")
                        .with(user("cashier@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayOrderCount", notNullValue()))
                .andExpect(jsonPath("$.todaySales", notNullValue()))
                .andExpect(jsonPath("$.recentOrders", notNullValue()))
                .andExpect(jsonPath("$.recentOrders").isArray());
    }

    @Test
    @DisplayName("Admin user with ROLE_ADMIN can access /dashboard successfully")
    void testAdminUserCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayOrderCount", notNullValue()))
                .andExpect(jsonPath("$.todaySales", notNullValue()));
    }

    private DashboarResponse fetchDashboardResponse(String username, String role) throws Exception {
        String content = mockMvc.perform(get("/dashboard")
                        .with(user(username).roles(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(content, DashboarResponse.class);
    }

    @Test
    @DisplayName("todayOrderCount and todaySales dynamically update when new orders are placed today")
    void testTodayOrderCountAccuracyWithNewOrders() throws Exception {
        // 1. Fetch initial baseline dashboard metrics
        DashboarResponse initialResponse = fetchDashboardResponse("staff@example.com", "USER");
        long initialCount = initialResponse.getTodayOrderCount() != null ? initialResponse.getTodayOrderCount() : 0L;
        double initialSales = initialResponse.getTodaySales() != null ? initialResponse.getTodaySales() : 0.0;

        // 2. Persist a new order placed today
        String uniqueOrderId = "ORD-DASH-" + UUID.randomUUID().toString().substring(0, 8);
        Double orderAmount = 150000.0;
        OrderEntity order = OrderEntity.builder()
                .orderId(uniqueOrderId)
                .customerName("Nguyen Van Dashboard")
                .phoneNumber("0911223344")
                .subtotal(136363.64)
                .tax(13636.36)
                .grandTotal(orderAmount)
                .paymentMethod(PaymentMethod.CASH)
                .paymentDetails(PaymentDetails.builder()
                        .status(PaymentDetails.PaymentStatus.COMPLETED)
                        .build())
                .createdAt(LocalDateTime.now())
                .build();
        orderEntityRepository.saveAndFlush(order);

        // 3. Re-query dashboard and assert that todayOrderCount incremented by 1 and todaySales increased
        DashboarResponse updatedResponse = fetchDashboardResponse("staff@example.com", "USER");
        assertNotNull(updatedResponse);
        assertEquals(initialCount + 1, updatedResponse.getTodayOrderCount());
        assertEquals(initialSales + orderAmount, updatedResponse.getTodaySales(), 0.01);
    }

    @Test
    @DisplayName("Recent orders list does not exceed maximum limit of 5")
    void testRecentOrdersLimit() throws Exception {
        // Create 7 orders
        for (int i = 0; i < 7; i++) {
            OrderEntity order = OrderEntity.builder()
                    .orderId("ORD-BATCH-" + i + "-" + UUID.randomUUID().toString().substring(0, 8))
                    .customerName("Customer Batch " + i)
                    .phoneNumber("090000000" + i)
                    .subtotal(50000.0)
                    .tax(5000.0)
                    .grandTotal(55000.0)
                    .paymentMethod(PaymentMethod.CASH)
                    .createdAt(LocalDateTime.now().minusMinutes(i))
                    .build();
            orderEntityRepository.save(order);
        }

        mockMvc.perform(get("/dashboard")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentOrders", hasSize(lessThanOrEqualTo(5))));
    }

    @Test
    @DisplayName("Dashboard response metrics should never be null even when zero orders exist")
    void testDashboardDefaultMetricsNotNull() throws Exception {
        mockMvc.perform(get("/dashboard")
                        .with(user("staff@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayOrderCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.todaySales", greaterThanOrEqualTo(0.0)));
    }

    @Test
    @DisplayName("Dashboard todaySales and todayOrderCount strictly include COMPLETED orders and exclude PENDING, CANCELLED, and FAILED")
    void testDashboardMetricsExcludeNonCompletedOrders() throws Exception {
        // 1. Fetch baseline
        DashboarResponse initialResponse = fetchDashboardResponse("staff@example.com", "USER");
        long initialCount = initialResponse.getTodayOrderCount() != null ? initialResponse.getTodayOrderCount() : 0L;
        double initialSales = initialResponse.getTodaySales() != null ? initialResponse.getTodaySales() : 0.0;

        // 2. Persist non-COMPLETED orders today
        OrderEntity pendingOrder = OrderEntity.builder()
                .orderId("ORD-PENDING-" + UUID.randomUUID().toString().substring(0, 8))
                .customerName("Pending Customer")
                .grandTotal(50000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(PaymentDetails.builder().status(PaymentDetails.PaymentStatus.PENDING).build())
                .createdAt(LocalDateTime.now())
                .build();

        OrderEntity cancelledOrder = OrderEntity.builder()
                .orderId("ORD-CANCELLED-" + UUID.randomUUID().toString().substring(0, 8))
                .customerName("Cancelled Customer")
                .grandTotal(75000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(PaymentDetails.builder().status(PaymentDetails.PaymentStatus.CANCELLED).build())
                .createdAt(LocalDateTime.now())
                .build();

        OrderEntity failedOrder = OrderEntity.builder()
                .orderId("ORD-FAILED-" + UUID.randomUUID().toString().substring(0, 8))
                .customerName("Failed Customer")
                .grandTotal(60000.0)
                .paymentMethod(PaymentMethod.PAYOS)
                .paymentDetails(PaymentDetails.builder().status(PaymentDetails.PaymentStatus.FAILED).build())
                .createdAt(LocalDateTime.now())
                .build();

        // Out-of-bounds orders (yesterday and tomorrow) with COMPLETED status
        OrderEntity yesterdayCompleted = OrderEntity.builder()
                .orderId("ORD-YESTERDAY-" + UUID.randomUUID().toString().substring(0, 8))
                .customerName("Yesterday Customer")
                .grandTotal(200000.0)
                .paymentMethod(PaymentMethod.CASH)
                .paymentDetails(PaymentDetails.builder().status(PaymentDetails.PaymentStatus.COMPLETED).build())
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        OrderEntity tomorrowCompleted = OrderEntity.builder()
                .orderId("ORD-TOMORROW-" + UUID.randomUUID().toString().substring(0, 8))
                .customerName("Tomorrow Customer")
                .grandTotal(300000.0)
                .paymentMethod(PaymentMethod.CASH)
                .paymentDetails(PaymentDetails.builder().status(PaymentDetails.PaymentStatus.COMPLETED).build())
                .createdAt(LocalDateTime.now().plusDays(1))
                .build();

        // 1 COMPLETED order today
        Double completedAmount = 120000.0;
        OrderEntity todayCompleted = OrderEntity.builder()
                .orderId("ORD-COMPLETED-" + UUID.randomUUID().toString().substring(0, 8))
                .customerName("Completed Customer")
                .grandTotal(completedAmount)
                .paymentMethod(PaymentMethod.CASH)
                .paymentDetails(PaymentDetails.builder().status(PaymentDetails.PaymentStatus.COMPLETED).build())
                .createdAt(LocalDateTime.now())
                .build();

        orderEntityRepository.save(pendingOrder);
        orderEntityRepository.save(cancelledOrder);
        orderEntityRepository.save(failedOrder);
        orderEntityRepository.save(yesterdayCompleted);
        orderEntityRepository.save(tomorrowCompleted);
        orderEntityRepository.save(todayCompleted);
        orderEntityRepository.flush();

        // 3. Verify metrics strictly reflect only todayCompleted
        DashboarResponse updatedResponse = fetchDashboardResponse("staff@example.com", "USER");
        assertNotNull(updatedResponse);
        assertEquals(initialCount + 1, updatedResponse.getTodayOrderCount());
        assertEquals(initialSales + completedAmount, updatedResponse.getTodaySales(), 0.01);
    }
}
