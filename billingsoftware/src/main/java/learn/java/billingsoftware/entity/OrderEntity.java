package learn.java.billingsoftware.entity;

import jakarta.persistence.*;
import learn.java.billingsoftware.io.PaymentDetails;
import learn.java.billingsoftware.io.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="tbl_orders")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderEntity {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_code", unique = true, nullable = false)
    private String orderId;
    private String customerId;
    private String customerName;
    private String phoneNumber;
    private Double subtotal;
    @Builder.Default
    private Double discountAmount = 0.0;
    private Double tax;
    private Double grandTotal;
    private String promotionId;
    private String promotionName;
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    // THỰC HÀNH TỐT (Best practice): Viết thêm một hàm tiện ích để thêm Item
    public void addOrderItem(OrderItemEntity item) {
        items.add(item);
        item.setOrder(this); // RẤT QUAN TRỌNG: Gán order cha cho item con
    }
    @Embedded
    private PaymentDetails paymentDetails;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private static String generateRandomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(ALPHANUMERIC_CHARS.length());
            sb.append(ALPHANUMERIC_CHARS.charAt(index));
        }
        return sb.toString();
    }

    public static String generateOrderCode() {
        return "ORD" + System.currentTimeMillis() + "-" + generateRandomSuffix(4);
    }

    @PrePersist
    public void onCreate() {
        if (this.orderId == null || this.orderId.trim().isEmpty()) {
            this.orderId = generateOrderCode();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
