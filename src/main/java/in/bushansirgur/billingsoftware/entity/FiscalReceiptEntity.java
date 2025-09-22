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
    private String fiscalNumber; 
    
    private String orderId; 
    
    private String deviceSerialNumber; 
    
    private LocalDateTime fiscalDateTime; 
    
    private BigDecimal subtotal; 
    
    private BigDecimal vatAmount; 
    
    private BigDecimal grandTotal;
    
    private String qrCode; 
    
    private String fiscalUrl; 
    
    @Enumerated(EnumType.STRING)
    private FiscalStatus status; 
    
    private String errorMessage; 
    
    @PrePersist
    protected void onCreate() {
        this.fiscalDateTime = LocalDateTime.now();
        if (this.status == null) {
            this.status = FiscalStatus.PENDING;
        }
    }
    
    public enum FiscalStatus {
        PENDING,    
        SENT,       
        CONFIRMED,  
        ERROR,   
        CANCELLED   
    }
}
