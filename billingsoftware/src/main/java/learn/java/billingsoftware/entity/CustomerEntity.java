package learn.java.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Entity
@Table(name = "tbl_customers")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    private String email;

    @Builder.Default
    @Column(nullable = false)
    private Double totalSpent = 0.0;

    @Builder.Default
    @Column(nullable = false)
    private Integer orderCount = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    public void accrueOrderSpending(Double orderTotal) {
        this.orderCount = (this.orderCount != null ? this.orderCount : 0) + 1;
        double currentSpent = this.totalSpent != null ? this.totalSpent : 0.0;
        double amountToAdd = orderTotal != null ? orderTotal : 0.0;
        this.totalSpent = currentSpent + amountToAdd;
    }

    public void revertOrderSpending(Double orderTotal) {
        if (this.orderCount != null && this.orderCount > 0) {
            this.orderCount--;
        }
        double currentSpent = this.totalSpent != null ? this.totalSpent : 0.0;
        double amountToDeduct = orderTotal != null ? orderTotal : 0.0;
        this.totalSpent = Math.max(0.0, currentSpent - amountToDeduct);
    }
}
