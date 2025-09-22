package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_inventory_adjustments")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryAdjustmentEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String adjustmentId;
    
    private String itemId;
    
    @Enumerated(EnumType.STRING)
    private AdjustmentType adjustmentType;
    
    private Integer quantity;
    
    private String reason;
    
    private String notes;
    
    private String createdBy;
    
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.adjustmentId == null) {
            this.adjustmentId = "ADJ" + System.currentTimeMillis();
        }
    }
    
    public enum AdjustmentType {
        COUNT_CORRECTION,  
        DAMAGE,             
        EXPIRY,             
        THEFT,             
        LOSS,                
        FOUND,              
        QUALITY_ISSUE,       
        SYSTEM_CORRECTION,   
        OTHER                
    }
}
