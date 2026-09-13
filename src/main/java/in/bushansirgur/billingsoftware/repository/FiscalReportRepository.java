package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.FiscalReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalReportRepository extends JpaRepository<FiscalReportEntity, Long>,
        JpaSpecificationExecutor<FiscalReportEntity> {
    
    Optional<FiscalReportEntity> findByReportNumber(String reportNumber);
    
    List<FiscalReportEntity> findByReportType(FiscalReportEntity.ReportType reportType);
    
    List<FiscalReportEntity> findByReportDate(LocalDate reportDate);
    
    List<FiscalReportEntity> findByDeviceSerialNumber(String deviceSerialNumber);
    
    List<FiscalReportEntity> findByStatus(FiscalReportEntity.ReportStatus status);

    List<FiscalReportEntity> findByReportDateBeforeOrderByReportDateAsc(LocalDate cutoff);
    
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

    @Query("""
            SELECT f FROM FiscalReportEntity f
            WHERE (:hasType = false OR f.reportType = :reportType)
              AND f.reportDate >= :fromDate AND f.reportDate <= :toDate
              AND (:hasCashierFilter = false
                   OR LOWER(TRIM(f.cashierName)) = :cashierKey1
                   OR LOWER(TRIM(f.cashierName)) = :cashierKey2)
            """)
    Page<FiscalReportEntity> searchReports(@Param("hasType") boolean hasType,
                                           @Param("reportType") FiscalReportEntity.ReportType reportType,
                                           @Param("fromDate") LocalDate fromDate,
                                           @Param("toDate") LocalDate toDate,
                                           @Param("hasCashierFilter") boolean hasCashierFilter,
                                           @Param("cashierKey1") String cashierKey1,
                                           @Param("cashierKey2") String cashierKey2,
                                           Pageable pageable);
}
