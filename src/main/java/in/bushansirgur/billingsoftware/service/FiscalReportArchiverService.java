package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.ArchiveDestination;

import java.time.LocalDate;

public interface FiscalReportArchiverService {
    int archiveOldReports();

    int archiveOldReports(ArchiveDestination destination);

    int archiveReportsBefore(LocalDate cutoffDate, ArchiveDestination destination);
}
