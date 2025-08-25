package in.bushansirgur.billingsoftware.io;

import in.bushansirgur.billingsoftware.entity.FiscalReceiptEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FiscalReceiptResponse {
    private Long id;
    private String fiscalNumber;
    private String orderId;
    private String deviceSerialNumber;
    private LocalDateTime fiscalDateTime;
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal grandTotal;
    private String qrCode;
    private String fiscalUrl;
    private FiscalReceiptEntity.FiscalStatus status;
    private String errorMessage;
    
    public static FiscalReceiptResponse fromEntity(FiscalReceiptEntity entity) {
        return FiscalReceiptResponse.builder()
                .id(entity.getId())
                .fiscalNumber(entity.getFiscalNumber())
                .orderId(entity.getOrderId())
                .deviceSerialNumber(entity.getDeviceSerialNumber())
                .fiscalDateTime(entity.getFiscalDateTime())
                .subtotal(entity.getSubtotal())
                .vatAmount(entity.getVatAmount())
                .grandTotal(entity.getGrandTotal())
                .qrCode(entity.getQrCode())
                .fiscalUrl(entity.getFiscalUrl())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}
