package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.ArchiveDestination;

import java.time.LocalDate;

public interface ReportExportService {

    /**
     * Export orders for date range [from, to] inclusive as CSV to local disk or S3.
     * Returns the local path or S3 key of the created report.
     */
    String exportOrdersCsv(LocalDate from, LocalDate to, ArchiveDestination destination);

    java.util.List<in.bushansirgur.billingsoftware.io.CashierSummaryResponse> getCashierSummaries(LocalDate from, LocalDate to);
}
