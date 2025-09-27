package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.CategoryEntity;
import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.io.ExcelImportRequest;
import in.bushansirgur.billingsoftware.io.ExcelImportResponse;
import in.bushansirgur.billingsoftware.repository.CategoryRepository;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.service.ExcelImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportServiceImpl implements ExcelImportService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public ExcelImportResponse importProductsFromExcel(MultipartFile file) throws IOException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int successfulImports = 0;
        int failedImports = 0;
        int totalRows = 0;

        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            // Handle CSV file
            return importFromCSV(file, errors, warnings);
        } else {
            // Handle Excel file
            try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);
                
                // Skip header row
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    totalRows++;
                    
                    try {
                        ExcelImportRequest request = parseRow(row);
                        if (request != null) {
                            importProduct(request);
                            successfulImports++;
                        } else {
                            failedImports++;
                            errors.add("Row " + (i + 1) + ": Invalid data format");
                        }
                    } catch (Exception e) {
                        failedImports++;
                        errors.add("Row " + (i + 1) + ": " + e.getMessage());
                        log.error("Error importing row {}: {}", i + 1, e.getMessage());
                    }
                }
            }
        }

        String message = String.format("Import completed. %d successful, %d failed out of %d total rows.", 
                successfulImports, failedImports, totalRows);

        return ExcelImportResponse.builder()
                .totalRows(totalRows)
                .successfulImports(successfulImports)
                .failedImports(failedImports)
                .errors(errors)
                .warnings(warnings)
                .message(message)
                .build();
    }

    private ExcelImportRequest parseRow(Row row) {
        try {
            // Expected columns: Category Name, Category Description, Item Name, Item Description, Barcode, VAT Rate, Price, Stock Quantity
            String categoryName = getCellValueAsString(row.getCell(0));
            String categoryDescription = getCellValueAsString(row.getCell(1));
            String itemName = getCellValueAsString(row.getCell(2));
            String itemDescription = getCellValueAsString(row.getCell(3));
            String barcode = getCellValueAsString(row.getCell(4));
            BigDecimal vatRate = getCellValueAsBigDecimal(row.getCell(5));
            BigDecimal price = getCellValueAsBigDecimal(row.getCell(6));
            Integer stockQuantity = getCellValueAsInteger(row.getCell(7));

            // Validate required fields
            if (categoryName == null || categoryName.trim().isEmpty() ||
                itemName == null || itemName.trim().isEmpty() ||
                price == null) {
                return null;
            }

            return ExcelImportRequest.builder()
                    .categoryName(categoryName.trim())
                    .categoryDescription(categoryDescription != null ? categoryDescription.trim() : "")
                    .itemName(itemName.trim())
                    .itemDescription(itemDescription != null ? itemDescription.trim() : "")
                    .barcode(barcode != null ? barcode.trim() : "")
                    .vatRate(vatRate != null ? vatRate : new BigDecimal("0.20"))
                    .price(price)
                    .stockQuantity(stockQuantity != null ? stockQuantity : 0)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing row: {}", e.getMessage());
            return null;
        }
    }

    private void importProduct(ExcelImportRequest request) {
        // Find or create category
        CategoryEntity category = categoryRepository.findByName(request.getCategoryName())
                .orElseGet(() -> {
                    CategoryEntity newCategory = CategoryEntity.builder()
                            .categoryId(UUID.randomUUID().toString())
                            .name(request.getCategoryName())
                            .description(request.getCategoryDescription())
                            .bgColor("#2c2c2c")
                            .imgUrl("https://shop-software-pirinpixel.s3.eu-central-1.amazonaws.com/supermarket.png")
                            .build();
                    return categoryRepository.save(newCategory);
                });

        // Check if item with same barcode already exists
        if (request.getBarcode() != null && !request.getBarcode().trim().isEmpty()) {
            itemRepository.findByBarcode(request.getBarcode())
                    .ifPresent(item -> {
                        throw new RuntimeException("Item with barcode " + request.getBarcode() + " already exists");
                    });
        }

        // Create item
        ItemEntity item = ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name(request.getItemName())
                .description(request.getItemDescription())
                .price(request.getPrice())
                .barcode(request.getBarcode())
                .vatRate(request.getVatRate())
                .stockQuantity(request.getStockQuantity())
                .category(category)
                .imgUrl("https://shop-software-pirinpixel.s3.eu-central-1.amazonaws.com/supermarket.png")
                .build();

        itemRepository.save(item);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) return null;
        
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING:
                    String stringValue = cell.getStringCellValue().trim();
                    if (stringValue.isEmpty()) return null;
                    return new BigDecimal(stringValue);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getCellValueAsInteger(Cell cell) {
        if (cell == null) return null;
        
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return (int) cell.getNumericCellValue();
                case STRING:
                    String stringValue = cell.getStringCellValue().trim();
                    if (stringValue.isEmpty()) return null;
                    return Integer.parseInt(stringValue);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ExcelImportResponse importFromCSV(MultipartFile file, List<String> errors, List<String> warnings) throws IOException {
        int successfulImports = 0;
        int failedImports = 0;
        int totalRows = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header row
                }
                
                totalRows++;
                
                try {
                    String[] values = parseCSVLine(line);
                    if (values.length >= 7) { // Minimum required columns
                        ExcelImportRequest request = parseCSVRow(values);
                        if (request != null) {
                            importProduct(request);
                            successfulImports++;
                        } else {
                            failedImports++;
                            errors.add("Row " + (totalRows + 1) + ": Invalid data format");
                        }
                    } else {
                        failedImports++;
                        errors.add("Row " + (totalRows + 1) + ": Insufficient columns");
                    }
                } catch (Exception e) {
                    failedImports++;
                    errors.add("Row " + (totalRows + 1) + ": " + e.getMessage());
                    log.error("Error importing CSV row {}: {}", totalRows + 1, e.getMessage());
                }
            }
        }

        String message = String.format("CSV Import completed. %d successful, %d failed out of %d total rows.", 
                successfulImports, failedImports, totalRows);

        return ExcelImportResponse.builder()
                .totalRows(totalRows)
                .successfulImports(successfulImports)
                .failedImports(failedImports)
                .errors(errors)
                .warnings(warnings)
                .message(message)
                .build();
    }

    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private ExcelImportRequest parseCSVRow(String[] values) {
        try {
            // Expected columns: Category Name, Category Description, Item Name, Item Description, Barcode, VAT Rate, Price, Stock Quantity
            String categoryName = values.length > 0 ? values[0] : null;
            String categoryDescription = values.length > 1 ? values[1] : "";
            String itemName = values.length > 2 ? values[2] : null;
            String itemDescription = values.length > 3 ? values[3] : "";
            String barcode = values.length > 4 ? values[4] : "";
            BigDecimal vatRate = values.length > 5 && !values[5].isEmpty() ? new BigDecimal(values[5]) : new BigDecimal("0.20");
            BigDecimal price = values.length > 6 && !values[6].isEmpty() ? new BigDecimal(values[6]) : null;
            Integer stockQuantity = values.length > 7 && !values[7].isEmpty() ? Integer.parseInt(values[7]) : 0;

            // Validate required fields
            if (categoryName == null || categoryName.trim().isEmpty() ||
                itemName == null || itemName.trim().isEmpty() ||
                price == null) {
                return null;
            }

            return ExcelImportRequest.builder()
                    .categoryName(categoryName.trim())
                    .categoryDescription(categoryDescription.trim())
                    .itemName(itemName.trim())
                    .itemDescription(itemDescription.trim())
                    .barcode(barcode.trim())
                    .vatRate(vatRate)
                    .price(price)
                    .stockQuantity(stockQuantity)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing CSV row: {}", e.getMessage());
            return null;
        }
    }
}
