package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerResponse {
    
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String loyaltyCardBarcode;
    private Boolean isLoyaltyActive;
    private Integer loyaltyPoints;
    private BigDecimal totalSpent;
    private Integer totalOrders;
    private String status;
    private String notes;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp lastPurchaseDate;
}
