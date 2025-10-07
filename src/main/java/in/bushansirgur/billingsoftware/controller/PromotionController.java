package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.entity.PromotionEntity;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/promotions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PromotionController {

    private final PromotionRepository promotionRepository;
    private final ItemRepository itemRepository;

    @PostMapping
    public ResponseEntity<?> createPromotion(@RequestBody Map<String, Object> body) {
        try {
            System.out.println("=== PromotionController.createPromotion called ===");
            System.out.println("Request body: " + body);
            Object idObj = body.get("itemDbId");
            Long itemDbId = null;
            if (idObj != null) {
                itemDbId = (idObj instanceof Number)
                        ? ((Number) idObj).longValue()
                        : Long.parseLong(idObj.toString());
            }

            String priceStr = String.valueOf(body.get("promoPrice"));
            String startStr = String.valueOf(body.get("startAt"));
            String endStr = String.valueOf(body.get("endAt"));
            System.out.println("Parsed fields -> itemDbId:" + itemDbId + ", promoPrice:" + priceStr + ", startAt:" + startStr + ", endAt:" + endStr);

            BigDecimal promoPrice = new BigDecimal(priceStr);
            Timestamp startAt = Timestamp.valueOf(startStr);
            Timestamp endAt = Timestamp.valueOf(endStr);

            ItemEntity item = null;
            if (itemDbId != null) {
                item = itemRepository.findById(itemDbId).orElse(null);
            }
            if (item == null) {
                // Fallback: try business itemId (UUID/string)
                Object businessId = body.get("itemId");
                if (businessId != null) {
                    item = itemRepository.findByItemId(businessId.toString()).orElse(null);
                }
            }
            if (item == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid item reference (itemDbId/itemId)"));
            }

            PromotionEntity promo = PromotionEntity.builder()
                    .item(item)
                    .promoPrice(promoPrice)
                    .startAt(startAt)
                    .endAt(endAt)
                    .active(true)
                    .build();
            promotionRepository.save(promo);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("id", promo.getId());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listActivePromotions() {
        Timestamp now = Timestamp.from(Instant.now());
        List<PromotionEntity> list = promotionRepository.findAll();
        List<Map<String, Object>> result = list.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()) && now.after(p.getStartAt()) && now.before(p.getEndAt()))
                .map(p -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", p.getId());
                    row.put("itemDbId", p.getItem().getId());
                    row.put("itemId", p.getItem().getItemId());
                    row.put("name", p.getItem().getName());
                    row.put("promoPrice", p.getPromoPrice());
                    row.put("startAt", p.getStartAt());
                    row.put("endAt", p.getEndAt());
                    return row;
                }).toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePromotion(@PathVariable Long id) {
        try {
            if (!promotionRepository.existsById(id)) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Promotion not found"));
            }
            promotionRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}


