package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.SaleSequenceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SaleSequenceRepository extends JpaRepository<SaleSequenceEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SaleSequenceEntity s where s.individualNumber = :individualNumber")
    Optional<SaleSequenceEntity> findForUpdate(@Param("individualNumber") String individualNumber);
}
