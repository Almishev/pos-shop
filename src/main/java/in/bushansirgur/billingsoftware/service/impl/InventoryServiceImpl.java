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
        ItemEntity item = getItemById(request.getItemId());
        Integer previousQuantity = item.getStockQuantity();
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
        
        // Update item stock
        item.setStockQuantity(newQuantity);
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
        Integer previousQuantity = item.getStockQuantity();
        Integer newQuantity = previousQuantity - request.getQuantity();
        
        // Allow negative stock for tracking purposes
        if (newQuantity < 0) {
            log.warn("Negative stock detected for item {}: {} -> {}", 
                    item.getName(), previousQuantity, newQuantity);
        }
        
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
            .collect(Collectors.toList());
    }
    
    @Override
    public List<InventoryResponse> getRecentTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)).stream()
            .map(this::buildInventoryResponseFromTransaction)
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
    public List<InventoryResponse> getActiveAlerts() {
        return alertRepository.findByIsResolvedFalse().stream()
            .map(this::buildInventoryResponseFromAlert)
            .collect(Collectors.toList());
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
        ItemEntity item = getItemById(transaction.getItemId());
        return buildInventoryResponse(item, transaction, 
            transaction.getPreviousQuantity(), transaction.getNewQuantity());
    }
    
    private InventoryResponse buildInventoryResponseFromAlert(InventoryAlertEntity alert) {
        ItemEntity item = getItemById(alert.getItemId());
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
