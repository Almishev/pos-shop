package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.config.CardTerminalProperties;
import in.bushansirgur.billingsoftware.io.PosPaymentIO;
import in.bushansirgur.billingsoftware.io.PaymentDetails;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.service.PosPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PosPaymentServiceImpl implements PosPaymentService {

    private final CardTerminalProperties props;
    private final OrderEntityRepository orderRepository;

    @Override
    @Transactional
    public PosPaymentIO.InitiateResponse initiate(PosPaymentIO.InitiateRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment request");
        }
        // Using configured merchant/terminal identifiers from application.properties

        // Use simulation flag to mock or later call real provider using apiEndpoint/apiKey
        boolean approved = props.isSimulate() ? request.getAmount().compareTo(new BigDecimal("1000")) <= 0 : true;
        System.out.println("=== PosPaymentServiceImpl.initiate ===");
        System.out.println("simulate=" + props.isSimulate() + ", merchantId=" + props.getMerchantId() + ", terminalId=" + props.getDefaultId());
        System.out.println("orderId=" + request.getOrderId() + ", amount=" + request.getAmount() + ", decision=" + (approved ? "APPROVED" : "DECLINED"));
        PosPaymentIO.InitiateResponse resp = PosPaymentIO.InitiateResponse.builder()
                .transactionId(UUID.randomUUID().toString())
                .status(approved ? "APPROVED" : "DECLINED")
                .message(approved ? "Approved (mock)" : "Declined (mock limit)")
                .authCode(approved ? String.valueOf((int)(Math.random()*900000)+100000) : null)
                .build();

        // Persist to order if provided
        if (request.getOrderId() != null && !request.getOrderId().isEmpty()) {
            orderRepository.findByOrderId(request.getOrderId()).ifPresent(order -> {
                PaymentDetails details = order.getPaymentDetails();
                if (details == null) {
                    details = new PaymentDetails();
                    order.setPaymentDetails(details);
                }
                details.setPosTransactionId(resp.getTransactionId());
                details.setPosAuthCode(resp.getAuthCode());
                details.setStatus(approved ? PaymentDetails.PaymentStatus.COMPLETED : PaymentDetails.PaymentStatus.FAILED);
                System.out.println("Updating order " + order.getOrderId() + " paymentDetails: status=" + details.getStatus());
                orderRepository.save(order);
            });
        }
        return resp;
    }

    @Override
    @Transactional
    public PosPaymentIO.RefundResponse refund(PosPaymentIO.RefundRequest request) {
        if (request.getOriginalTransactionId() == null ||
                request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid refund request");
        }
        // Using configured merchant/terminal identifiers from application.properties

        boolean approved = props.isSimulate() ? true : true; // placeholder for real provider
        return PosPaymentIO.RefundResponse.builder()
                .refundTransactionId(UUID.randomUUID().toString())
                .status(approved ? "APPROVED" : "DECLINED")
                .message(approved ? "Refund approved (mock)" : "Refund declined (mock)")
                .build();
    }
}


