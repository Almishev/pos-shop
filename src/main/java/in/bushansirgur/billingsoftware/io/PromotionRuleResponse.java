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
public class PromotionRuleResponse {
    
    private String ruleId;
    private String name;
    private String description;
    private String ruleType;
    private String discountType;
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
    private Integer currentUsage;
    private Integer priority;
    private Boolean requiresLoyaltyCard;
    private Integer minimumLoyaltyPoints;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
