package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalDeviceRepository extends JpaRepository<FiscalDeviceEntity, Long> {
    
    Optional<FiscalDeviceEntity> findBySerialNumber(String serialNumber);
    
    List<FiscalDeviceEntity> findByStatus(FiscalDeviceEntity.DeviceStatus status);
    
    List<FiscalDeviceEntity> findByManufacturer(String manufacturer);
    
    List<FiscalDeviceEntity> findByLocation(String location);
    
    Optional<FiscalDeviceEntity> findByFiscalMemoryNumber(String fiscalMemoryNumber);
}
