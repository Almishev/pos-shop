package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryRequest {
    private String referenceNumber;
    private String supplierName;
    private LocalDate deliveryDate;
    private String notes;
    private String createdBy;
    private List<DeliveryLineRequest> lines;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DeliveryLineRequest {
        private String itemId;
        private Integer quantity;
        private BigDecimal unitCost;
    }
}
