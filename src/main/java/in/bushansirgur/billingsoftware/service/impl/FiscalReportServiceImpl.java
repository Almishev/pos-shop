package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.FiscalReportEntity;
import in.bushansirgur.billingsoftware.io.FiscalReportRequest;
import in.bushansirgur.billingsoftware.io.FiscalReportResponse;
import in.bushansirgur.billingsoftware.repository.FiscalReceiptRepository;
import in.bushansirgur.billingsoftware.repository.FiscalReportRepository;
import in.bushansirgur.billingsoftware.service.FiscalReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalReportServiceImpl implements FiscalReportService {
    
    private final FiscalReportRepository fiscalReportRepository;
    private final FiscalReceiptRepository fiscalReceiptRepository;
    
    @Override
    public FiscalReportResponse generateDailyReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        
        // Изчисляване на статистика за деня
        LocalDateTime startOfDay = reportDate.atStartOfDay();
        LocalDateTime endOfDay = reportDate.atTime(LocalTime.MAX);
        
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.DAILY, reportDate, reportDate);
        
        if (!existingReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Daily report for date " + reportDate + " already exists");
        }
        
        // Извличане на данни за деня
        Long totalReceipts = fiscalReceiptRepository.countByDateRange(startOfDay, endOfDay);
        Double totalSales = fiscalReceiptRepository.sumGrandTotalByDateRange(startOfDay, endOfDay);
        Double totalVAT = fiscalReceiptRepository.sumVatAmountByDateRange(startOfDay, endOfDay);
        
        // Създаване на отчета
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.DAILY, reportDate))
                .reportType(FiscalReportEntity.ReportType.DAILY)
                .reportDate(reportDate)
                .totalReceipts(totalReceipts != null ? totalReceipts.intValue() : 0)
                .totalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO)
                .totalVAT(totalVAT != null ? BigDecimal.valueOf(totalVAT) : BigDecimal.ZERO)
                .totalNetSales(totalSales != null && totalVAT != null ? 
                        BigDecimal.valueOf(totalSales - totalVAT) : BigDecimal.ZERO)
                .cashierName(request.getCashierName())
                .deviceSerialNumber(request.getDeviceSerialNumber())
                .notes(request.getNotes())
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Daily report generated: {}", report.getReportNumber());
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse generateShiftReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        
        // Симулация на сменен отчет (в реалност ще има логика за смени)
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.SHIFT, reportDate))
                .reportType(FiscalReportEntity.ReportType.SHIFT)
                .reportDate(reportDate)
                .totalReceipts(0)
                .totalSales(BigDecimal.ZERO)
                .totalVAT(BigDecimal.ZERO)
                .totalNetSales(BigDecimal.ZERO)
                .cashierName(request.getCashierName())
                .deviceSerialNumber(request.getDeviceSerialNumber())
                .notes(request.getNotes())
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Shift report generated: {}", report.getReportNumber());
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse generateMonthlyReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        LocalDate startOfMonth = reportDate.withDayOfMonth(1);
        LocalDate endOfMonth = reportDate.withDayOfMonth(reportDate.lengthOfMonth());
        
        // Проверка за съществуващ месечен отчет
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.MONTHLY, startOfMonth, endOfMonth);
        
        if (!existingReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Monthly report for " + reportDate.getMonth() + " " + reportDate.getYear() + " already exists");
        }
        
        // Изчисляване на месечна статистика
        LocalDateTime startOfMonthDateTime = startOfMonth.atStartOfDay();
        LocalDateTime endOfMonthDateTime = endOfMonth.atTime(LocalTime.MAX);
        
        Long totalReceipts = fiscalReceiptRepository.countByDateRange(startOfMonthDateTime, endOfMonthDateTime);
        Double totalSales = fiscalReceiptRepository.sumGrandTotalByDateRange(startOfMonthDateTime, endOfMonthDateTime);
        Double totalVAT = fiscalReceiptRepository.sumVatAmountByDateRange(startOfMonthDateTime, endOfMonthDateTime);
        
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.MONTHLY, reportDate))
                .reportType(FiscalReportEntity.ReportType.MONTHLY)
                .reportDate(reportDate)
                .totalReceipts(totalReceipts != null ? totalReceipts.intValue() : 0)
                .totalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO)
                .totalVAT(totalVAT != null ? BigDecimal.valueOf(totalVAT) : BigDecimal.ZERO)
                .totalNetSales(totalSales != null && totalVAT != null ? 
                        BigDecimal.valueOf(totalSales - totalVAT) : BigDecimal.ZERO)
                .cashierName(request.getCashierName())
                .deviceSerialNumber(request.getDeviceSerialNumber())
                .notes(request.getNotes())
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Monthly report generated: {}", report.getReportNumber());
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse generateYearlyReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        LocalDate startOfYear = reportDate.withDayOfYear(1);
        LocalDate endOfYear = reportDate.withDayOfYear(reportDate.lengthOfYear());
        
        // Проверка за съществуващ годишен отчет
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.YEARLY, startOfYear, endOfYear);
        
        if (!existingReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Yearly report for " + reportDate.getYear() + " already exists");
        }
        
        // Изчисляване на годишна статистика
        LocalDateTime startOfYearDateTime = startOfYear.atStartOfDay();
        LocalDateTime endOfYearDateTime = endOfYear.atTime(LocalTime.MAX);
        
        Long totalReceipts = fiscalReceiptRepository.countByDateRange(startOfYearDateTime, endOfYearDateTime);
        Double totalSales = fiscalReceiptRepository.sumGrandTotalByDateRange(startOfYearDateTime, endOfYearDateTime);
        Double totalVAT = fiscalReceiptRepository.sumVatAmountByDateRange(startOfYearDateTime, endOfYearDateTime);
        
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.YEARLY, reportDate))
                .reportType(FiscalReportEntity.ReportType.YEARLY)
                .reportDate(reportDate)
                .totalReceipts(totalReceipts != null ? totalReceipts.intValue() : 0)
                .totalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO)
                .totalVAT(totalVAT != null ? BigDecimal.valueOf(totalVAT) : BigDecimal.ZERO)
                .totalNetSales(totalSales != null && totalVAT != null ? 
                        BigDecimal.valueOf(totalSales - totalVAT) : BigDecimal.ZERO)
                .cashierName(request.getCashierName())
                .deviceSerialNumber(request.getDeviceSerialNumber())
                .notes(request.getNotes())
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Yearly report generated: {}", report.getReportNumber());
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public List<FiscalReportResponse> getAllReports() {
        return fiscalReportRepository.findAll().stream()
                .map(FiscalReportResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public FiscalReportResponse getReportById(Long reportId) {
        FiscalReportEntity report = fiscalReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with id: " + reportId));
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse getReportByNumber(String reportNumber) {
        FiscalReportEntity report = fiscalReportRepository.findByReportNumber(reportNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with number: " + reportNumber));
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public List<FiscalReportResponse> getReportsByDateRange(LocalDate startDate, LocalDate endDate) {
        return fiscalReportRepository.findByDateRange(startDate, endDate).stream()
                .map(FiscalReportResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<FiscalReportResponse> getReportsByType(String reportType) {
        try {
            FiscalReportEntity.ReportType type = FiscalReportEntity.ReportType.valueOf(reportType.toUpperCase());
            return fiscalReportRepository.findByReportType(type).stream()
                    .map(FiscalReportResponse::fromEntity)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report type: " + reportType);
        }
    }
    
    @Override
    public List<FiscalReportResponse> getReportsByDevice(String deviceSerialNumber) {
        return fiscalReportRepository.findByDeviceSerialNumber(deviceSerialNumber).stream()
                .map(FiscalReportResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean sendReportToNAF(Long reportId) {
        FiscalReportEntity report = fiscalReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with id: " + reportId));
        
        // Симулация на изпращане към НАП
        report.setStatus(FiscalReportEntity.ReportStatus.SENT_TO_NAF);
        fiscalReportRepository.save(report);
        
        log.info("Report sent to NAF: {}", report.getReportNumber());
        return true;
    }
    
    @Override
    public boolean sendReportToNAF(String reportNumber) {
        FiscalReportEntity report = fiscalReportRepository.findByReportNumber(reportNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with number: " + reportNumber));
        
        // Симулация на изпращане към НАП
        report.setStatus(FiscalReportEntity.ReportStatus.SENT_TO_NAF);
        fiscalReportRepository.save(report);
        
        log.info("Report sent to NAF: {}", report.getReportNumber());
        return true;
    }
    
    @Override
    public Double getTotalSalesForDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        return fiscalReceiptRepository.sumGrandTotalByDateRange(startOfDay, endOfDay);
    }
    
    @Override
    public Double getTotalVATForDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        return fiscalReceiptRepository.sumVatAmountByDateRange(startOfDay, endOfDay);
    }
    
    @Override
    public Integer getTotalReceiptsForDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        Long count = fiscalReceiptRepository.countByDateRange(startOfDay, endOfDay);
        return count != null ? count.intValue() : 0;
    }
    
    // Помощен метод за генериране на номер на отчет
    private String generateReportNumber(FiscalReportEntity.ReportType reportType, LocalDate date) {
        String typePrefix = switch (reportType) {
            case DAILY -> "DR";
            case SHIFT -> "SR";
            case MONTHLY -> "MR";
            case YEARLY -> "YR";
            case Z_REPORT -> "ZR";
            case X_REPORT -> "XR";
        };
        
        return typePrefix + "-" + date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + 
               "-" + String.format("%06d", (int)(Math.random() * 999999));
    }
}
