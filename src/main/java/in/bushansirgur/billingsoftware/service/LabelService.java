package in.bushansirgur.billingsoftware.service;

import java.util.List;
import java.util.Map;

public interface LabelService {
    
    /**
     * Печат на ценови етикети за избрани продукти
     */
    Map<String, Object> printPriceLabels(List<Map<String, Object>> items);
    
    /**
     * Печат на рафтови етикети за категории
     */
    Map<String, Object> printShelfLabels(List<Map<String, Object>> categories);
    
    /**
     * Печат на промо етикети със стара/нова цена
     */
    Map<String, Object> printPromoLabels(List<Map<String, Object>> promoItems);
    
    /**
     * Масов печат на всички продукти или по категория
     */
    Map<String, Object> bulkPrintAllItems(String categoryId);
    
    /**
     * Предварителен преглед на етикет
     */
    Map<String, Object> previewLabel(Map<String, Object> labelData);
    
    /**
     * Получаване на налични етикет шаблони
     */
    List<Map<String, Object>> getLabelTemplates();
}
