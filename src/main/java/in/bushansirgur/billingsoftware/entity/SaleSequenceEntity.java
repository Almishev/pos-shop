package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_sale_sequence")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaleSequenceEntity {

    /** 8-digit fiscal device individual number. */
    @Id
    @Column(length = 8)
    private String individualNumber;

    /** Last issued sale sequence for this device. The next sale uses lastNumber + 1. */
    private long lastNumber;
}
