package learn.java.billingsoftware.repository;

import learn.java.billingsoftware.entity.OrderEntity;
import learn.java.billingsoftware.io.PaymentDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderEntityRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity>findByOrderId(String orderId);

    List<OrderEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT SUM(o.grandTotal) FROM OrderEntity o WHERE o.createdAt >= :startDate AND o.createdAt < :endDate AND o.paymentDetails.status = learn.java.billingsoftware.io.PaymentDetails.PaymentStatus.COMPLETED")
    Double sumSalesBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.createdAt >= :startDate AND o.createdAt < :endDate AND o.paymentDetails.status = learn.java.billingsoftware.io.PaymentDetails.PaymentStatus.COMPLETED")
    Long countOrdersBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    default Double sumSalesByDate(LocalDate date) {
        return sumSalesBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    default Long countByOrderDate(LocalDate date) {
        return countOrdersBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    @Query("SELECT o FROM OrderEntity o order by o.createdAt DESC")
    List<OrderEntity> findRecentOrders(Pageable pageable);

    @Query(value = "SELECT o FROM OrderEntity o WHERE " +
            "(:status IS NULL OR o.paymentDetails.status = :status) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(o.orderId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(o.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "o.phoneNumber LIKE CONCAT('%', :search, '%'))",
            countQuery = "SELECT COUNT(o) FROM OrderEntity o WHERE " +
            "(:status IS NULL OR o.paymentDetails.status = :status) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(o.orderId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(o.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "o.phoneNumber LIKE CONCAT('%', :search, '%'))")
    Page<OrderEntity> findOrdersWithFilter(@Param("search") String search,
                                          @Param("status") PaymentDetails.PaymentStatus status,
                                          Pageable pageable);
}
