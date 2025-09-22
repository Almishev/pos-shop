package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.InventoryAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustmentEntity, Long> {
    
    Optional<InventoryAdjustmentEntity> findByAdjustmentId(String adjustmentId);
    
    List<InventoryAdjustmentEntity> findByItemIdOrderByCreatedAtDesc(String itemId);
    
    List<InventoryAdjustmentEntity> findByAdjustmentType(InventoryAdjustmentEntity.AdjustmentType adjustmentType);
    
    List<InventoryAdjustmentEntity> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT a FROM InventoryAdjustmentEntity a WHERE a.itemId = :itemId AND a.createdAt BETWEEN :startDate AND :endDate")
    List<InventoryAdjustmentEntity> findByItemIdAndDateRange(@Param("itemId") String itemId, 
                                                             @Param("startDate") LocalDateTime startDate, 
                                                             @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(a.quantity) FROM InventoryAdjustmentEntity a WHERE a.itemId = :itemId AND a.adjustmentType = :adjustmentType")
    Integer sumQuantityByItemIdAndType(@Param("itemId") String itemId, 
                                      @Param("adjustmentType") InventoryAdjustmentEntity.AdjustmentType adjustmentType);
}
