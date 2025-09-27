package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByCustomerId(String customerId);
    
    Optional<CustomerEntity> findByLoyaltyCardBarcode(String loyaltyCardBarcode);
    
    Optional<CustomerEntity> findByPhoneNumber(String phoneNumber);
    
    Optional<CustomerEntity> findByEmail(String email);
    
    List<CustomerEntity> findByIsLoyaltyActiveTrue();
    
    List<CustomerEntity> findByStatus(String status);
    
    @Query("SELECT c FROM CustomerEntity c WHERE c.firstName LIKE %:searchTerm% OR c.lastName LIKE %:searchTerm% OR c.phoneNumber LIKE %:searchTerm% OR c.loyaltyCardBarcode LIKE %:searchTerm%")
    List<CustomerEntity> searchCustomers(@Param("searchTerm") String searchTerm);
    
    @Query("SELECT COUNT(c) FROM CustomerEntity c WHERE c.isLoyaltyActive = true")
    Long countActiveLoyaltyCustomers();
    
    @Query("SELECT SUM(c.totalSpent) FROM CustomerEntity c WHERE c.isLoyaltyActive = true")
    Double getTotalLoyaltyRevenue();
    
    @Query("SELECT c FROM CustomerEntity c ORDER BY c.totalSpent DESC")
    List<CustomerEntity> findTopCustomersByTotalSpent(Integer limit);
}
