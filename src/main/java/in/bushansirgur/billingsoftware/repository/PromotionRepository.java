package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.PromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<PromotionEntity, Long> {

    @Query("select p from PromotionEntity p where p.item.id = :itemId and p.active = true and :now between p.startAt and p.endAt order by p.promoPrice asc, p.id desc")
    List<PromotionEntity> findActiveForItem(@Param("itemId") Long itemId, @Param("now") Timestamp now);

    default Optional<PromotionEntity> findBestActiveForItem(Long itemId, Timestamp now) {
        List<PromotionEntity> list = findActiveForItem(itemId, now);
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}


