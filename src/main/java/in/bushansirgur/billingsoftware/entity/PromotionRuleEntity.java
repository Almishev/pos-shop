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
@Table(name = "tbl_promotion_rules")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromotionRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String ruleId;

    private String name;
    private String description;
    
    // Rule type: PRODUCT, CATEGORY, QUANTITY, AMOUNT, LOYALTY_POINTS
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;
    
    // Discount configuration
    @Enumerated(EnumType.STRING)
    private DiscountType discountType; // PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y
    
    private BigDecimal discountValue; // Percentage (0.15 = 15%) or fixed amount
    private Integer minimumQuantity; // For quantity-based rules
    private BigDecimal minimumAmount; // For amount-based rules
    
    // Target configuration
    private String targetItemId; // For product-specific rules
    private String targetCategoryId; // For category-specific rules
    
    // Buy X Get Y configuration
    private Integer buyQuantity; // Buy X items
    private Integer getQuantity; // Get Y items free/discounted
    private BigDecimal getDiscountPercentage; // Discount on the "get" items
    
    // Rule validity
    private Timestamp validFrom;
    private Timestamp validUntil;
    
    // Usage limits
    private Integer maxUsagePerCustomer;
    private Integer maxTotalUsage;
    
    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;
    
    @UpdateTimestamp
    private Timestamp updatedAt;
    
    // Default values
    @Builder.Default
    private Boolean isActive = true;
    @Builder.Default
    private Integer currentUsage = 0;
    @Builder.Default
    private Integer priority = 0;
    @Builder.Default
    private Boolean requiresLoyaltyCard = false;
    @Builder.Default
    private Integer minimumLoyaltyPoints = 0;
    
    public enum RuleType {
        PRODUCT,        // Specific product discount
        CATEGORY,       // Category-wide discount
        QUANTITY,       // Buy X get Y free/discounted
        AMOUNT,         // Discount when spending above threshold
        LOYALTY_POINTS  // Points-based rewards
    }
    
    public enum DiscountType {
        PERCENTAGE,     // 15% off
        FIXED_AMOUNT,   // 5 BGN off
        BUY_X_GET_Y,    // Buy 3 get 1 free
        POINTS_REDEMPTION // Use loyalty points
    }
}
