package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.ArchiveDestination;

import java.time.LocalDate;

public interface OrderArchiverService {

    /** Archive orders older than retention using schedule destination (default local). */
    int archiveOldOrders();

    int archiveOldOrders(ArchiveDestination destination);

    int archiveOrdersBefore(LocalDate cutoffDate, ArchiveDestination destination);

    int exportMonth(int year, int month, ArchiveDestination destination);
}
