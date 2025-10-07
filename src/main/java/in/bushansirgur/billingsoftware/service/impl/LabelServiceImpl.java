package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.service.LabelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabelServiceImpl implements LabelService {
    private final ItemRepository itemRepository;
    
    @Override
    public Map<String, Object> printPriceLabels(List<Map<String, Object>> items) {
        log.info("Printing price labels for {} items", items.size());
        
        List<Map<String, Object>> printedLabels = new ArrayList<>();
        
        for (Map<String, Object> item : items) {
            Map<String, Object> label = createPriceLabel(item);
            printedLabels.add(label);
            log.info("Created price label for item: {}", item.get("name"));
        }
        
        return Map.of(
            "success", true,
            "message", "Печат на " + items.size() + " ценови етикета",
            "labels", printedLabels,
            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    @Override
    public Map<String, Object> printShelfLabels(List<Map<String, Object>> categories) {
        log.info("Printing shelf labels for {} categories", categories.size());
        
        List<Map<String, Object>> printedLabels = new ArrayList<>();
        
        for (Map<String, Object> category : categories) {
            Map<String, Object> label = createShelfLabel(category);
            printedLabels.add(label);
            log.info("Created shelf label for category: {}", category.get("name"));
        }
        
        return Map.of(
            "success", true,
            "message", "Печат на " + categories.size() + " рафтови етикета",
            "labels", printedLabels,
            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    @Override
    public Map<String, Object> printPromoLabels(List<Map<String, Object>> promoItems) {
        log.info("Printing promo labels for {} items", promoItems.size());
        
        List<Map<String, Object>> printedLabels = new ArrayList<>();
        
        for (Map<String, Object> item : promoItems) {
            Map<String, Object> label = createPromoLabel(item);
            printedLabels.add(label);
            log.info("Created promo label for item: {}", item.get("name"));
        }
        
        return Map.of(
            "success", true,
            "message", "Печат на " + promoItems.size() + " промо етикета",
            "labels", printedLabels,
            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    @Override
    public Map<String, Object> bulkPrintAllItems(String categoryId) {
        log.info("Bulk printing all items for category: {}", categoryId);

        List<ItemEntity> items = itemRepository.findAll();

        // Optional filter by categoryId if provided
        if (categoryId != null && !categoryId.isBlank()) {
            try {
                Long catId = Long.parseLong(categoryId);
                items = items.stream()
                        .filter(it -> it.getCategory() != null && Objects.equals(it.getCategory().getId(), catId))
                        .toList();
            } catch (NumberFormatException e) {
                log.warn("Invalid categoryId provided for bulk print: {}", categoryId);
            }
        }

        List<Map<String, Object>> printedLabels = new ArrayList<>();
        for (ItemEntity entity : items) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", entity.getId());
            item.put("name", entity.getName());
            item.put("price", entity.getPrice() != null ? entity.getPrice().doubleValue() : 0.0);
            item.put("barcode", entity.getBarcode());
            printedLabels.add(createPriceLabel(item));
        }

        return Map.of(
                "success", true,
                "message", "Масов печат на " + printedLabels.size() + " етикета",
                "labels", printedLabels,
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    @Override
    public Map<String, Object> previewLabel(Map<String, Object> labelData) {
        log.info("Generating label preview for type: {}", labelData.get("type"));
        
        String labelType = (String) labelData.get("type");
        Map<String, Object> preview;
        
        switch (labelType) {
            case "price":
                preview = createPriceLabel(labelData);
                break;
            case "shelf":
                preview = createShelfLabel(labelData);
                break;
            case "promo":
                preview = createPromoLabel(labelData);
                break;
            default:
                throw new IllegalArgumentException("Unknown label type: " + labelType);
        }
        
        return Map.of(
            "success", true,
            "preview", preview,
            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    @Override
    public List<Map<String, Object>> getLabelTemplates() {
        return Arrays.asList(
            Map.of(
                "id", "price_standard",
                "name", "Стандартен ценови етикет",
                "size", "30x20mm",
                "description", "Име, цена, баркод"
            ),
            Map.of(
                "id", "price_large",
                "name", "Голям ценови етикет",
                "size", "50x30mm",
                "description", "Име, цена, баркод, промо"
            ),
            Map.of(
                "id", "shelf_standard",
                "name", "Стандартен рафтов етикет",
                "size", "100x50mm",
                "description", "Категория, промоция"
            ),
            Map.of(
                "id", "promo_standard",
                "name", "Стандартен промо етикет",
                "size", "40x25mm",
                "description", "Стара/нова цена, дати"
            )
        );
    }
    
    private Map<String, Object> createPriceLabel(Map<String, Object> item) {
        return Map.of(
            "type", "price",
            "itemId", item.get("id"),
            "name", item.get("name"),
            "price", item.get("price"),
            "barcode", item.get("barcode"),
            "template", "price_standard",
            "html", generatePriceLabelHTML(item),
            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    private Map<String, Object> createShelfLabel(Map<String, Object> category) {
        return Map.of(
            "type", "shelf",
            "categoryId", category.get("id"),
            "name", category.get("name"),
            "promotion", category.getOrDefault("promotion", ""),
            "template", "shelf_standard",
            "html", generateShelfLabelHTML(category),
            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    private Map<String, Object> createPromoLabel(Map<String, Object> item) {
        return Map.of(
            "type", "promo",
            "itemId", item.get("id"),
            "name", item.get("name"),
            "oldPrice", item.get("oldPrice"),
            "newPrice", item.get("newPrice"),
            "promoStart", item.get("promoStart"),
            "promoEnd", item.get("promoEnd"),
            "template", "promo_standard",
            "html", generatePromoLabelHTML(item),
            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    private String generatePriceLabelHTML(Map<String, Object> item) {
        return String.format("""
            <div class="price-label" style="width: 30mm; height: 20mm; border: 1px solid #000; padding: 2mm; font-family: Arial, sans-serif;">
                <div style="font-size: 8pt; font-weight: bold; text-align: center; margin-bottom: 1mm;">%s</div>
                <div style="font-size: 10pt; font-weight: bold; text-align: center; color: #d32f2f; margin-bottom: 1mm;">%.2f лв.</div>
                <div style="font-size: 6pt; text-align: center;">%s</div>
            </div>
            """, 
            item.get("name"), 
            item.get("price"), 
            item.get("barcode")
        );
    }
    
    private String generateShelfLabelHTML(Map<String, Object> category) {
        return String.format("""
            <div class="shelf-label" style="width: 100mm; height: 50mm; border: 2px solid #1976d2; padding: 3mm; font-family: Arial, sans-serif; background: #e3f2fd;">
                <div style="font-size: 16pt; font-weight: bold; text-align: center; color: #1976d2; margin-bottom: 2mm;">%s</div>
                <div style="font-size: 12pt; text-align: center; color: #d32f2f;">%s</div>
            </div>
            """, 
            category.get("name"), 
            category.getOrDefault("promotion", "")
        );
    }
    
    private String generatePromoLabelHTML(Map<String, Object> item) {
        return String.format("""
            <div class="promo-label" style="width: 40mm; height: 25mm; border: 2px solid #d32f2f; padding: 2mm; font-family: Arial, sans-serif; background: #ffebee;">
                <div style="font-size: 8pt; font-weight: bold; text-align: center; margin-bottom: 1mm;">%s</div>
                <div style="font-size: 8pt; text-align: center; text-decoration: line-through; color: #666;">%.2f лв.</div>
                <div style="font-size: 10pt; font-weight: bold; text-align: center; color: #d32f2f;">%.2f лв.</div>
                <div style="font-size: 6pt; text-align: center; color: #d32f2f;">ПРОМОЦИЯ</div>
            </div>
            """, 
            item.get("name"), 
            item.get("oldPrice"), 
            item.get("newPrice")
        );
    }
}
