package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.FiscalReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalReceiptRepository extends JpaRepository<FiscalReceiptEntity, Long> {
    
    Optional<FiscalReceiptEntity> findByFiscalNumber(String fiscalNumber);
    
    Optional<FiscalReceiptEntity> findByOrderId(String orderId);
    
    List<FiscalReceiptEntity> findByDeviceSerialNumber(String deviceSerialNumber);
    
    List<FiscalReceiptEntity> findByStatus(FiscalReceiptEntity.FiscalStatus status);
    
    @Query("SELECT f FROM FiscalReceiptEntity f WHERE f.fiscalDateTime BETWEEN :startDate AND :endDate")
    List<FiscalReceiptEntity> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                             @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(f) FROM FiscalReceiptEntity f WHERE f.fiscalDateTime BETWEEN :startDate AND :endDate")
    Long countByDateRange(@Param("startDate") LocalDateTime startDate, 
                         @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(f.grandTotal) FROM FiscalReceiptEntity f WHERE f.fiscalDateTime BETWEEN :startDate AND :endDate")
    Double sumGrandTotalByDateRange(@Param("startDate") LocalDateTime startDate, 
                                   @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(f.vatAmount) FROM FiscalReceiptEntity f WHERE f.fiscalDateTime BETWEEN :startDate AND :endDate")
    Double sumVatAmountByDateRange(@Param("startDate") LocalDateTime startDate, 
                                  @Param("endDate") LocalDateTime endDate);
}
