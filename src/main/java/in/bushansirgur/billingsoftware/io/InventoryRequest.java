package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryRequest {
    
    private String itemId;
    private String itemName;
    private String barcode;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String transactionType;
    private String referenceNumber;
    private String referenceType;
    private String notes;
    private String createdBy;
    
    private String adjustmentType;
    private String reason;
    
    private Integer newQuantity;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Integer reorderPoint;
    private String unitOfMeasure;
    private String supplierName;
    private String supplierCode;
    private BigDecimal costPrice;
}
