package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.DeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<DeliveryEntity, Long> {
    Optional<DeliveryEntity> findByDeliveryId(String deliveryId);
    List<DeliveryEntity> findAllByOrderByCreatedAtDesc();
}
