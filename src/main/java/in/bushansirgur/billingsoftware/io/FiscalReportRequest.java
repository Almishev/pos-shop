package in.bushansirgur.billingsoftware.io;

import in.bushansirgur.billingsoftware.entity.FiscalReportEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FiscalReportRequest {
    private FiscalReportEntity.ReportType reportType;
    private LocalDate reportDate;
    private String cashierName;
    private String deviceSerialNumber;
    private String notes;
}
