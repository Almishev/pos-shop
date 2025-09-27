package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_stock_movements")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockMovementEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String movementId;
    
    private String itemId;
    
    @Enumerated(EnumType.STRING)
    private MovementType movementType;
    
    private Integer quantity;
    private Integer previousQuantity;
    private Integer newQuantity;
    
    private String reason;
    private String referenceNumber;
    private String referenceType;
    
    private String createdBy;
    private LocalDateTime createdAt;
    
    public enum MovementType {
        IN, OUT, ADJUSTMENT, TRANSFER
    }
}
