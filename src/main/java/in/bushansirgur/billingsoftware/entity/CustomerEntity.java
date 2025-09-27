package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "tbl_customers")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String customerId;

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    
    // Loyalty card information
    @Column(unique = true)
    private String loyaltyCardBarcode;
    
    // Customer status and preferences
    private String notes;
    
    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;
    
    @UpdateTimestamp
    private Timestamp updatedAt;
    
    private Timestamp lastPurchaseDate;
    
    // Default values
    @Builder.Default
    private Boolean isLoyaltyActive = false;
    @Builder.Default
    private Integer loyaltyPoints = 0;
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;
    @Builder.Default
    private Integer totalOrders = 0;
    @Builder.Default
    private String status = "ACTIVE";
}
