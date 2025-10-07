package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.PosPaymentIO;
import in.bushansirgur.billingsoftware.service.PosPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos-payments")
@RequiredArgsConstructor
public class PosPaymentController {

    private final PosPaymentService service;

    @PostMapping("/initiate")
    public ResponseEntity<PosPaymentIO.InitiateResponse> initiate(@RequestBody PosPaymentIO.InitiateRequest request) {
        try {
            System.out.println("=== PosPaymentController.initiate ===");
            System.out.println("OrderId=" + request.getOrderId() + ", Amount=" + request.getAmount() + ", Currency=" + request.getCurrency());
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                System.out.println("Authenticated user: " + auth.getName());
            }
        } catch (Exception ignore) {}
        return ResponseEntity.status(HttpStatus.CREATED).body(service.initiate(request));
    }

    @PostMapping("/refund")
    public PosPaymentIO.RefundResponse refund(@RequestBody PosPaymentIO.RefundRequest request) {
        return service.refund(request);
    }
}


