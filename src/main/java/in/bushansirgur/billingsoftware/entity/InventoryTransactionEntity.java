package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_inventory_transactions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryTransactionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String transactionId;
    
    private String itemId;
    
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    
    private Integer quantity;
    
    private Integer previousQuantity;
    
    private Integer newQuantity;
    
    private BigDecimal unitPrice;
    
    private BigDecimal totalValue;
    
    private String referenceNumber;
    
    private String referenceType;
    
    private String notes;
    
    private String createdBy;
    
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.transactionId == null) {
            this.transactionId = "TXN" + System.currentTimeMillis();
        }
    }
    
    public enum TransactionType {
        SALE,          
        PURCHASE,      
        ADJUSTMENT,     
        TRANSFER_IN,    
        TRANSFER_OUT,   
        RETURN,         
        DAMAGED,        
        EXPIRED,        
        LOST,           
        FOUND           
    }
}
