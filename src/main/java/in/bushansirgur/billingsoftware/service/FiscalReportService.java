package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.FiscalReportRequest;
import in.bushansirgur.billingsoftware.io.FiscalReportResponse;

import java.time.LocalDate;
import java.util.List;

public interface FiscalReportService {
    
    FiscalReportResponse generateDailyReport(FiscalReportRequest request);
    
    FiscalReportResponse generateShiftReport(FiscalReportRequest request);
    
    FiscalReportResponse generateMonthlyReport(FiscalReportRequest request);
    
    FiscalReportResponse generateYearlyReport(FiscalReportRequest request);
    
    List<FiscalReportResponse> getAllReports();
    
    FiscalReportResponse getReportById(Long reportId);
    
    FiscalReportResponse getReportByNumber(String reportNumber);
    
    List<FiscalReportResponse> getReportsByDateRange(LocalDate startDate, LocalDate endDate);
    
    List<FiscalReportResponse> getReportsByType(String reportType);
    
    List<FiscalReportResponse> getReportsByDevice(String deviceSerialNumber);
    
    boolean sendReportToNAF(Long reportId);
    
    boolean sendReportToNAF(String reportNumber);
    
    Double getTotalSalesForDate(LocalDate date);
    
    Double getTotalVATForDate(LocalDate date);
    
    Integer getTotalReceiptsForDate(LocalDate date);
}
