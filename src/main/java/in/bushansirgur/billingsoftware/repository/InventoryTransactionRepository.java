package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.InventoryTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransactionEntity, Long> {
    
    Optional<InventoryTransactionEntity> findByTransactionId(String transactionId);
    
    List<InventoryTransactionEntity> findByItemIdOrderByCreatedAtDesc(String itemId);
    
    List<InventoryTransactionEntity> findByTransactionType(InventoryTransactionEntity.TransactionType transactionType);
    
    List<InventoryTransactionEntity> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT t FROM InventoryTransactionEntity t WHERE t.itemId = :itemId AND t.createdAt BETWEEN :startDate AND :endDate")
    List<InventoryTransactionEntity> findByItemIdAndDateRange(@Param("itemId") String itemId, 
                                                             @Param("startDate") LocalDateTime startDate, 
                                                             @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT t FROM InventoryTransactionEntity t WHERE t.transactionType = :transactionType AND t.createdAt BETWEEN :startDate AND :endDate")
    List<InventoryTransactionEntity> findByTransactionTypeAndDateRange(@Param("transactionType") InventoryTransactionEntity.TransactionType transactionType,
                                                                       @Param("startDate") LocalDateTime startDate,
                                                                       @Param("endDate") LocalDateTime endDate);
    
    Page<InventoryTransactionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    @Query("SELECT SUM(t.quantity) FROM InventoryTransactionEntity t WHERE t.itemId = :itemId AND t.transactionType = :transactionType")
    Integer sumQuantityByItemIdAndType(@Param("itemId") String itemId, 
                                      @Param("transactionType") InventoryTransactionEntity.TransactionType transactionType);
}
