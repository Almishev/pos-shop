package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderRefundRequest {
    private String orderId; // original order
    private String reason;
    private String refundMethod; // CASH or CARD
    private Double refundAmount; // optional if itemized
    private List<RefundItem> items; // optional; partial refunds

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class RefundItem {
        private String itemId;
        private Integer quantity;
    }
}


