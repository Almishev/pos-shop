package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.FiscalReportRequest;
import in.bushansirgur.billingsoftware.io.FiscalReportResponse;

import java.time.LocalDate;
import java.util.List;

public interface FiscalReportService {
    
    FiscalReportResponse generateShiftReport(FiscalReportRequest request);
    
    FiscalReportResponse generateStoreDailyReport(FiscalReportRequest request);
    
    FiscalReportResponse generateMonthlyReport(FiscalReportRequest request);
    
    FiscalReportResponse generateYearlyReport(FiscalReportRequest request);
    
    List<FiscalReportResponse> getAllReports();

    org.springframework.data.domain.Page<FiscalReportResponse> getReportsPage(
            org.springframework.data.domain.Pageable pageable,
            String reportType,
            java.time.LocalDate dateFrom,
            java.time.LocalDate dateTo
    );
    
    FiscalReportResponse getReportById(Long reportId);
    
    FiscalReportResponse getReportByNumber(String reportNumber);
    
    List<FiscalReportResponse> getReportsByDateRange(LocalDate startDate, LocalDate endDate);
    
    List<FiscalReportResponse> getReportsByType(String reportType);
    
    List<FiscalReportResponse> getReportsByDevice(String deviceSerialNumber);
    
    boolean sendReportToNAF(Long reportId);
    
    boolean sendReportToNAF(String reportNumber);
    
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
