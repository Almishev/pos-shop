package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.DeliveryEntity;
import in.bushansirgur.billingsoftware.entity.DeliveryLineEntity;
import in.bushansirgur.billingsoftware.entity.ItemEntity;
import in.bushansirgur.billingsoftware.io.DeliveryRequest;
import in.bushansirgur.billingsoftware.io.DeliveryResponse;
import in.bushansirgur.billingsoftware.io.InventoryRequest;
import in.bushansirgur.billingsoftware.repository.DeliveryRepository;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.service.DeliveryService;
import in.bushansirgur.billingsoftware.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final ItemRepository itemRepository;
    private final InventoryService inventoryService;

    @Override
    public List<DeliveryResponse> listDeliveries() {
        return deliveryRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DeliveryResponse getDelivery(String deliveryId) {
        return toResponse(getEntity(deliveryId));
    }

    @Override
    @Transactional
    public DeliveryResponse createDraft(DeliveryRequest request) {
        validateLines(request);
        DeliveryEntity delivery = DeliveryEntity.builder()
                .referenceNumber(request.getReferenceNumber())
                .supplierName(request.getSupplierName())
                .deliveryDate(request.getDeliveryDate() != null ? request.getDeliveryDate() : LocalDate.now())
                .notes(request.getNotes())
                .createdBy(request.getCreatedBy() != null ? request.getCreatedBy() : "Admin")
                .status(DeliveryEntity.DeliveryStatus.DRAFT)
                .lines(new ArrayList<>())
                .build();

        applyLines(delivery, request);
        return toResponse(deliveryRepository.save(delivery));
    }

    @Override
    @Transactional
    public DeliveryResponse updateDraft(String deliveryId, DeliveryRequest request) {
        DeliveryEntity delivery = getEntity(deliveryId);
        if (delivery.getStatus() != DeliveryEntity.DeliveryStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Posted deliveries cannot be edited");
        }
        validateLines(request);

        delivery.setReferenceNumber(request.getReferenceNumber());
        delivery.setSupplierName(request.getSupplierName());
        delivery.setDeliveryDate(request.getDeliveryDate() != null ? request.getDeliveryDate() : delivery.getDeliveryDate());
        delivery.setNotes(request.getNotes());
        if (request.getCreatedBy() != null) {
            delivery.setCreatedBy(request.getCreatedBy());
        }

        delivery.getLines().clear();
        applyLines(delivery, request);
        return toResponse(deliveryRepository.save(delivery));
    }

    @Override
    @Transactional
    public DeliveryResponse postDelivery(String deliveryId) {
        DeliveryEntity delivery = getEntity(deliveryId);
        if (delivery.getStatus() == DeliveryEntity.DeliveryStatus.POSTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery already posted");
        }
        if (delivery.getLines() == null || delivery.getLines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery has no lines");
        }

        for (DeliveryLineEntity line : delivery.getLines()) {
            InventoryRequest stockRequest = InventoryRequest.builder()
                    .itemId(line.getItemId())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitCost())
                    .notes("Delivery " + delivery.getDeliveryId()
                            + (delivery.getReferenceNumber() != null ? " / " + delivery.getReferenceNumber() : ""))
                    .createdBy(delivery.getCreatedBy())
                    .referenceNumber(delivery.getDeliveryId())
                    .referenceType("DELIVERY")
                    .build();
            inventoryService.addStock(stockRequest);
        }

        delivery.setStatus(DeliveryEntity.DeliveryStatus.POSTED);
        delivery.setPostedAt(LocalDateTime.now());
        return toResponse(deliveryRepository.save(delivery));
    }

    private void validateLines(DeliveryRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one delivery line is required");
        }
        for (DeliveryRequest.DeliveryLineRequest line : request.getLines()) {
            if (line.getItemId() == null || line.getItemId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item id is required on each line");
            }
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than 0");
            }
            if (line.getUnitCost() == null || line.getUnitCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery unit cost must be greater than 0");
            }
            itemRepository.findByItemId(line.getItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + line.getItemId()));
        }
    }

    private void applyLines(DeliveryEntity delivery, DeliveryRequest request) {
        for (DeliveryRequest.DeliveryLineRequest lineReq : request.getLines()) {
            ItemEntity item = itemRepository.findByItemId(lineReq.getItemId()).orElseThrow();
            BigDecimal lineTotal = lineReq.getUnitCost().multiply(BigDecimal.valueOf(lineReq.getQuantity()));
            DeliveryLineEntity line = DeliveryLineEntity.builder()
                    .delivery(delivery)
                    .itemId(item.getItemId())
                    .itemName(item.getName())
                    .quantity(lineReq.getQuantity())
                    .unitCost(lineReq.getUnitCost())
                    .lineTotal(lineTotal)
                    .build();
            delivery.getLines().add(line);
        }
    }

    private DeliveryEntity getEntity(String deliveryId) {
        return deliveryRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found: " + deliveryId));
    }

    private DeliveryResponse toResponse(DeliveryEntity delivery) {
        List<DeliveryResponse.DeliveryLineResponse> lines = delivery.getLines() == null
                ? List.of()
                : delivery.getLines().stream()
                .map(line -> DeliveryResponse.DeliveryLineResponse.builder()
                        .id(line.getId())
                        .itemId(line.getItemId())
                        .itemName(line.getItemName())
                        .quantity(line.getQuantity())
                        .unitCost(line.getUnitCost())
                        .lineTotal(line.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalCost = lines.stream()
                .map(l -> l.getLineTotal() != null ? l.getLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DeliveryResponse.builder()
                .deliveryId(delivery.getDeliveryId())
                .referenceNumber(delivery.getReferenceNumber())
                .supplierName(delivery.getSupplierName())
                .deliveryDate(delivery.getDeliveryDate())
                .notes(delivery.getNotes())
                .status(delivery.getStatus() != null ? delivery.getStatus().name() : null)
                .createdBy(delivery.getCreatedBy())
                .createdAt(delivery.getCreatedAt())
                .postedAt(delivery.getPostedAt())
                .totalCost(totalCost)
                .lines(lines)
                .build();
    }
}
