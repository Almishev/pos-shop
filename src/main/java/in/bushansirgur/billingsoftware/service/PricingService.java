package in.bushansirgur.billingsoftware.service;

import java.math.BigDecimal;

public interface PricingService {
    BigDecimal getEffectivePrice(Long itemDbId);
}


