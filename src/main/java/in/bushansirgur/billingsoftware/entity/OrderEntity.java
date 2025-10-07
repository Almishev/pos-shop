package in.bushansirgur.billingsoftware.entity;

import in.bushansirgur.billingsoftware.io.PaymentDetails;
import in.bushansirgur.billingsoftware.io.PaymentMethod;
import in.bushansirgur.billingsoftware.io.OrderStatus;
import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_orders", indexes = {
        @Index(name = "idx_orders_created_at", columnList = "createdAt"),
        @Index(name = "idx_orders_order_id", columnList = "orderId", unique = true),
        @Index(name = "idx_orders_payment_method", columnList = "paymentMethod"),
        @Index(name = "idx_orders_cashier_username", columnList = "cashierUsername")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderId;
    private String customerName;
    private String phoneNumber;
    private Double subtotal;
    private Double tax;
    private Double grandTotal;
    private LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    @Builder.Default
    private List<OrderItemEntity> items = new ArrayList<>();

    @Embedded
    private PaymentDetails paymentDetails;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus status;

    // Reference to original order if this record represents a refund/void
    private String originalOrderId;

    // Username of the cashier who created the order
    private String cashierUsername;

    @PrePersist
    protected void onCreate() {
        this.orderId = "ORD"+System.currentTimeMillis();
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = OrderStatus.COMPLETED;
    }

}
