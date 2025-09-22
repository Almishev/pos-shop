package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.InventoryAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryAlertRepository extends JpaRepository<InventoryAlertEntity, Long> {
    
    Optional<InventoryAlertEntity> findByAlertId(String alertId);
    
    List<InventoryAlertEntity> findByItemIdOrderByCreatedAtDesc(String itemId);
    
    List<InventoryAlertEntity> findByAlertType(InventoryAlertEntity.AlertType alertType);
    
    List<InventoryAlertEntity> findByIsResolved(Boolean isResolved);
    
    List<InventoryAlertEntity> findByIsResolvedFalse();
    
    List<InventoryAlertEntity> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT a FROM InventoryAlertEntity a WHERE a.itemId = :itemId AND a.isResolved = false")
    List<InventoryAlertEntity> findActiveAlertsByItemId(@Param("itemId") String itemId);
    
    @Query("SELECT COUNT(a) FROM InventoryAlertEntity a WHERE a.isResolved = false")
    Long countActiveAlerts();
    
    @Query("SELECT COUNT(a) FROM InventoryAlertEntity a WHERE a.alertType = :alertType AND a.isResolved = false")
    Long countActiveAlertsByType(@Param("alertType") InventoryAlertEntity.AlertType alertType);
}
