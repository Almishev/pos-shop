package in.bushansirgur.billingsoftware.entity;

import in.bushansirgur.billingsoftware.io.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_audit_log", indexes = {
        @Index(name = "idx_audit_occurred_at", columnList = "occurredAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String operatorName;

    @Column(length = 4)
    private String operatorCode;

    private String role;

    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AuditAction action;

    @Column(length = 4000)
    private String description;

    @Column(length = 21)
    private String uniqueSaleNumber;
}
