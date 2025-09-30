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

    @Query("SELECT o FROM OrderEntity o WHERE (:q IS NULL OR LOWER(o.orderId) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(o.phoneNumber) LIKE LOWER(CONCAT('%', :q, '%'))) AND (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to)")
    Page<OrderEntity> searchOrders(@Param("q") String q,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   Pageable pageable);

    @Query("SELECT o.cashierUsername as cashier, COUNT(o) as cnt, COALESCE(SUM(o.grandTotal),0) as total FROM OrderEntity o WHERE o.createdAt >= :from AND o.createdAt <= :to GROUP BY o.cashierUsername")
    List<Object[]> summarizeByCashier(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}
