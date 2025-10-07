package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.entity.PromotionEntity;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.repository.PromotionRepository;
import in.bushansirgur.billingsoftware.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final PromotionRepository promotionRepository;
    private final ItemRepository itemRepository;

    @Override
    public BigDecimal getEffectivePrice(Long itemDbId) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return promotionRepository.findBestActiveForItem(itemDbId, now)
                .map(PromotionEntity::getPromoPrice)
                .orElseGet(() -> {
                    ItemEntity item = itemRepository.findById(itemDbId).orElse(null);
                    return item != null ? (item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO) : BigDecimal.ZERO;
                });
    }
}


