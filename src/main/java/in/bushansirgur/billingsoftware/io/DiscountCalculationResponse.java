package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DiscountCalculationResponse {
    
    private String customerId;
    private String customerName;
    private Integer customerLoyaltyPoints;
    private BigDecimal originalSubtotal;
    private BigDecimal totalDiscount;
    private BigDecimal finalAmount;
    private List<AppliedDiscount> appliedDiscounts;
    private List<String> applicablePromotions;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AppliedDiscount {
        private String ruleId;
        private String ruleName;
        private String ruleType;
        private String itemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal originalPrice;
        private BigDecimal discountAmount;
        private BigDecimal finalPrice;
        private String description;
    }
}
