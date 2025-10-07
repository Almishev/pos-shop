package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/labels")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class LabelController {
    
    private final LabelService labelService;
    
    // Печат на ценови етикети
    @PostMapping("/price-labels")
    public ResponseEntity<Map<String, Object>> printPriceLabels(@RequestBody List<Map<String, Object>> items) {
        try {
            Map<String, Object> result = labelService.printPriceLabels(items);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Печат на рафтови етикети
    @PostMapping("/shelf-labels")
    public ResponseEntity<Map<String, Object>> printShelfLabels(@RequestBody List<Map<String, Object>> categories) {
        try {
            Map<String, Object> result = labelService.printShelfLabels(categories);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Печат на промо етикети
    @PostMapping("/promo-labels")
    public ResponseEntity<Map<String, Object>> printPromoLabels(@RequestBody List<Map<String, Object>> promoItems) {
        try {
            Map<String, Object> result = labelService.printPromoLabels(promoItems);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Масов печат на всички продукти
    @PostMapping("/bulk-print")
    public ResponseEntity<Map<String, Object>> bulkPrintAllItems(@RequestParam(required = false) String categoryId) {
        try {
            Map<String, Object> result = labelService.bulkPrintAllItems(categoryId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Предварителен преглед на етикет
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewLabel(@RequestBody Map<String, Object> labelData) {
        try {
            Map<String, Object> result = labelService.previewLabel(labelData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Получаване на налични етикет шаблони
    @GetMapping("/templates")
    public ResponseEntity<List<Map<String, Object>>> getLabelTemplates() {
        try {
            List<Map<String, Object>> templates = labelService.getLabelTemplates();
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(List.of());
        }
    }
}
