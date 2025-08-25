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
    private String reportNumber; // Номер на отчета
    
    @Enumerated(EnumType.STRING)
    private ReportType reportType; // Тип на отчета
    
    private LocalDate reportDate; // Дата на отчета
    
    private LocalDateTime generatedAt; // Кога е генериран
    
    private Integer totalReceipts; // Общ брой разписки
    
    private BigDecimal totalSales; // Общ оборот
    
    private BigDecimal totalVAT; // Общо ДДС
    
    private BigDecimal totalNetSales; // Общо нетни продажби
    
    private String cashierName; // Име на касиера
    
    private String deviceSerialNumber; // Сериен номер на ФУ
    
    @Enumerated(EnumType.STRING)
    private ReportStatus status; // Статус на отчета
    
    private String notes; // Бележки
    
    @PrePersist
    protected void onCreate() {
        this.generatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ReportStatus.GENERATED;
        }
    }
    
    public enum ReportType {
        DAILY,      // Дневен отчет
        SHIFT,      // Сменен отчет
        MONTHLY,    // Месечен отчет
        YEARLY,     // Годишен отчет
        Z_REPORT,   // Z-отчет (краен дневен)
        X_REPORT    // X-отчет (междинен)
    }
    
    public enum ReportStatus {
        GENERATED,  // Генериран
        SENT_TO_NAF, // Изпратен към НАП
        CONFIRMED,  // Потвърден
        ERROR       // Грешка
    }
}
