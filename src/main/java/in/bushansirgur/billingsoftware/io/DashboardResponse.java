package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardResponse {

    private Double todaySales;
    private Long todayOrderCount;
    private Double todayVAT;
    private Long todayFiscalReceipts;
    private Long activeDevices;
    private Long totalDevices;
    private List<OrderResponse> recentOrders;
}
