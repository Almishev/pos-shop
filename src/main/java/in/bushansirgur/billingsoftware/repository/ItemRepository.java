package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.ItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<ItemEntity, Long> {

    Optional<ItemEntity> findByItemId(String id);
    
    Optional<ItemEntity> findByBarcode(String barcode);

    Integer countByCategoryId(Long id);
    
    @Query("SELECT i FROM ItemEntity i WHERE i.name LIKE %:searchTerm% OR i.barcode LIKE %:searchTerm%")
    List<ItemEntity> findByNameContainingOrBarcodeContaining(@Param("searchTerm") String searchTerm);
    
    // Inventory Management Queries
    List<ItemEntity> findByStockQuantityLessThanEqual(Integer quantity);
    
    List<ItemEntity> findByStockQuantityGreaterThan(Integer quantity);
    
    @Query("SELECT i FROM ItemEntity i WHERE i.stockQuantity <= i.reorderPoint")
    List<ItemEntity> findLowStockItems();
    
    @Query("SELECT i FROM ItemEntity i WHERE i.stockQuantity = 0")
    List<ItemEntity> findOutOfStockItems();
    
    @Query("SELECT i FROM ItemEntity i WHERE i.stockQuantity > i.maxStockLevel")
    List<ItemEntity> findOverstockItems();
    
    @Query("SELECT COUNT(i) FROM ItemEntity i WHERE i.stockQuantity <= i.reorderPoint")
    Long countLowStockItems();
    
    @Query("SELECT COUNT(i) FROM ItemEntity i WHERE i.stockQuantity = 0")
    Long countOutOfStockItems();
    
    @Query("SELECT SUM(i.stockQuantity * COALESCE(i.costPrice, 0)) FROM ItemEntity i WHERE i.stockQuantity > 0")
    Double getTotalInventoryValue();
}
