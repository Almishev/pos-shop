package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.io.DashboardResponse;
import in.bushansirgur.billingsoftware.io.OrderResponse;
import in.bushansirgur.billingsoftware.repository.FiscalReceiptRepository;
import in.bushansirgur.billingsoftware.service.FiscalDeviceService;
import in.bushansirgur.billingsoftware.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final OrderService orderService;
    private final FiscalReceiptRepository fiscalReceiptRepository;
    private final FiscalDeviceService fiscalDeviceService;

    @GetMapping
    public DashboardResponse getDashboardData() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        Double todaySale = orderService.sumSalesByDate(today);
        Long todayOrderCount = orderService.countByOrderDate(today);
        Double todayVAT = orderService.sumTaxByDate(today);
        Long todayFiscalReceipts = fiscalReceiptRepository.countByDateRange(startOfDay, endOfDay);

        List<FiscalDeviceEntity> devices = fiscalDeviceService.getAllDevices();
        long totalDevices = devices != null ? devices.size() : 0L;
        long activeDevices = devices == null ? 0L : devices.stream()
                .filter(d -> d.getStatus() == FiscalDeviceEntity.DeviceStatus.ACTIVE)
                .count();

        List<OrderResponse> recentOrders = orderService.findRecentOrders();
        return DashboardResponse.builder()
                .todaySales(todaySale != null ? todaySale : 0.0)
                .todayOrderCount(todayOrderCount != null ? todayOrderCount : 0L)
                .todayVAT(todayVAT != null ? todayVAT : 0.0)
                .todayFiscalReceipts(todayFiscalReceipts != null ? todayFiscalReceipts : 0L)
                .activeDevices(activeDevices)
                .totalDevices(totalDevices)
                .recentOrders(recentOrders)
                .build();
    }
}
