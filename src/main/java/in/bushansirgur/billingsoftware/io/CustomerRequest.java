package in.bushansirgur.billingsoftware.io;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerRequest {
    
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String loyaltyCardBarcode;
    private Boolean isLoyaltyActive;
    private String status;
    private String notes;
}
