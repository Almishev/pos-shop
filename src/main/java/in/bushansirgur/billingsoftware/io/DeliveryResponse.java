package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryResponse {
    private String deliveryId;
    private String referenceNumber;
    private String supplierName;
    private LocalDate deliveryDate;
    private String notes;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime postedAt;
    private BigDecimal totalCost;
    private List<DeliveryLineResponse> lines;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DeliveryLineResponse {
        private Long id;
        private String itemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal unitCost;
        private BigDecimal lineTotal;
    }
}
