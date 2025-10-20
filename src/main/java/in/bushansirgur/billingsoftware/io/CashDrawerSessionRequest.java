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
public class CashDrawerSessionRequest {
    private String cashierUsername;
    private BigDecimal startAmount;
    private BigDecimal endAmount;
    private String notes;
    private String registerId;
    private String deviceSerialNumber;
}
