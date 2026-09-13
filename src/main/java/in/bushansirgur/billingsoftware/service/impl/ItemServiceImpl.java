package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.CategoryEntity;
import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.io.ItemRequest;
import in.bushansirgur.billingsoftware.io.ItemResponse;
import in.bushansirgur.billingsoftware.repository.CategoryRepository;
import in.bushansirgur.billingsoftware.repository.InventoryAdjustmentRepository;
import in.bushansirgur.billingsoftware.repository.InventoryAlertRepository;
import in.bushansirgur.billingsoftware.repository.InventoryTransactionRepository;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.repository.PromotionRepository;
import in.bushansirgur.billingsoftware.repository.StockMovementRepository;
import in.bushansirgur.billingsoftware.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryAlertRepository inventoryAlertRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final PromotionRepository promotionRepository;
    private final StockMovementRepository stockMovementRepository;

    @Override
    public ItemResponse add(ItemRequest request, MultipartFile file) throws IOException {
        // Check if barcode already exists
        if (request.getBarcode() != null && !request.getBarcode().trim().isEmpty()) {
            itemRepository.findByBarcode(request.getBarcode())
                    .ifPresent(item -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Item with barcode " + request.getBarcode() + " already exists");
                    });
        }
        
        ItemEntity newItem = convertToEntity(request);
        CategoryEntity existingCategory = categoryRepository.findByCategoryId(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found: "+request.getCategoryId()));
        newItem.setCategory(existingCategory);
        newItem.setImgUrl(null);
        newItem = itemRepository.save(newItem);
        return convertToResponse(newItem);
    }

    private ItemResponse convertToResponse(ItemEntity newItem) {
        // Determine stock status
        String stockStatus = determineStockStatus(newItem);
        Boolean needsReorder = newItem.getStockQuantity() != null && 
                              newItem.getReorderPoint() != null && 
                              newItem.getStockQuantity() <= newItem.getReorderPoint();
        Integer reorderQuantity = needsReorder && newItem.getMaxStockLevel() != null ? 
            newItem.getMaxStockLevel() - newItem.getStockQuantity() : 0;
        
        // Generate itemId if missing (for legacy items)
        String itemId = newItem.getItemId();
        if (itemId == null || itemId.trim().isEmpty()) {
            itemId = java.util.UUID.randomUUID().toString();
            newItem.setItemId(itemId);
            itemRepository.save(newItem); // Save the generated itemId
        }
        
        return ItemResponse.builder()
                .itemId(itemId)
                .name(newItem.getName())
                .description(newItem.getDescription())
                .price(newItem.getPrice())
                .barcode(newItem.getBarcode())
                .imgUrl(newItem.getImgUrl())
                .categoryName(newItem.getCategory().getName())
                .categoryId(newItem.getCategory().getCategoryId())
                .createdAt(newItem.getCreatedAt())
                .updatedAt(newItem.getUpdatedAt())
                .stockQuantity(newItem.getStockQuantity())
                .minStockLevel(newItem.getMinStockLevel())
                .maxStockLevel(newItem.getMaxStockLevel())
                .reorderPoint(newItem.getReorderPoint())
                .unitOfMeasure(newItem.getUnitOfMeasure())
                .supplierName(newItem.getSupplierName())
                .supplierCode(newItem.getSupplierCode())
                .costPrice(newItem.getCostPrice())
                .lastRestockDate(newItem.getLastRestockDate())
                .lastStockCheck(newItem.getLastStockCheck())
                .stockStatus(stockStatus)
                .needsReorder(needsReorder)
                .reorderQuantity(reorderQuantity)
                .vatRate(newItem.getVatRate())
                .build();
    }
    
    private String determineStockStatus(ItemEntity item) {
        if (item.getStockQuantity() == null || item.getStockQuantity() <= 0) {
            return "OUT_OF_STOCK";
        } else if (item.getReorderPoint() != null && item.getStockQuantity() <= item.getReorderPoint()) {
            return "LOW_STOCK";
        } else if (item.getMaxStockLevel() != null && item.getStockQuantity() > item.getMaxStockLevel()) {
            return "OVERSTOCK";
        } else {
            return "NORMAL";
        }
    }

    private ItemEntity convertToEntity(ItemRequest request) {
        String barcode = request.getBarcode();
        if (barcode == null || barcode.trim().isEmpty()) {
            // Generate valid EAN-13 if none provided
            String base12 = String.valueOf(System.currentTimeMillis());
            if (base12.length() > 12) {
                base12 = base12.substring(base12.length() - 12);
            } else if (base12.length() < 12) {
                base12 = ("000000000000" + base12).substring(base12.length());
            }
            int checksum = computeEan13Checksum(base12);
            barcode = base12 + checksum;
        }
        
        return ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .barcode(barcode)
                .vatRate(request.getVatRate() == null ? new java.math.BigDecimal("0.20") : request.getVatRate())
                .unitOfMeasure(normalizeUnitOfMeasure(request.getUnitOfMeasure()))
                .costPrice(request.getCostPrice())
                .build();
    }

    private String normalizeUnitOfMeasure(String unit) {
        if (unit == null || unit.trim().isEmpty()) {
            return "pcs";
        }
        String normalized = unit.trim().toLowerCase();
        return switch (normalized) {
            case "kg", "кг", "kilogram", "kilograms" -> "kg";
            case "l", "lt", "л", "л.", "liter", "litre", "литър", "литри" -> "l";
            case "pcs", "pc", "бр", "бр.", "брой", "бройка", "бройки", "piece", "pieces" -> "pcs";
            default -> "pcs";
        };
    }

    private int computeEan13Checksum(String base12Digits) {
        int sum = 0;
        for (int i = 0; i < base12Digits.length(); i++) {
            int digit = base12Digits.charAt(i) - '0';
            sum += digit * ((i % 2 == 0) ? 1 : 3);
        }
        return (10 - (sum % 10)) % 10;
    }

    @Override
    public List<ItemResponse> fetchItems() {
        System.out.println("=== ItemServiceImpl.fetchItems called ===");
        List<ItemEntity> allItems = itemRepository.findAll();
        System.out.println("Total items in database: " + allItems.size());
        
        return allItems.stream()
                .map(itemEntity -> {
                    System.out.println("Processing item: " + itemEntity.getName() + 
                                     " (ID: " + itemEntity.getId() + 
                                     ", itemId: " + itemEntity.getItemId() + ")");
                    
                    // Ensure itemId exists for all items
                    if (itemEntity.getItemId() == null || itemEntity.getItemId().trim().isEmpty()) {
                        String newItemId = java.util.UUID.randomUUID().toString();
                        System.out.println("Generating new itemId for " + itemEntity.getName() + ": " + newItemId);
                        itemEntity.setItemId(newItemId);
                        itemRepository.save(itemEntity);
                    }
                    return convertToResponse(itemEntity);
                })
                .collect(Collectors.toList());
    }

    @Override
    public ItemResponse findItemByBarcode(String barcode) {
        ItemEntity item = itemRepository.findByBarcode(barcode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found with barcode: " + barcode));
        return convertToResponse(item);
    }

    @Override
    public List<ItemResponse> searchItems(String searchTerm) {
        return itemRepository.findByNameContainingOrBarcodeContaining(searchTerm)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ItemResponse update(String itemId, ItemRequest request, MultipartFile file) throws IOException {
        ItemEntity existingItem = itemRepository.findByItemId(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));

        // Update basic fields
        existingItem.setName(request.getName());
        existingItem.setDescription(request.getDescription());
        existingItem.setPrice(request.getPrice());
        existingItem.setBarcode(request.getBarcode());
        existingItem.setVatRate(request.getVatRate());
        if (request.getUnitOfMeasure() != null) {
            existingItem.setUnitOfMeasure(normalizeUnitOfMeasure(request.getUnitOfMeasure()));
        }
        // Allow clearing or setting optional last known cost price
        existingItem.setCostPrice(request.getCostPrice());

        // Update category if provided
        if (request.getCategoryId() != null && !request.getCategoryId().isEmpty()) {
            CategoryEntity category = categoryRepository.findByCategoryId(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found: " + request.getCategoryId()));
            existingItem.setCategory(category);
        }

        // Images disabled — ignore any uploaded file
        existingItem.setImgUrl(null);

        ItemEntity updatedItem = itemRepository.save(existingItem);
        return convertToResponse(updatedItem);
    }

    @Override
    public ItemResponse getItemById(String itemId) {
        System.out.println("=== ItemServiceImpl.getItemById called ===");
        System.out.println("Searching for itemId: " + itemId);
        
        Optional<ItemEntity> itemOpt = itemRepository.findByItemId(itemId);
        System.out.println("Item found: " + itemOpt.isPresent());
        
        if (itemOpt.isEmpty()) {
            System.err.println("Item not found with itemId: " + itemId);
            // Try to find by numeric ID as fallback
            try {
                Long numericId = Long.parseLong(itemId);
                System.out.println("Trying to find by numeric ID: " + numericId);
                Optional<ItemEntity> itemByNumericId = itemRepository.findById(numericId);
                if (itemByNumericId.isPresent()) {
                    System.out.println("Found item by numeric ID: " + itemByNumericId.get().getName());
                    return convertToResponse(itemByNumericId.get());
                }
            } catch (NumberFormatException e) {
                System.err.println("ItemId is not a valid UUID or numeric ID: " + itemId);
            }
            throw new RuntimeException("Item not found: " + itemId);
        }
        
        ItemEntity item = itemOpt.get();
        System.out.println("Found item: " + item.getName() + " with itemId: " + item.getItemId());
        return convertToResponse(item);
    }

    @Override
    @Transactional
    public void deleteItem(String itemId) {
        ItemEntity existingItem = itemRepository.findByItemId(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + itemId));

        // Remove dependent records before deleting the item
        inventoryTransactionRepository.deleteByItemId(itemId);
        inventoryAlertRepository.deleteByItemId(itemId);
        inventoryAdjustmentRepository.deleteByItemId(itemId);
        stockMovementRepository.deleteByItemId(itemId);
        promotionRepository.deleteByItem_Id(existingItem.getId());

        try {
            itemRepository.delete(existingItem);
            itemRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не може да се изтрие продуктът, защото се ползва в други записи (поръчки, доставки и т.н.).",
                    ex);
        }
    }
}
