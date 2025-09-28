package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.ItemRequest;
import in.bushansirgur.billingsoftware.io.ItemResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ItemService {

    ItemResponse add(ItemRequest request, MultipartFile file) throws IOException;

    ItemResponse update(String itemId, ItemRequest request, MultipartFile file) throws IOException;

    ItemResponse getItemById(String itemId);

    List<ItemResponse> fetchItems();

    ItemResponse findItemByBarcode(String barcode);

    List<ItemResponse> searchItems(String searchTerm);

    void deleteItem(String itemId);
    
    void generateMissingItemIds();
    
    List<in.bushansirgur.billingsoftware.entity.ItemEntity> getAllItemsForDebug();
}
