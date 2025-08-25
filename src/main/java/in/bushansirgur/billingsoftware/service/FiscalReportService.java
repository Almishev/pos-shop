package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.FiscalReportRequest;
import in.bushansirgur.billingsoftware.io.FiscalReportResponse;

import java.time.LocalDate;
import java.util.List;

public interface FiscalReportService {
    
    // Генериране на отчети
    FiscalReportResponse generateDailyReport(FiscalReportRequest request);
    
    FiscalReportResponse generateShiftReport(FiscalReportRequest request);
    
    FiscalReportResponse generateMonthlyReport(FiscalReportRequest request);
    
    FiscalReportResponse generateYearlyReport(FiscalReportRequest request);
    
    // Извличане на отчети
    List<FiscalReportResponse> getAllReports();
    
    FiscalReportResponse getReportById(Long reportId);
    
    FiscalReportResponse getReportByNumber(String reportNumber);
    
    List<FiscalReportResponse> getReportsByDateRange(LocalDate startDate, LocalDate endDate);
    
    List<FiscalReportResponse> getReportsByType(String reportType);
    
    List<FiscalReportResponse> getReportsByDevice(String deviceSerialNumber);
    
    // Изпращане към НАП
    boolean sendReportToNAF(Long reportId);
    
    boolean sendReportToNAF(String reportNumber);
    
    // Статистика
    Double getTotalSalesForDate(LocalDate date);
    
    Double getTotalVATForDate(LocalDate date);
    
    Integer getTotalReceiptsForDate(LocalDate date);
}
