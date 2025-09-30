package in.bushansirgur.billingsoftware.service;

import java.time.LocalDate;

public interface ReportExportService {

    /**
     * Export orders for date range [from, to] inclusive to S3 as CSV.
     * Returns the S3 key of the created report.
     */
    String exportOrdersCsv(LocalDate from, LocalDate to);

    /**
     * Generate daily report for yesterday (for NAP compliance).
     * Returns the S3 key of the created report.
     */
    String generateDailyReport();

    java.util.List<in.bushansirgur.billingsoftware.io.CashierSummaryResponse> getCashierSummaries(java.time.LocalDate from, java.time.LocalDate to);
}


