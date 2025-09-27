package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.StockMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {
    
    List<StockMovementEntity> findByItemIdOrderByCreatedAtDesc(String itemId);
    
    List<StockMovementEntity> findByMovementType(StockMovementEntity.MovementType movementType);
    
    List<StockMovementEntity> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT s FROM StockMovementEntity s WHERE s.itemId = :itemId AND s.createdAt BETWEEN :startDate AND :endDate")
    List<StockMovementEntity> findByItemIdAndDateRange(@Param("itemId") String itemId, 
                                                      @Param("startDate") LocalDateTime startDate, 
                                                      @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT s FROM StockMovementEntity s ORDER BY s.createdAt DESC")
    List<StockMovementEntity> findRecentMovements();
}
