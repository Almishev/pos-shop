package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.FiscalReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalReportRepository extends JpaRepository<FiscalReportEntity, Long> {
    
    Optional<FiscalReportEntity> findByReportNumber(String reportNumber);
    
    List<FiscalReportEntity> findByReportType(FiscalReportEntity.ReportType reportType);
    
    List<FiscalReportEntity> findByReportDate(LocalDate reportDate);
    
    List<FiscalReportEntity> findByDeviceSerialNumber(String deviceSerialNumber);
    
    List<FiscalReportEntity> findByStatus(FiscalReportEntity.ReportStatus status);
    
    @Query("SELECT f FROM FiscalReportEntity f WHERE f.reportDate BETWEEN :startDate AND :endDate")
    List<FiscalReportEntity> findByDateRange(@Param("startDate") LocalDate startDate, 
                                            @Param("endDate") LocalDate endDate);
    
    @Query("SELECT f FROM FiscalReportEntity f WHERE f.reportType = :reportType AND f.reportDate BETWEEN :startDate AND :endDate")
    List<FiscalReportEntity> findByReportTypeAndDateRange(@Param("reportType") FiscalReportEntity.ReportType reportType,
                                                         @Param("startDate") LocalDate startDate, 
                                                         @Param("endDate") LocalDate endDate);
    
    @Query("SELECT f FROM FiscalReportEntity f WHERE f.deviceSerialNumber = :deviceSerialNumber AND f.reportDate = :reportDate")
    List<FiscalReportEntity> findByDeviceAndDate(@Param("deviceSerialNumber") String deviceSerialNumber,
                                                @Param("reportDate") LocalDate reportDate);
}
