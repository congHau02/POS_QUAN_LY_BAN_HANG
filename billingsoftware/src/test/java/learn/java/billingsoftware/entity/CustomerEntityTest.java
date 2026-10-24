package learn.java.billingsoftware.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerEntityTest {

    @Test
    @DisplayName("accrueOrderSpending increments order count and adds order total to total spent")
    void testAccrueOrderSpending() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId("CUST-1")
                .name("Test Customer")
                .phoneNumber("0912345678")
                .orderCount(0)
                .totalSpent(0.0)
                .build();

        customer.accrueOrderSpending(50000.0);
        assertEquals(1, customer.getOrderCount());
        assertEquals(50000.0, customer.getTotalSpent(), 0.001);

        customer.accrueOrderSpending(30000.0);
        assertEquals(2, customer.getOrderCount());
        assertEquals(80000.0, customer.getTotalSpent(), 0.001);
    }

    @Test
    @DisplayName("accrueOrderSpending handles null orderTotal gracefully")
    void testAccrueOrderSpendingNullTotal() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId("CUST-1")
                .name("Test Customer")
                .phoneNumber("0912345678")
                .orderCount(null)
                .totalSpent(null)
                .build();

        customer.accrueOrderSpending(null);
        assertEquals(1, customer.getOrderCount());
        assertEquals(0.0, customer.getTotalSpent(), 0.001);
    }

    @Test
    @DisplayName("revertOrderSpending decrements order count and deducts total spent without going below zero")
    void testRevertOrderSpending() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId("CUST-1")
                .name("Test Customer")
                .phoneNumber("0912345678")
                .orderCount(1)
                .totalSpent(50000.0)
                .build();

        customer.revertOrderSpending(30000.0);
        assertEquals(0, customer.getOrderCount());
        assertEquals(20000.0, customer.getTotalSpent(), 0.001);

        customer.revertOrderSpending(50000.0);
        assertEquals(0, customer.getOrderCount());
        assertEquals(0.0, customer.getTotalSpent(), 0.001);
    }
}
