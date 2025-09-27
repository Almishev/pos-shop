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

import java.io.IOException;
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
}
