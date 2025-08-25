package in.bushansirgur.billingsoftware.io;

import in.bushansirgur.billingsoftware.entity.FiscalReportEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FiscalReportResponse {
    private Long id;
    private String reportNumber;
    private FiscalReportEntity.ReportType reportType;
    private LocalDate reportDate;
    private LocalDateTime generatedAt;
    private Integer totalReceipts;
    private BigDecimal totalSales;
    private BigDecimal totalVAT;
    private BigDecimal totalNetSales;
    private String cashierName;
    private String deviceSerialNumber;
    private FiscalReportEntity.ReportStatus status;
    private String notes;
    
    public static FiscalReportResponse fromEntity(FiscalReportEntity entity) {
        return FiscalReportResponse.builder()
                .id(entity.getId())
                .reportNumber(entity.getReportNumber())
                .reportType(entity.getReportType())
                .reportDate(entity.getReportDate())
                .generatedAt(entity.getGeneratedAt())
                .totalReceipts(entity.getTotalReceipts())
                .totalSales(entity.getTotalSales())
                .totalVAT(entity.getTotalVAT())
                .totalNetSales(entity.getTotalNetSales())
                .cashierName(entity.getCashierName())
                .deviceSerialNumber(entity.getDeviceSerialNumber())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .build();
    }
}
