package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.FiscalReportRequest;
import in.bushansirgur.billingsoftware.io.FiscalReportResponse;

import java.time.LocalDate;
import java.util.List;

public interface FiscalReportService {
    
    FiscalReportResponse generateDailyReport(FiscalReportRequest request);
    
    FiscalReportResponse generateShiftReport(FiscalReportRequest request);
    
    FiscalReportResponse generateStoreDailyReport(FiscalReportRequest request);
    
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
    
    // Reset data after reports
    void resetDataAfterShiftReport(String cashierUsername, LocalDate date);
    void resetDataAfterStoreDailyReport(LocalDate date);
    
    // Send to NAP
    // NOTE: In real systems, fiscal devices automatically send Z-reports to NAP.
    // These methods are for tracking/logging purposes or manual submission scenarios.
    boolean sendStoreDailyReportToNAP(Long reportId);
    boolean sendShiftReportToNAP(Long reportId);
    
    // Export to XML (optional feature for archiving, backup, or manual submission)
    // NOTE: Fiscal devices automatically send data to NAP. XML export is supplementary.
    String exportReportToXML(Long reportId);
    String exportReportToXML(String reportNumber);
}
