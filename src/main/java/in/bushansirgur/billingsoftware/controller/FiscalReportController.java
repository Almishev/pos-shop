package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.FiscalReportRequest;
import in.bushansirgur.billingsoftware.io.FiscalReportResponse;
import in.bushansirgur.billingsoftware.service.FiscalReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1.0/fiscal/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FiscalReportController {
    
    private final FiscalReportService fiscalReportService;
    
    // Генериране на отчети
    @PostMapping("/daily")
    public ResponseEntity<FiscalReportResponse> generateDailyReport(@RequestBody FiscalReportRequest request) {
        return ResponseEntity.ok(fiscalReportService.generateDailyReport(request));
    }
    
    @PostMapping("/shift")
    public ResponseEntity<FiscalReportResponse> generateShiftReport(@RequestBody FiscalReportRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            request.setCashierName(authentication.getName());
        }
        return ResponseEntity.ok(fiscalReportService.generateShiftReport(request));
    }
    
    @PostMapping("/monthly")
    public ResponseEntity<FiscalReportResponse> generateMonthlyReport(@RequestBody FiscalReportRequest request) {
        return ResponseEntity.ok(fiscalReportService.generateMonthlyReport(request));
    }
    
    @PostMapping("/yearly")
    public ResponseEntity<FiscalReportResponse> generateYearlyReport(@RequestBody FiscalReportRequest request) {
        return ResponseEntity.ok(fiscalReportService.generateYearlyReport(request));
    }
    
    // Извличане на отчети
    @GetMapping
    public ResponseEntity<List<FiscalReportResponse>> getAllReports() {
        return ResponseEntity.ok(fiscalReportService.getAllReports());
    }
    
    @GetMapping("/{reportId}")
    public ResponseEntity<FiscalReportResponse> getReportById(@PathVariable Long reportId) {
        return ResponseEntity.ok(fiscalReportService.getReportById(reportId));
    }
    
    @GetMapping("/number/{reportNumber}")
    public ResponseEntity<FiscalReportResponse> getReportByNumber(@PathVariable String reportNumber) {
        return ResponseEntity.ok(fiscalReportService.getReportByNumber(reportNumber));
    }
    
    @GetMapping("/date-range")
    public ResponseEntity<List<FiscalReportResponse>> getReportsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(fiscalReportService.getReportsByDateRange(startDate, endDate));
    }
    
    @GetMapping("/type/{reportType}")
    public ResponseEntity<List<FiscalReportResponse>> getReportsByType(@PathVariable String reportType) {
        return ResponseEntity.ok(fiscalReportService.getReportsByType(reportType));
    }
    
    @GetMapping("/device/{deviceSerialNumber}")
    public ResponseEntity<List<FiscalReportResponse>> getReportsByDevice(@PathVariable String deviceSerialNumber) {
        return ResponseEntity.ok(fiscalReportService.getReportsByDevice(deviceSerialNumber));
    }
    
    // Изпращане към НАП
    @PostMapping("/{reportId}/send-to-naf")
    public ResponseEntity<Boolean> sendReportToNAF(@PathVariable Long reportId) {
        return ResponseEntity.ok(fiscalReportService.sendReportToNAF(reportId));
    }
    
    @PostMapping("/number/{reportNumber}/send-to-naf")
    public ResponseEntity<Boolean> sendReportToNAFByNumber(@PathVariable String reportNumber) {
        return ResponseEntity.ok(fiscalReportService.sendReportToNAF(reportNumber));
    }
    
    // Статистика
    @GetMapping("/stats/sales/{date}")
    public ResponseEntity<Double> getTotalSalesForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(fiscalReportService.getTotalSalesForDate(date));
    }
    
    @GetMapping("/stats/vat/{date}")
    public ResponseEntity<Double> getTotalVATForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(fiscalReportService.getTotalVATForDate(date));
    }
    
    @GetMapping("/stats/receipts/{date}")
    public ResponseEntity<Integer> getTotalReceiptsForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(fiscalReportService.getTotalReceiptsForDate(date));
    }
}
