package in.bushansirgur.billingsoftware.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.bushansirgur.billingsoftware.io.ItemRequest;
import in.bushansirgur.billingsoftware.io.ItemResponse;
import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/admin/items")
    public ItemResponse addItem(@RequestPart("item") String itemString,
                                @RequestPart(value = "file", required = false) MultipartFile file) {
        ObjectMapper objectMapper = new ObjectMapper();
        ItemRequest itemRequest = null;
        try {
            itemRequest = objectMapper.readValue(itemString, ItemRequest.class);
            return itemService.add(itemRequest, file);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error occurred while processing the json: " + ex.getMessage());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error occurred while processing the file: " + ex.getMessage());
        }
    }

    @GetMapping("/items")
    public List<ItemResponse> readItems() {
        return itemService.fetchItems();
    }

    @GetMapping("/items/barcode/{barcode}")
    public ItemResponse findItemByBarcode(@PathVariable String barcode) {
        return itemService.findItemByBarcode(barcode);
    }

    @GetMapping("/items/search")
    public List<ItemResponse> searchItems(@RequestParam String q) {
        return itemService.searchItems(q);
    }

    @PostMapping("/admin/items/generate-missing-ids")
    public ResponseEntity<String> generateMissingItemIds() {
        try {
            itemService.generateMissingItemIds();
            return ResponseEntity.ok("Missing item IDs generated successfully");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error generating missing item IDs: " + e.getMessage());
        }
    }

    @GetMapping("/items/debug/all")
    public ResponseEntity<List<Map<String, Object>>> debugAllItems() {
        try {
            List<ItemEntity> items = itemService.getAllItemsForDebug();
            List<Map<String, Object>> debugInfo = items.stream()
                    .map(item -> {
                        Map<String, Object> info = new java.util.HashMap<>();
                        info.put("id", item.getId());
                        info.put("itemId", item.getItemId());
                        info.put("name", item.getName());
                        info.put("hasItemId", item.getItemId() != null && !item.getItemId().trim().isEmpty());
                        return info;
                    })
                    .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(debugInfo);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error getting debug info: " + e.getMessage());
        }
    }

    @GetMapping("/items/generate-barcode")
    public ResponseEntity<Map<String, String>> generateBarcode() {
        // Generate a valid EAN-13 numeric barcode
        // Strategy: use a 12-digit base from timestamp/random, then append checksum
        String base12 = String.valueOf(System.currentTimeMillis());
        // Ensure length 12 by trimming/padding
        if (base12.length() > 12) {
            base12 = base12.substring(base12.length() - 12);
        } else if (base12.length() < 12) {
            base12 = ("000000000000" + base12).substring(base12.length());
        }
        int check = computeEan13Checksum(base12);
        String barcode = base12 + check;
        Map<String, String> response = new HashMap<>();
        response.put("barcode", barcode);
        return ResponseEntity.ok(response);
    }

    private int computeEan13Checksum(String base12Digits) {
        int sum = 0;
        for (int i = 0; i < base12Digits.length(); i++) {
            int digit = base12Digits.charAt(i) - '0';
            sum += digit * ((i % 2 == 0) ? 1 : 3);
        }
        return (10 - (sum % 10)) % 10;
    }

    @PutMapping("/admin/items/{itemId}")
    public ItemResponse updateItem(@PathVariable String itemId,
                                  @RequestPart("item") String itemString,
                                  @RequestPart(value = "file", required = false) MultipartFile file) {
        ObjectMapper objectMapper = new ObjectMapper();
        ItemRequest itemRequest = null;
        try {
            itemRequest = objectMapper.readValue(itemString, ItemRequest.class);
            return itemService.update(itemId, itemRequest, file);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error occurred while processing the json: " + ex.getMessage());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error occurred while processing the file: " + ex.getMessage());
        }
    }

    @GetMapping("/items/{itemId}")
    public ItemResponse getItemById(@PathVariable String itemId) {
        System.out.println("=== ItemController.getItemById called ===");
        System.out.println("ItemId: " + itemId);
        System.out.println("ItemId type: " + itemId.getClass().getSimpleName());
        try {
            ItemResponse response = itemService.getItemById(itemId);
            System.out.println("Item found: " + response.getName());
            return response;
        } catch (Exception e) {
            System.err.println("Error getting item by ID: " + itemId);
            System.err.println("Error: " + e.getMessage());
            throw e;
        }
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/admin/items/{itemId}")
    public void removeItem(@PathVariable String itemId) {
        try {
            itemService.deleteItem(itemId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
    }
}
