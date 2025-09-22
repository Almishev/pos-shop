package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.CategoryEntity;
import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.io.ItemRequest;
import in.bushansirgur.billingsoftware.io.ItemResponse;
import in.bushansirgur.billingsoftware.repository.CategoryRepository;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.service.FileUploadService;
import in.bushansirgur.billingsoftware.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final FileUploadService fileUploadService;

    @Override
    public ItemResponse add(ItemRequest request, MultipartFile file) throws IOException {
        // Check if barcode already exists
        if (request.getBarcode() != null && !request.getBarcode().trim().isEmpty()) {
            itemRepository.findByBarcode(request.getBarcode())
                    .ifPresent(item -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Item with barcode " + request.getBarcode() + " already exists");
                    });
        }
        
        String imgUrl = fileUploadService.uploadFile(file);
        ItemEntity newItem = convertToEntity(request);
        CategoryEntity existingCategory = categoryRepository.findByCategoryId(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found: "+request.getCategoryId()));
        newItem.setCategory(existingCategory);
        newItem.setImgUrl(imgUrl);
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
        
        return ItemResponse.builder()
                .itemId(newItem.getItemId())
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
            // Generate a unique barcode if none provided
            barcode = "BC" + System.currentTimeMillis() + String.valueOf((int)(Math.random() * 1000));
        }
        
        return ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .barcode(barcode)
                .build();
    }

    @Override
    public List<ItemResponse> fetchItems() {
        return itemRepository.findAll()
                .stream()
                .map(itemEntity -> convertToResponse(itemEntity))
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
    public void deleteItem(String itemId) {
        ItemEntity existingItem = itemRepository.findByItemId(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found: "+itemId));
        fileUploadService.deleteFile(existingItem.getImgUrl());
        itemRepository.delete(existingItem);
    }
}
