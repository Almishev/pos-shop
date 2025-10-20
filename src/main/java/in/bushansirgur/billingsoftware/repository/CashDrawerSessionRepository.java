package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.CashDrawerSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashDrawerSessionRepository extends JpaRepository<CashDrawerSessionEntity, Long> {
    
    // Намери активна сесия за касиер на дадена дата
    @Query("SELECT c FROM CashDrawerSessionEntity c WHERE c.cashierUsername = :cashierUsername " +
           "AND c.sessionDate = :date AND c.status = 'ACTIVE'")
    Optional<CashDrawerSessionEntity> findActiveSessionByCashierAndDate(
            @Param("cashierUsername") String cashierUsername, 
            @Param("date") LocalDate date);
    
    // Намери всички сесии за касиер на дадена дата
    List<CashDrawerSessionEntity> findByCashierUsernameAndSessionDate(String cashierUsername, LocalDate date);
    
    // Намери всички активни сесии за дадена дата
    List<CashDrawerSessionEntity> findBySessionDateAndStatus(LocalDate date, CashDrawerSessionEntity.SessionStatus status);
    
    // Намери всички сесии в даден период
    List<CashDrawerSessionEntity> findBySessionDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Намери последната сесия за касиер
    @Query("SELECT c FROM CashDrawerSessionEntity c WHERE c.cashierUsername = :cashierUsername " +
           "ORDER BY c.sessionDate DESC, c.sessionStartTime DESC")
    List<CashDrawerSessionEntity> findLastSessionsByCashier(@Param("cashierUsername") String cashierUsername);

    // Проверка дали устройство е заето (има активна сесия)
    boolean existsByDeviceSerialNumberAndStatus(String deviceSerialNumber, CashDrawerSessionEntity.SessionStatus status);
    
    // Debug метод за проверка на активни сесии
    @Query("SELECT c FROM CashDrawerSessionEntity c WHERE c.deviceSerialNumber = :deviceSerialNumber AND c.status = :status")
    List<CashDrawerSessionEntity> findByDeviceSerialNumberAndStatus(@Param("deviceSerialNumber") String deviceSerialNumber, @Param("status") CashDrawerSessionEntity.SessionStatus status);
}
