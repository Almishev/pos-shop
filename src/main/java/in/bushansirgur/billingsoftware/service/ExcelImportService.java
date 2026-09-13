package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.ExcelImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ExcelImportService {
    ExcelImportResponse importProductsFromExcel(MultipartFile file) throws IOException;

    /**
     * Import delivery lines from Excel/CSV.
     * Columns: Barcode, Quantity, Unit Cost [, Item Name optional]
     * Creates a DRAFT delivery; optionally posts to stock immediately.
     */
    ExcelImportResponse importDeliveriesFromExcel(
            MultipartFile file,
            String supplierName,
            String referenceNumber,
            String deliveryDate,
            String notes,
            boolean postImmediately,
            String createdBy
    ) throws IOException;
}
