package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.InventoryRequest;
import in.bushansirgur.billingsoftware.io.InventoryResponse;
import in.bushansirgur.billingsoftware.io.InventorySummaryResponse;

import java.util.List;

public interface InventoryService {
    
    // Stock operations
    InventoryResponse updateStock(InventoryRequest request);
    InventoryResponse addStock(InventoryRequest request);
    InventoryResponse removeStock(InventoryRequest request);
    InventoryResponse adjustStock(InventoryRequest request);
    
    // Inventory queries
    InventoryResponse getItemStock(String itemId);
    List<InventoryResponse> getLowStockItems();
    List<InventoryResponse> getOutOfStockItems();
    List<InventoryResponse> getOverstockItems();
    InventorySummaryResponse getInventorySummary();
    
    // Transaction history
    List<InventoryResponse> getItemTransactionHistory(String itemId);
    List<InventoryResponse> getRecentTransactions();
    
    // Alerts
    void checkAndCreateAlerts(String itemId);
    List<InventoryResponse> getActiveAlerts();
    
    // Automatic operations
    void processSaleTransaction(String itemId, Integer quantity, String orderId);
    void processPurchaseTransaction(String itemId, Integer quantity, String purchaseOrderId);
}
