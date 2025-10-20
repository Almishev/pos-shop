package in.bushansirgur.billingsoftware.io;

import in.bushansirgur.billingsoftware.entity.CashDrawerSessionEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CashDrawerSessionResponse {
    private Long id;
    private LocalDate sessionDate;
    private String cashierUsername;
    private BigDecimal startAmount;
    private BigDecimal endAmount;
    private LocalDateTime sessionStartTime;
    private LocalDateTime sessionEndTime;
    private CashDrawerSessionEntity.SessionStatus status;
    private String notes;
    private String registerId;
    private String deviceSerialNumber;
    
    public static CashDrawerSessionResponse fromEntity(CashDrawerSessionEntity entity) {
        return CashDrawerSessionResponse.builder()
                .id(entity.getId())
                .sessionDate(entity.getSessionDate())
                .cashierUsername(entity.getCashierUsername())
                .startAmount(entity.getStartAmount())
                .endAmount(entity.getEndAmount())
                .sessionStartTime(entity.getSessionStartTime())
                .sessionEndTime(entity.getSessionEndTime())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .registerId(entity.getRegisterId())
                .deviceSerialNumber(entity.getDeviceSerialNumber())
                .build();
    }
}
