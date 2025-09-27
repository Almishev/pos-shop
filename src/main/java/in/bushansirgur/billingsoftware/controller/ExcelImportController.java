package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.ExcelImportResponse;
import in.bushansirgur.billingsoftware.service.ExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class ExcelImportController {

    private final ExcelImportService excelImportService;

    @PostMapping("/import/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExcelImportResponse> importProducts(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file type
            if (!isValidFile(file)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only Excel (.xlsx) or CSV files are allowed");
            }

            ExcelImportResponse response = excelImportService.importProductsFromExcel(file);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error processing file: " + e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error importing products: " + e.getMessage());
        }
    }

    private boolean isValidFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        
        if (filename == null) return false;
        
        String lowerFilename = filename.toLowerCase();
        
        // Check for Excel files
        boolean isExcel = contentType != null && (
                contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
                contentType.equals("application/vnd.ms-excel") ||
                lowerFilename.endsWith(".xlsx")
        );
        
        // Check for CSV files
        boolean isCsv = contentType != null && (
                contentType.equals("text/csv") ||
                contentType.equals("application/csv") ||
                lowerFilename.endsWith(".csv")
        );
        
        return isExcel || isCsv;
    }
}
