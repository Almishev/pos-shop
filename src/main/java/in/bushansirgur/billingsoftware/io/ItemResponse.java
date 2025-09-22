package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemResponse {
    private String itemId;
    private String name;
    private BigDecimal price;
    private String categoryId;
    private String description;
    private String barcode;
    private String categoryName;
    private String imgUrl;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    private Integer stockQuantity;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Integer reorderPoint;
    private String unitOfMeasure;
    private String supplierName;
    private String supplierCode;
    private BigDecimal costPrice;
    private Timestamp lastRestockDate;
    private Timestamp lastStockCheck;
    private String stockStatus; // LOW_STOCK, OUT_OF_STOCK, NORMAL, OVERSTOCK
    private Boolean needsReorder;
    private Integer reorderQuantity;
    private BigDecimal vatRate;
}
