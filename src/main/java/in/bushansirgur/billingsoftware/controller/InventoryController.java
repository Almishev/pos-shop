package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.InventoryRequest;
import in.bushansirgur.billingsoftware.io.InventoryResponse;
import in.bushansirgur.billingsoftware.io.InventorySummaryResponse;
import in.bushansirgur.billingsoftware.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // Stock operations
    @PostMapping("/stock/add")
    public ResponseEntity<InventoryResponse> addStock(@RequestBody InventoryRequest request) {
        InventoryResponse response = inventoryService.addStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stock/remove")
    public ResponseEntity<InventoryResponse> removeStock(@RequestBody InventoryRequest request) {
        InventoryResponse response = inventoryService.removeStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stock/adjust")
    public ResponseEntity<InventoryResponse> adjustStock(@RequestBody InventoryRequest request) {
        InventoryResponse response = inventoryService.adjustStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stock/update")
    public ResponseEntity<InventoryResponse> updateStock(@RequestBody InventoryRequest request) {
        InventoryResponse response = inventoryService.updateStock(request);
        return ResponseEntity.ok(response);
    }

    // Inventory queries
    @GetMapping("/stock/{itemId}")
    public ResponseEntity<InventoryResponse> getItemStock(@PathVariable String itemId) {
        InventoryResponse response = inventoryService.getItemStock(itemId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stock/low")
    public ResponseEntity<List<InventoryResponse>> getLowStockItems() {
        List<InventoryResponse> response = inventoryService.getLowStockItems();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stock/out-of-stock")
    public ResponseEntity<List<InventoryResponse>> getOutOfStockItems() {
        List<InventoryResponse> response = inventoryService.getOutOfStockItems();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stock/overstock")
    public ResponseEntity<List<InventoryResponse>> getOverstockItems() {
        List<InventoryResponse> response = inventoryService.getOverstockItems();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<InventorySummaryResponse> getInventorySummary() {
        InventorySummaryResponse response = inventoryService.getInventorySummary();
        return ResponseEntity.ok(response);
    }

    // Transaction history
    @GetMapping("/transactions/{itemId}")
    public ResponseEntity<List<InventoryResponse>> getItemTransactionHistory(@PathVariable String itemId) {
        List<InventoryResponse> response = inventoryService.getItemTransactionHistory(itemId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/recent")
    public ResponseEntity<List<InventoryResponse>> getRecentTransactions() {
        List<InventoryResponse> response = inventoryService.getRecentTransactions();
        return ResponseEntity.ok(response);
    }

    // Alerts
    @GetMapping("/alerts")
    public ResponseEntity<List<InventoryResponse>> getActiveAlerts() {
        List<InventoryResponse> response = inventoryService.getActiveAlerts();
        return ResponseEntity.ok(response);
    }

    // Automatic operations
    @PostMapping("/auto/sale")
    public ResponseEntity<Void> processSaleTransaction(
            @RequestParam String itemId,
            @RequestParam int quantity,
            @RequestParam String orderId) {
        inventoryService.processSaleTransaction(itemId, quantity, orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/auto/purchase")
    public ResponseEntity<Void> processPurchaseTransaction(
            @RequestParam String itemId,
            @RequestParam int quantity,
            @RequestParam String purchaseOrderId) {
        inventoryService.processPurchaseTransaction(itemId, quantity, purchaseOrderId);
        return ResponseEntity.ok().build();
    }
}
