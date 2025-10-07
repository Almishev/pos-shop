package in.bushansirgur.billingsoftware.io;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDetails {

    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
    // POS terminal payment fields
    private String posTransactionId;
    private String posAuthCode;
    private PaymentStatus status;
    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED
    }
}
