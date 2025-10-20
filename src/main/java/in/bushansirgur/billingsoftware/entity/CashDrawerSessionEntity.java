package in.bushansirgur.billingsoftware.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_cash_drawer_sessions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CashDrawerSessionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDate sessionDate;
    
    @Column(nullable = false)
    private String cashierUsername;
    
    @Column(nullable = false)
    private BigDecimal startAmount;
    
    private BigDecimal endAmount;
    
    @Column(nullable = false)
    private LocalDateTime sessionStartTime;
    
    private LocalDateTime sessionEndTime;
    
    // Logical register identifier (till). Optional if you only have one.
    private String registerId;

    // Bound fiscal device (ЕКАФП) serial number used during this session
    private String deviceSerialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;
    
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        if (this.sessionStartTime == null) {
            this.sessionStartTime = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = SessionStatus.ACTIVE;
        }
    }
    
    public enum SessionStatus {
        ACTIVE,     // Активна сесия
        CLOSED,     // Приключена сесия
        SUSPENDED   // Временно спряна сесия
    }
}
