package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.*;
import in.bushansirgur.billingsoftware.io.InventoryRequest;
import in.bushansirgur.billingsoftware.io.InventoryResponse;
import in.bushansirgur.billingsoftware.io.InventorySummaryResponse;
import in.bushansirgur.billingsoftware.repository.*;
import in.bushansirgur.billingsoftware.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {
    
    private final ItemRepository itemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryAlertRepository alertRepository;
    
    @Override
    @Transactional
    public InventoryResponse updateStock(InventoryRequest request) {
        ItemEntity item = getItemById(request.getItemId());
        Integer previousQuantity = item.getStockQuantity();
        Integer newQuantity = request.getNewQuantity();
        
        // Create transaction record
        InventoryTransactionEntity transaction = createTransaction(
            item.getItemId(),
            InventoryTransactionEntity.TransactionType.ADJUSTMENT,
            newQuantity - previousQuantity,
            previousQuantity,
            newQuantity,
            request.getUnitPrice(),
            request.getNotes(),
            request.getCreatedBy(),
            request.getReferenceNumber(),
            request.getReferenceType()
        );
        
        // Update item stock
        item.setStockQuantity(newQuantity);
        item.setLastStockCheck(Timestamp.valueOf(LocalDateTime.now()));
        itemRepository.save(item);
        
        // Check for alerts
        checkAndCreateAlerts(item.getItemId());
        
        return buildInventoryResponse(item, transaction, previousQuantity, newQuantity);
    }
    
    @Override
    @Transactional
    public InventoryResponse addStock(InventoryRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than 0");
        }
        if (request.getUnitPrice() == null || request.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery unit price must be greater than 0");
        }

        ItemEntity item = getItemById(request.getItemId());
        Integer previousQuantity = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
        Integer newQuantity = previousQuantity + request.getQuantity();
        
        // Create transaction record
        InventoryTransactionEntity transaction = createTransaction(
            item.getItemId(),
            InventoryTransactionEntity.TransactionType.PURCHASE,
            request.getQuantity(),
            previousQuantity,
            newQuantity,
            request.getUnitPrice(),
            request.getNotes(),
            request.getCreatedBy(),
            request.getReferenceNumber(),
            request.getReferenceType()
        );
        
        // Update item stock + last known delivery cost
        item.setStockQuantity(newQuantity);
        item.setCostPrice(request.getUnitPrice());
        item.setLastRestockDate(Timestamp.valueOf(LocalDateTime.now()));
        itemRepository.save(item);
        
        // Check for alerts
        checkAndCreateAlerts(item.getItemId());
        
        return buildInventoryResponse(item, transaction, previousQuantity, newQuantity);
    }
    
    @Override
    @Transactional
    public InventoryResponse removeStock(InventoryRequest request) {
        ItemEntity item = getItemById(request.getItemId());
        Integer previousQuantity = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than 0");
        }
        // Allow sale below recorded stock (delivery not entered yet, shelf has goods).
        Integer newQuantity = previousQuantity - request.getQuantity();
        
        // Create transaction record
        InventoryTransactionEntity transaction = createTransaction(
            item.getItemId(),
            InventoryTransactionEntity.TransactionType.SALE,
            -request.getQuantity(),
            previousQuantity,
            newQuantity,
            request.getUnitPrice(),
            request.getNotes(),
            request.getCreatedBy(),
            request.getReferenceNumber(),
            request.getReferenceType()
        );
        
        // Update item stock
        item.setStockQuantity(newQuantity);
        item.setLastStockCheck(Timestamp.valueOf(LocalDateTime.now()));
        itemRepository.save(item);
        
        // Check for alerts
        checkAndCreateAlerts(item.getItemId());
        
        return buildInventoryResponse(item, transaction, previousQuantity, newQuantity);
    }
    
    @Override
    @Transactional
    public InventoryResponse adjustStock(InventoryRequest request) {
        ItemEntity item = getItemById(request.getItemId());
        
        // Create adjustment record
        InventoryAdjustmentEntity adjustment = InventoryAdjustmentEntity.builder()
            .itemId(item.getItemId())
            .adjustmentType(InventoryAdjustmentEntity.AdjustmentType.valueOf(request.getAdjustmentType()))
            .quantity(request.getQuantity())
            .reason(request.getReason())
            .notes(request.getNotes())
            .createdBy(request.getCreatedBy())
            .build();
        
        adjustmentRepository.save(adjustment);
        
        // Update stock based on adjustment type
        Integer previousQuantity = item.getStockQuantity();
        Integer newQuantity = previousQuantity + request.getQuantity();
        
        // Create transaction record
        InventoryTransactionEntity transaction = createTransaction(
            item.getItemId(),
            InventoryTransactionEntity.TransactionType.ADJUSTMENT,
            request.getQuantity(),
            previousQuantity,
            newQuantity,
            request.getUnitPrice(),
            request.getNotes(),
            request.getCreatedBy(),
            adjustment.getAdjustmentId(),
            "ADJUSTMENT"
        );
        
        // Update item stock
        item.setStockQuantity(newQuantity);
        item.setLastStockCheck(Timestamp.valueOf(LocalDateTime.now()));
        itemRepository.save(item);
        
        // Check for alerts
        checkAndCreateAlerts(item.getItemId());
        
        return buildInventoryResponse(item, transaction, previousQuantity, newQuantity);
    }
    
    @Override
    public InventoryResponse getItemStock(String itemId) {
        ItemEntity item = getItemById(itemId);
        return buildInventoryResponse(item, null, item.getStockQuantity(), item.getStockQuantity());
    }
    
    @Override
    public List<InventoryResponse> getLowStockItems() {
        return itemRepository.findLowStockItems().stream()
            .map(item -> buildInventoryResponse(item, null, item.getStockQuantity(), item.getStockQuantity()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<InventoryResponse> getOutOfStockItems() {
        return itemRepository.findOutOfStockItems().stream()
            .map(item -> buildInventoryResponse(item, null, item.getStockQuantity(), item.getStockQuantity()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<InventoryResponse> getOverstockItems() {
        return itemRepository.findOverstockItems().stream()
            .map(item -> buildInventoryResponse(item, null, item.getStockQuantity(), item.getStockQuantity()))
            .collect(Collectors.toList());
    }
    
    @Override
    public InventorySummaryResponse getInventorySummary() {
        Long totalItems = itemRepository.count();
        Long lowStockItems = itemRepository.countLowStockItems();
        Long outOfStockItems = itemRepository.countOutOfStockItems();
        Long activeAlerts = alertRepository.countActiveAlerts();
        
        Double totalValue = itemRepository.getTotalInventoryValue();
        BigDecimal totalInventoryValue = totalValue != null ? BigDecimal.valueOf(totalValue) : BigDecimal.ZERO;
        
        return InventorySummaryResponse.builder()
            .totalItems(totalItems)
            .lowStockItems(lowStockItems)
            .outOfStockItems(outOfStockItems)
            .overstockItems((long) itemRepository.findOverstockItems().size())
            .totalInventoryValue(totalInventoryValue)
            .totalCostValue(totalInventoryValue) // Simplified for now
            .activeAlerts(activeAlerts)
            .itemsLowStock(getLowStockItems())
            .itemsOutOfStock(getOutOfStockItems())
            .itemsOverstock(getOverstockItems())
            .recentTransactions(getRecentTransactions())
            .build();
    }
    
    @Override
    public List<InventoryResponse> getItemTransactionHistory(String itemId) {
        return transactionRepository.findByItemIdOrderByCreatedAtDesc(itemId).stream()
            .map(this::buildInventoryResponseFromTransaction)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<InventoryResponse> getRecentTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)).stream()
            .map(this::buildInventoryResponseFromTransaction)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    @Override
    public void checkAndCreateAlerts(String itemId) {
        ItemEntity item = getItemById(itemId);
        
        // Check for low stock
        if (item.getStockQuantity() <= item.getReorderPoint() && item.getStockQuantity() > 0) {
            createAlert(item, InventoryAlertEntity.AlertType.LOW_STOCK, 
                "Low stock alert: " + item.getName() + " has " + item.getStockQuantity() + " units remaining");
        }
        
        // Check for out of stock
        if (item.getStockQuantity() <= 0) {
            createAlert(item, InventoryAlertEntity.AlertType.OUT_OF_STOCK, 
                "Out of stock: " + item.getName() + " is completely out of stock");
        }
        
        // Check for overstock
        if (item.getStockQuantity() > item.getMaxStockLevel()) {
            createAlert(item, InventoryAlertEntity.AlertType.OVERSTOCK, 
                "Overstock alert: " + item.getName() + " has " + item.getStockQuantity() + " units (max: " + item.getMaxStockLevel() + ")");
        }
    }
    
    @Override
    @Transactional
    public List<InventoryResponse> getActiveAlerts() {
        List<InventoryResponse> results = new java.util.ArrayList<>();
        for (InventoryAlertEntity alert : alertRepository.findByIsResolvedFalse()) {
            try {
                InventoryResponse response = buildInventoryResponseFromAlert(alert);
                if (response != null) {
                    results.add(response);
                } else {
                    // Orphan alert for deleted item — resolve so it stops breaking the warehouse page
                    alert.setIsResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    alert.setResolvedBy("SYSTEM");
                    alertRepository.save(alert);
                }
            } catch (Exception ex) {
                log.warn("Skipping broken inventory alert {} for item {}: {}",
                        alert.getId(), alert.getItemId(), ex.getMessage());
                try {
                    alert.setIsResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    alert.setResolvedBy("SYSTEM");
                    alertRepository.save(alert);
                } catch (Exception saveEx) {
                    log.warn("Could not auto-resolve orphan alert {}: {}", alert.getId(), saveEx.getMessage());
                }
            }
        }
        return results;
    }
    
    @Override
    @Transactional
    public void processSaleTransaction(String itemId, Integer quantity, String orderId) {
        InventoryRequest request = InventoryRequest.builder()
            .itemId(itemId)
            .quantity(quantity)
            .referenceNumber(orderId)
            .referenceType("SALE")
            .notes("Automatic sale transaction")
            .createdBy("SYSTEM")
            .build();
        
        removeStock(request);
    }
    
    @Override
    @Transactional
    public void processPurchaseTransaction(String itemId, Integer quantity, String purchaseOrderId) {
        InventoryRequest request = InventoryRequest.builder()
            .itemId(itemId)
            .quantity(quantity)
            .referenceNumber(purchaseOrderId)
            .referenceType("PURCHASE")
            .notes("Automatic purchase transaction")
            .createdBy("SYSTEM")
            .build();
        
        addStock(request);
    }

    @Override
    @Transactional
    public boolean processReturnTransaction(String itemId, String barcode, Integer quantity, String refundReference) {
        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than 0");
        }
        Optional<ItemEntity> itemOpt = Optional.empty();
        if (itemId != null && !itemId.isBlank()) {
            itemOpt = itemRepository.findByItemId(itemId);
        }
        if (itemOpt.isEmpty() && barcode != null && !barcode.isBlank()) {
            itemOpt = itemRepository.findByBarcode(barcode);
        }
        if (itemOpt.isEmpty()) {
            log.warn("Skip return restock — item not in catalog (id={}, barcode={}, ref={})",
                    itemId, barcode, refundReference);
            return false;
        }
        ItemEntity item = itemOpt.get();
        Integer previousQuantity = item.getStockQuantity() != null ? item.getStockQuantity() : 0;
        Integer newQuantity = previousQuantity + quantity;

        createTransaction(
            item.getItemId(),
            InventoryTransactionEntity.TransactionType.RETURN,
            quantity,
            previousQuantity,
            newQuantity,
            item.getCostPrice(),
            "Return / refund restock",
            "SYSTEM",
            refundReference,
            "RETURN"
        );

        item.setStockQuantity(newQuantity);
        itemRepository.save(item);
        checkAndCreateAlerts(item.getItemId());
        return true;
    }
    
    // Helper methods
    private ItemEntity getItemById(String itemId) {
        return itemRepository.findByItemId(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + itemId));
    }
    
    private InventoryTransactionEntity createTransaction(String itemId, 
                                                       InventoryTransactionEntity.TransactionType type,
                                                       Integer quantity,
                                                       Integer previousQuantity,
                                                       Integer newQuantity,
                                                       BigDecimal unitPrice,
                                                       String notes,
                                                       String createdBy,
                                                       String referenceNumber,
                                                       String referenceType) {
        
        InventoryTransactionEntity transaction = InventoryTransactionEntity.builder()
            .itemId(itemId)
            .transactionType(type)
            .quantity(quantity)
            .previousQuantity(previousQuantity)
            .newQuantity(newQuantity)
            .unitPrice(unitPrice)
            .totalValue(unitPrice != null ? unitPrice.multiply(BigDecimal.valueOf(quantity)) : null)
            .referenceNumber(referenceNumber)
            .referenceType(referenceType)
            .notes(notes)
            .createdBy(createdBy)
            .build();
        
        return transactionRepository.save(transaction);
    }
    
    private void createAlert(ItemEntity item, InventoryAlertEntity.AlertType alertType, String message) {
        // Check if alert already exists
        List<InventoryAlertEntity> existingAlerts = alertRepository.findActiveAlertsByItemId(item.getItemId());
        boolean alertExists = existingAlerts.stream()
            .anyMatch(alert -> alert.getAlertType() == alertType);
        
        if (!alertExists) {
            InventoryAlertEntity alert = InventoryAlertEntity.builder()
                .itemId(item.getItemId())
                .alertType(alertType)
                .alertMessage(message)
                .currentQuantity(item.getStockQuantity())
                .thresholdQuantity(alertType == InventoryAlertEntity.AlertType.LOW_STOCK ? 
                    item.getReorderPoint() : 
                    alertType == InventoryAlertEntity.AlertType.OVERSTOCK ? 
                    item.getMaxStockLevel() : 0)
                .isResolved(false)
                .build();
            
            alertRepository.save(alert);
        }
    }
    
    private InventoryResponse buildInventoryResponse(ItemEntity item, 
                                                   InventoryTransactionEntity transaction,
                                                   Integer previousQuantity,
                                                   Integer newQuantity) {
        
        String stockStatus = determineStockStatus(item);
        Boolean needsReorder = item.getStockQuantity() <= item.getReorderPoint();
        Integer reorderQuantity = needsReorder ? 
            item.getMaxStockLevel() - item.getStockQuantity() : 0;
        
        return InventoryResponse.builder()
            .itemId(item.getItemId())
            .itemName(item.getName())
            .barcode(item.getBarcode())
            .currentStock(item.getStockQuantity())
            .previousStock(previousQuantity)
            .newStock(newQuantity)
            .unitPrice(transaction != null ? transaction.getUnitPrice() : null)
            .totalValue(transaction != null ? transaction.getTotalValue() : null)
            .transactionType(transaction != null ? transaction.getTransactionType().name() : null)
            .transactionId(transaction != null ? transaction.getTransactionId() : null)
            .referenceNumber(transaction != null ? transaction.getReferenceNumber() : null)
            .referenceType(transaction != null ? transaction.getReferenceType() : null)
            .notes(transaction != null ? transaction.getNotes() : null)
            .createdBy(transaction != null ? transaction.getCreatedBy() : null)
            .createdAt(transaction != null ? transaction.getCreatedAt() : null)
            .minStockLevel(item.getMinStockLevel())
            .maxStockLevel(item.getMaxStockLevel())
            .reorderPoint(item.getReorderPoint())
            .unitOfMeasure(item.getUnitOfMeasure())
            .supplierName(item.getSupplierName())
            .supplierCode(item.getSupplierCode())
            .costPrice(item.getCostPrice())
            .lastRestockDate(item.getLastRestockDate() != null ? 
                item.getLastRestockDate().toLocalDateTime() : null)
            .lastStockCheck(item.getLastStockCheck() != null ? 
                item.getLastStockCheck().toLocalDateTime() : null)
            .stockStatus(stockStatus)
            .needsReorder(needsReorder)
            .reorderQuantity(reorderQuantity)
            .build();
    }
    
    private InventoryResponse buildInventoryResponseFromTransaction(InventoryTransactionEntity transaction) {
        Optional<ItemEntity> itemOpt = itemRepository.findByItemId(transaction.getItemId());
        if (itemOpt.isEmpty()) {
            log.warn("Skipping inventory transaction {} — item not found: {}",
                    transaction.getTransactionId(), transaction.getItemId());
            return null;
        }
        ItemEntity item = itemOpt.get();
        return buildInventoryResponse(item, transaction,
            transaction.getPreviousQuantity(), transaction.getNewQuantity());
    }
    
    private InventoryResponse buildInventoryResponseFromAlert(InventoryAlertEntity alert) {
        Optional<ItemEntity> itemOpt = itemRepository.findByItemId(alert.getItemId());
        if (itemOpt.isEmpty()) {
            log.warn("Skipping inventory alert {} — item not found: {}",
                    alert.getId(), alert.getItemId());
            return null;
        }
        ItemEntity item = itemOpt.get();
        return buildInventoryResponse(item, null, item.getStockQuantity(), item.getStockQuantity());
    }
    
    private String determineStockStatus(ItemEntity item) {
        if (item.getStockQuantity() <= 0) {
            return "OUT_OF_STOCK";
        } else if (item.getStockQuantity() <= item.getReorderPoint()) {
            return "LOW_STOCK";
        } else if (item.getStockQuantity() > item.getMaxStockLevel()) {
            return "OVERSTOCK";
        } else {
            return "NORMAL";
        }
    }
}
