package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "tbl_loyalty_transactions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoyaltyTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String transactionId;

    private String customerId;
    private String orderId;
    
    // Transaction type: POINTS_EARNED, POINTS_REDEEMED, DISCOUNT_APPLIED
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    
    private Integer pointsEarned;
    private Integer pointsRedeemed;
    private BigDecimal discountAmount;
    private String promotionRuleId;
    
    private String description;
    private String notes;
    
    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;
    
    public enum TransactionType {
        POINTS_EARNED,      // Customer earned points from purchase
        POINTS_REDEEMED,    // Customer used points for discount
        DISCOUNT_APPLIED,   // Promotion rule was applied
        CARD_ACTIVATED,     // Loyalty card was activated
        CARD_DEACTIVATED    // Loyalty card was deactivated
    }
}
