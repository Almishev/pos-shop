package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryResponse {
    
    private String itemId;
    private String itemName;
    private String barcode;
    private Integer currentStock;
    private Integer previousStock;
    private Integer newStock;
    private BigDecimal unitPrice;
    private BigDecimal totalValue;
    private String transactionType;
    private String transactionId;
    private String referenceNumber;
    private String referenceType;
    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
    
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Integer reorderPoint;
    private String unitOfMeasure;
    private String supplierName;
    private String supplierCode;
    private BigDecimal costPrice;
    private LocalDateTime lastRestockDate;
    private LocalDateTime lastStockCheck;
    

    private String stockStatus; // LOW_STOCK, OUT_OF_STOCK, NORMAL, OVERSTOCK
    private Boolean needsReorder;
    private Integer reorderQuantity;
}
