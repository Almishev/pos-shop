package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_inventory_alerts")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryAlertEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String alertId;
    
    private String itemId;
    
    @Enumerated(EnumType.STRING)
    private AlertType alertType;
    
    private String alertMessage;
    
    private Integer currentQuantity;
    
    private Integer thresholdQuantity;
    
    private Boolean isResolved;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime resolvedAt;
    
    private String resolvedBy;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.alertId == null) {
            this.alertId = "ALT" + System.currentTimeMillis();
        }
        if (this.isResolved == null) {
            this.isResolved = false;
        }
    }
    
    public enum AlertType {
        LOW_STOCK,          
        OUT_OF_STOCK,       
        OVERSTOCK,          
        EXPIRY_WARNING,    
        EXPIRED,            
        THEFT_SUSPICION,   
        QUALITY_ISSUE,     
        SYSTEM_ERROR        
    }
}
