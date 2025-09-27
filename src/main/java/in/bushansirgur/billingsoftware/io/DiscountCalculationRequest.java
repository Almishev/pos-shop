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
public class DiscountCalculationRequest {
    
    private String customerId;
    private String loyaltyCardBarcode;
    private String phoneNumber;
    private List<CartItem> cartItems;
    private BigDecimal subtotal;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CartItem {
        private String itemId;
        private String itemName;
        private String categoryId;
        private String barcode;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal vatRate;
    }
}
