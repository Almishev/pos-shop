package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PricingController {

    private final PricingService pricingService;
    private final ItemRepository itemRepository;

    @PostMapping("/effective")
    public ResponseEntity<?> getEffective(@RequestBody Map<String, Object> body) {
        Object idsObj = body.get("itemDbIds");
        if (!(idsObj instanceof List<?> idList) || idList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "itemDbIds required"));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : idList) {
            Long id = ((Number) o).longValue();
            Optional<ItemEntity> opt = itemRepository.findById(id);
            if (opt.isEmpty()) continue;
            ItemEntity item = opt.get();
            BigDecimal base = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
            BigDecimal effective = pricingService.getEffectivePrice(id);
            Map<String, Object> row = new HashMap<>();
            row.put("itemDbId", id);
            row.put("basePrice", base);
            row.put("effectivePrice", effective);
            row.put("isPromo", effective.compareTo(base) < 0);
            result.add(row);
        }
        return ResponseEntity.ok(result);
    }
}


