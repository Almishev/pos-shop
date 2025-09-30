package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CashierSummaryResponse {
    private String cashierUsername;
    private Long totalOrders;
    private Double totalAmount;
}


