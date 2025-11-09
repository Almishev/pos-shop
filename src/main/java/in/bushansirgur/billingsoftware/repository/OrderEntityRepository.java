package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.OrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrderEntityRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderId(String orderId);

    List<OrderEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT SUM(o.grandTotal) FROM OrderEntity o WHERE DATE(o.createdAt) = :date")
    Double sumSalesByDate(@Param("date") LocalDate date);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE DATE(o.createdAt) = :date")
    Long countByOrderDate(@Param("date") LocalDate date);

    @Query("SELECT o FROM OrderEntity o ORDER BY o.createdAt DESC")
    List<OrderEntity> findRecentOrders(Pageable pageable);

    List<OrderEntity> findAllByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime from, LocalDateTime to);
    
    List<OrderEntity> findAllByCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime cutoff);

    @Query("SELECT o FROM OrderEntity o WHERE (:q IS NULL OR LOWER(o.orderId) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(o.phoneNumber) LIKE LOWER(CONCAT('%', :q, '%'))) AND (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to)")
    Page<OrderEntity> searchOrders(@Param("q") String q,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   Pageable pageable);

    @Query("SELECT o.cashierUsername as cashier, COUNT(o) as cnt, COALESCE(SUM(o.grandTotal),0) as total FROM OrderEntity o WHERE o.createdAt >= :from AND o.createdAt <= :to GROUP BY o.cashierUsername")
    List<Object[]> summarizeByCashier(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    
    @Query("SELECT o.cashierUsername as cashier, COUNT(o) as cnt, COALESCE(SUM(o.grandTotal),0) as total FROM OrderEntity o WHERE DATE(o.createdAt) = :date GROUP BY o.cashierUsername ORDER BY total DESC")
    List<Object[]> summarizeByCashierForDate(@Param("date") LocalDate date);

    // Shift-specific aggregates for a given cashier within a session window
    @Query("SELECT COALESCE(SUM(o.grandTotal),0) FROM OrderEntity o WHERE LOWER(TRIM(o.cashierUsername)) = LOWER(TRIM(:cashier)) AND o.createdAt >= :from AND o.createdAt <= :to")
    Double sumSalesByCashierBetween(@Param("cashier") String cashier,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE LOWER(TRIM(o.cashierUsername)) = LOWER(TRIM(:cashier)) AND o.createdAt >= :from AND o.createdAt <= :to")
    Long countByCashierBetween(@Param("cashier") String cashier,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    // Breakdown by payment method within session window for given cashier
    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE LOWER(TRIM(o.cashierUsername)) = LOWER(TRIM(:cashier)) AND o.paymentMethod = :method AND o.createdAt >= :from AND o.createdAt <= :to")
    Long countByCashierBetweenAndMethod(@Param("cashier") String cashier,
                                        @Param("method") in.bushansirgur.billingsoftware.io.PaymentMethod method,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(o.grandTotal),0) FROM OrderEntity o WHERE LOWER(TRIM(o.cashierUsername)) = LOWER(TRIM(:cashier)) AND o.paymentMethod = :method AND o.createdAt >= :from AND o.createdAt <= :to")
    Double sumGrandByCashierBetweenAndMethod(@Param("cashier") String cashier,
                                             @Param("method") in.bushansirgur.billingsoftware.io.PaymentMethod method,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    // For SPLIT, also sum the split components
    @Query("SELECT COALESCE(SUM(o.paymentDetails.cashAmount),0) FROM OrderEntity o WHERE LOWER(TRIM(o.cashierUsername)) = LOWER(TRIM(:cashier)) AND o.paymentMethod = in.bushansirgur.billingsoftware.io.PaymentMethod.SPLIT AND o.createdAt >= :from AND o.createdAt <= :to")
    Double sumSplitCashByCashierBetween(@Param("cashier") String cashier,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(o.paymentDetails.cardAmount),0) FROM OrderEntity o WHERE LOWER(TRIM(o.cashierUsername)) = LOWER(TRIM(:cashier)) AND o.paymentMethod = in.bushansirgur.billingsoftware.io.PaymentMethod.SPLIT AND o.createdAt >= :from AND o.createdAt <= :to")
    Double sumSplitCardByCashierBetween(@Param("cashier") String cashier,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    // Methods for store daily reports - calculate sales between dates
    @Query("SELECT COALESCE(SUM(o.grandTotal),0) FROM OrderEntity o WHERE o.createdAt >= :from AND o.createdAt <= :to")
    Double sumSalesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.createdAt >= :from AND o.createdAt <= :to")
    Long countOrdersBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Payment breakdown for store daily reports (all cashiers)
    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.paymentMethod = :method AND o.createdAt >= :from AND o.createdAt <= :to")
    Long countByPaymentMethodBetween(@Param("method") in.bushansirgur.billingsoftware.io.PaymentMethod method,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(o.grandTotal),0) FROM OrderEntity o WHERE o.paymentMethod = :method AND o.createdAt >= :from AND o.createdAt <= :to")
    Double sumByPaymentMethodBetween(@Param("method") in.bushansirgur.billingsoftware.io.PaymentMethod method,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(o.paymentDetails.cashAmount),0) FROM OrderEntity o WHERE o.paymentMethod = in.bushansirgur.billingsoftware.io.PaymentMethod.SPLIT AND o.createdAt >= :from AND o.createdAt <= :to")
    Double sumSplitCashBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(o.paymentDetails.cardAmount),0) FROM OrderEntity o WHERE o.paymentMethod = in.bushansirgur.billingsoftware.io.PaymentMethod.SPLIT AND o.createdAt >= :from AND o.createdAt <= :to")
    Double sumSplitCardBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}
