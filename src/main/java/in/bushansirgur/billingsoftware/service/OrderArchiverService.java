package in.bushansirgur.billingsoftware.service;

import java.time.LocalDate;

public interface OrderArchiverService {

    /**
     * Archive orders older than retentionMonths to S3 and purge after success.
     * Returns number of archived orders.
     */
    int archiveOldOrders();

    /**
     * Archive orders older than the specified cutoff date to S3 and purge after success.
     * Returns number of archived orders.
     */
    int archiveOrdersBefore(LocalDate cutoffDate);

    /**
     * Export orders in given [inclusive) month (YYYY, MM) to S3 without purge.
     */
    int exportMonth(int year, int month);
}


