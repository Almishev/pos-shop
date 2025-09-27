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
public class ExcelImportRequest {
    private String categoryName;
    private String categoryDescription;
    private String itemName;
    private String itemDescription;
    private String barcode;
    private BigDecimal vatRate;
    private BigDecimal price;
    private Integer stockQuantity;
}
