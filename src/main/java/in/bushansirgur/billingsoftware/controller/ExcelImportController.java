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
            if (!isExcelFile(file)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only Excel files (.xlsx) are allowed");
            }

            ExcelImportResponse response = excelImportService.importProductsFromExcel(file);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error processing Excel file: " + e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error importing products: " + e.getMessage());
        }
    }

    private boolean isExcelFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
                contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
                contentType.equals("application/vnd.ms-excel") ||
                file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".xlsx")
        );
    }
}
