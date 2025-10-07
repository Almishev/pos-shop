package in.bushansirgur.billingsoftware.io;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

public class PosPaymentIO {

    @Data
    @Builder
    public static class InitiateRequest {
        private Long posTerminalId;
        private String orderId;
        private BigDecimal amount;
        private String currency;
    }

    @Data
    @Builder
    public static class InitiateResponse {
        private String transactionId;
        private String status; // APPROVED, DECLINED, PENDING
        private String message;
        private String authCode;
    }

    @Data
    @Builder
    public static class RefundRequest {
        private Long posTerminalId;
        private String originalTransactionId;
        private BigDecimal amount;
        private String currency;
        private String reason;
    }

    @Data
    @Builder
    public static class RefundResponse {
        private String refundTransactionId;
        private String status; // APPROVED, DECLINED
        private String message;
    }
}


