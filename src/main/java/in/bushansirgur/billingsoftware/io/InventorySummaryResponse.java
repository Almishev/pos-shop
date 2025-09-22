package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventorySummaryResponse {
    
    private Long totalItems;
    private Long lowStockItems;
    private Long outOfStockItems;
    private Long overstockItems;
    private BigDecimal totalInventoryValue;
    private BigDecimal totalCostValue;
    private Long activeAlerts;
    
    private List<InventoryResponse> recentTransactions;
    
    private List<InventoryResponse> itemsLowStock;
    
    private List<InventoryResponse> itemsOutOfStock;
    
    private List<InventoryResponse> itemsOverstock;
}
