package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.DeliveryRequest;
import in.bushansirgur.billingsoftware.io.DeliveryResponse;
import in.bushansirgur.billingsoftware.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    public ResponseEntity<List<DeliveryResponse>> listDeliveries() {
        return ResponseEntity.ok(deliveryService.listDeliveries());
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getDelivery(@PathVariable String deliveryId) {
        return ResponseEntity.ok(deliveryService.getDelivery(deliveryId));
    }

    @PostMapping
    public ResponseEntity<DeliveryResponse> createDraft(@RequestBody DeliveryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.createDraft(request));
    }

    @PutMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> updateDraft(
            @PathVariable String deliveryId,
            @RequestBody DeliveryRequest request) {
        return ResponseEntity.ok(deliveryService.updateDraft(deliveryId, request));
    }

    @PostMapping("/{deliveryId}/post")
    public ResponseEntity<DeliveryResponse> postDelivery(@PathVariable String deliveryId) {
        return ResponseEntity.ok(deliveryService.postDelivery(deliveryId));
    }
}
