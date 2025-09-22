package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;


import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "tbl_items")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String itemId;

    private String name;

    private BigDecimal price;

    private String description;

    @Column(unique = true)
    private String barcode;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;
    @UpdateTimestamp
    private Timestamp updatedAt;

    private String imgUrl;
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private CategoryEntity category;
    
    @Builder.Default
    private Integer stockQuantity = 0;
    @Builder.Default
    private Integer minStockLevel = 0;
    @Builder.Default
    private Integer maxStockLevel = 1000;
    @Builder.Default
    private Integer reorderPoint = 10;
    @Builder.Default
    private String unitOfMeasure = "pcs";
    private String supplierName;
    private String supplierCode;
    private BigDecimal costPrice;
    private Timestamp lastRestockDate;
    private Timestamp lastStockCheck;

    // Bulgarian VAT support: item-specific VAT rate (e.g., 0.20, 0.09, 0.00)
    @Builder.Default
    private BigDecimal vatRate = new BigDecimal("0.20");
}
