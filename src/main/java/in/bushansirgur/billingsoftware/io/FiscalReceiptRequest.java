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
public class FiscalReceiptRequest {
    private String orderId;
    private String deviceSerialNumber;
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal grandTotal;
    private String cashierName;
    private List<FiscalItemRequest> items;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FiscalItemRequest {
        private String itemName;
        private String barcode;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal totalPrice;
        private BigDecimal vatRate; // 20% за България
    }
}
