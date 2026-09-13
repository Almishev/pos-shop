package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_deliveries")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String deliveryId;

    private String referenceNumber;

    private String supplierName;

    private LocalDate deliveryDate;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.DRAFT;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime postedAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<DeliveryLineEntity> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.deliveryId == null) {
            this.deliveryId = "DEL" + System.currentTimeMillis();
        }
        if (this.status == null) {
            this.status = DeliveryStatus.DRAFT;
        }
        if (this.deliveryDate == null) {
            this.deliveryDate = LocalDate.now();
        }
    }

    public enum DeliveryStatus {
        DRAFT,
        POSTED
    }
}
