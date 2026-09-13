package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.DeliveryRequest;
import in.bushansirgur.billingsoftware.io.DeliveryResponse;

import java.util.List;

public interface DeliveryService {
    List<DeliveryResponse> listDeliveries();
    DeliveryResponse getDelivery(String deliveryId);
    DeliveryResponse createDraft(DeliveryRequest request);
    DeliveryResponse updateDraft(String deliveryId, DeliveryRequest request);
    DeliveryResponse postDelivery(String deliveryId);
}
