package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_fiscal_reports")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FiscalReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String reportNumber;
    
    @Enumerated(EnumType.STRING)
    private ReportType reportType;


    private LocalDate reportDate;
    
    private LocalDateTime generatedAt;
    
    private Integer totalReceipts; 
    
    private BigDecimal totalSales;
    
    private BigDecimal totalVAT;
    
    private BigDecimal totalNetSales;
    
    private String cashierName;
    
    private String deviceSerialNumber; 
    
    @Enumerated(EnumType.STRING)
    private ReportStatus status; 
    
    private String notes; 
    
    @PrePersist
    protected void onCreate() {
        this.generatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ReportStatus.GENERATED;
        }
    }
    
    public enum ReportType {
        DAILY,    
        SHIFT,     
        MONTHLY,   
        YEARLY,    
        Z_REPORT,  
        X_REPORT   
    }
    
    public enum ReportStatus {
        GENERATED,  
        SENT_TO_NAF, 
        CONFIRMED,  
        ERROR     
    }
}
