package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.LoyaltyTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransactionEntity, Long> {

    List<LoyaltyTransactionEntity> findByCustomerId(String customerId);
    
    List<LoyaltyTransactionEntity> findByOrderId(String orderId);
    
    List<LoyaltyTransactionEntity> findByTransactionType(LoyaltyTransactionEntity.TransactionType transactionType);
    
    @Query("SELECT lt FROM LoyaltyTransactionEntity lt WHERE lt.customerId = :customerId ORDER BY lt.createdAt DESC")
    List<LoyaltyTransactionEntity> findCustomerTransactionHistory(@Param("customerId") String customerId);
    
    @Query("SELECT SUM(lt.pointsEarned) FROM LoyaltyTransactionEntity lt WHERE lt.customerId = :customerId AND lt.transactionType = 'POINTS_EARNED'")
    Integer getTotalPointsEarned(@Param("customerId") String customerId);
    
    @Query("SELECT SUM(lt.pointsRedeemed) FROM LoyaltyTransactionEntity lt WHERE lt.customerId = :customerId AND lt.transactionType = 'POINTS_REDEEMED'")
    Integer getTotalPointsRedeemed(@Param("customerId") String customerId);
    
    @Query("SELECT SUM(lt.discountAmount) FROM LoyaltyTransactionEntity lt WHERE lt.customerId = :customerId AND lt.transactionType = 'DISCOUNT_APPLIED'")
    Double getTotalDiscountsApplied(@Param("customerId") String customerId);
    
    @Query("SELECT lt FROM LoyaltyTransactionEntity lt WHERE lt.createdAt >= :fromDate AND lt.createdAt <= :toDate ORDER BY lt.createdAt DESC")
    List<LoyaltyTransactionEntity> findTransactionsInDateRange(@Param("fromDate") Timestamp fromDate, 
                                                              @Param("toDate") Timestamp toDate);
}
