package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.ItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<ItemEntity, Long> {

    Optional<ItemEntity> findByItemId(String id);
    
    Optional<ItemEntity> findByBarcode(String barcode);

    Integer countByCategoryId(Long id);
    
    @Query("SELECT i FROM ItemEntity i WHERE i.name LIKE %:searchTerm% OR i.barcode LIKE %:searchTerm%")
    List<ItemEntity> findByNameContainingOrBarcodeContaining(@Param("searchTerm") String searchTerm);
}
