package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromotionRuleRequest {
    
    private String name;
    private String description;
    private String ruleType; // PRODUCT, CATEGORY, QUANTITY, AMOUNT, LOYALTY_POINTS
    private String discountType; // PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y
    private BigDecimal discountValue;
    private Integer minimumQuantity;
    private BigDecimal minimumAmount;
    private String targetItemId;
    private String targetCategoryId;
    private Integer buyQuantity;
    private Integer getQuantity;
    private BigDecimal getDiscountPercentage;
    private Timestamp validFrom;
    private Timestamp validUntil;
    private Boolean isActive;
    private Integer maxUsagePerCustomer;
    private Integer maxTotalUsage;
    private Integer priority;
    private Boolean requiresLoyaltyCard;
    private Integer minimumLoyaltyPoints;
}
