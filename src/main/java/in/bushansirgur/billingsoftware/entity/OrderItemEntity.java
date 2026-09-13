package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "tbl_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String itemId;
    private String name;
    private String barcode;
    private Double price;
    private Integer quantity;
    /** VAT rate as fraction: 0.20, 0.09, 0.00 (prices are VAT-inclusive). */
    private Double vatRate;
}
