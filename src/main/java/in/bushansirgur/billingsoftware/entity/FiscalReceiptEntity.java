package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_fiscal_receipts")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FiscalReceiptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String fiscalNumber; // Фискален номер от ФУ
    
    private String orderId; // Свързване с поръчката
    
    private String deviceSerialNumber; // Сериен номер на ФУ
    
    private LocalDateTime fiscalDateTime; // Време на фискализация
    
    private BigDecimal subtotal; // Междинна сума
    
    private BigDecimal vatAmount; // ДДС сума
    
    private BigDecimal grandTotal; // Крайна сума
    
    private String qrCode; // QR код за проверка
    
    private String fiscalUrl; // URL за проверка в НАП
    
    @Enumerated(EnumType.STRING)
    private FiscalStatus status; // Статус на фискализацията
    
    private String errorMessage; // Грешка ако има такава
    
    @PrePersist
    protected void onCreate() {
        this.fiscalDateTime = LocalDateTime.now();
        if (this.status == null) {
            this.status = FiscalStatus.PENDING;
        }
    }
    
    public enum FiscalStatus {
        PENDING,    // Чакащ
        SENT,       // Изпратен към ФУ
        CONFIRMED,  // Потвърден от ФУ
        ERROR,      // Грешка
        CANCELLED   // Анулиран
    }
}
