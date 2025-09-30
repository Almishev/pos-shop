package in.bushansirgur.billingsoftware.service;

public interface OrderArchiverService {

    /**
     * Archive orders older than retentionMonths to S3 and purge after success.
     * Returns number of archived orders.
     */
    int archiveOldOrders();

    /**
     * Export orders in given [inclusive) month (YYYY, MM) to S3 without purge.
     */
    int exportMonth(int year, int month);
}


