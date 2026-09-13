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
public class ItemRequest {

    private String name;
    private BigDecimal price;
    private String categoryId;
    private String description;
    private String barcode;
    private BigDecimal vatRate; // 0.20, 0.09, 0.00
    /** pcs | kg | l */
    private String unitOfMeasure;
    /** Optional last known delivery/cost price — not required on create */
    private BigDecimal costPrice;
}
