package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.entity.OrderItemEntity;
import in.bushansirgur.billingsoftware.io.*;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.service.CashDrawerSessionService;
import in.bushansirgur.billingsoftware.service.InventoryService;
import in.bushansirgur.billingsoftware.service.OrderService;
import in.bushansirgur.billingsoftware.service.PosPaymentService;
import in.bushansirgur.billingsoftware.io.PosPaymentIO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    
    private final OrderEntityRepository orderEntityRepository; 
    private final InventoryService inventoryService;
    private final PosPaymentService posPaymentService;
    private final CashDrawerSessionService cashDrawerSessionService;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to create an order");
        }
        String cashierUsername = auth.getName();

        try {
            cashDrawerSessionService.getActiveSession(cashierUsername, LocalDate.now());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "Няма активна касова сесия. Започнете работен ден преди продажба.");
            }
            throw ex;
        }

        OrderEntity newOrder = convertToOrderEntity(request);

        List<OrderItemEntity> orderItems = request.getCartItems().stream()
                .map(this::convertToOrderItemEntity)
                .collect(Collectors.toList());

        // Authoritative totals from line items (VAT-inclusive prices)
        double computedSubtotal = 0.0;
        double computedTax = 0.0;
        for (OrderItemEntity item : orderItems) {
            double price = item.getPrice() != null ? item.getPrice() : 0.0;
            double qty = item.getQuantity() != null ? item.getQuantity() : 0.0;
            double lineGross = price * qty;
            computedSubtotal += lineGross;
            double rate = item.getVatRate() != null ? item.getVatRate() : 0.20;
            if (rate > 1.0) {
                rate = rate / 100.0;
                item.setVatRate(rate);
            }
            if (rate > 0 && lineGross > 0) {
                double base = lineGross / (1.0 + rate);
                computedTax += (lineGross - base);
            }
        }
        computedSubtotal = Math.round(computedSubtotal * 100.0) / 100.0;
        computedTax = Math.round(computedTax * 100.0) / 100.0;
        newOrder.setSubtotal(computedSubtotal);
        newOrder.setTax(computedTax);
        newOrder.setGrandTotal(computedSubtotal);

        PaymentDetails paymentDetails = new PaymentDetails();
        // Card/split are approved on the client before create (sim); cash is immediate
        paymentDetails.setStatus(PaymentDetails.PaymentStatus.COMPLETED);
        if (newOrder.getPaymentMethod() == PaymentMethod.SPLIT) {
            paymentDetails.setCashAmount(request.getCashAmount());
            paymentDetails.setCardAmount(request.getCardAmount());
        }
        newOrder.setPaymentDetails(paymentDetails);

        newOrder.setItems(orderItems);
        newOrder.setCashierUsername(cashierUsername);
        
        newOrder = orderEntityRepository.save(newOrder);

        for (OrderRequest.OrderItemRequest itemReq : request.getCartItems()) {
            int invQty = itemReq.getQuantity() != null
                    ? (int) Math.max(1, Math.round(itemReq.getQuantity()))
                    : 1;
            inventoryService.processSaleTransaction(itemReq.getItemId(), invQty, newOrder.getOrderId());
        }

        return convertToResponse(newOrder);
    }

    @Override
    @Transactional
    public OrderResponse refundOrder(in.bushansirgur.billingsoftware.io.OrderRefundRequest request) {
        OrderEntity original = orderEntityRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (original.getOriginalOrderId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot refund a refund/void record");
        }
        if (original.getStatus() == OrderStatus.VOIDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot refund a voided order");
        }
        if (original.getStatus() == OrderStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order already fully refunded");
        }

        Map<String, Double> alreadyReturned = getAlreadyReturnedQuantities(original.getOrderId());
        Map<String, OrderItemEntity> originalByItemId = original.getItems().stream()
                .collect(Collectors.toMap(OrderItemEntity::getItemId, oi -> oi, (a, b) -> a));

        List<OrderItemEntity> refundItems = new ArrayList<>();
        double refundSubtotal = 0.0;

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (OrderRefundRequest.RefundItem ri : request.getItems()) {
                if (ri.getItemId() == null || ri.getQuantity() == null || ri.getQuantity() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid refund item quantity");
                }
                OrderItemEntity oi = originalByItemId.get(ri.getItemId());
                if (oi == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not in order: " + ri.getItemId());
                }
                double returned = alreadyReturned.getOrDefault(ri.getItemId(), 0.0);
                double origQty = oi.getQuantity() != null ? oi.getQuantity() : 0.0;
                double available = Math.max(0, origQty - returned);
                if (ri.getQuantity() > available + 1e-9) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot return more than available for " + oi.getName()
                                    + " (available: " + available + ")");
                }
                refundItems.add(OrderItemEntity.builder()
                        .itemId(oi.getItemId())
                        .name(oi.getName())
                        .barcode(oi.getBarcode())
                        .price(oi.getPrice())
                        .quantity(-Math.abs(ri.getQuantity()))
                        .vatRate(oi.getVatRate())
                        .build());
                refundSubtotal += (oi.getPrice() != null ? oi.getPrice() : 0.0) * ri.getQuantity();
            }
        } else {
            for (OrderItemEntity oi : original.getItems()) {
                double returned = alreadyReturned.getOrDefault(oi.getItemId(), 0.0);
                double origQty = oi.getQuantity() != null ? oi.getQuantity() : 0.0;
                double available = Math.max(0, origQty - returned);
                if (available <= 0) continue;
                refundItems.add(OrderItemEntity.builder()
                        .itemId(oi.getItemId())
                        .name(oi.getName())
                        .barcode(oi.getBarcode())
                        .price(oi.getPrice())
                        .quantity(-available)
                        .vatRate(oi.getVatRate())
                        .build());
                refundSubtotal += (oi.getPrice() != null ? oi.getPrice() : 0.0) * available;
            }
        }

        if (refundItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing left to return on this order");
        }

        // Item prices are VAT-inclusive (same as sale grandTotal). Tax on the refund
        // record is the proportional VAT share for fiscal reporting only — not added again.
        double refundTax = 0.0;
        if (original.getSubtotal() != null && original.getSubtotal() > 0 && original.getTax() != null) {
            refundTax = original.getTax() * (refundSubtotal / original.getSubtotal());
        }
        double refundAmount = request.getRefundAmount() != null ? request.getRefundAmount() : refundSubtotal;
        if (refundAmount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount must be greater than 0");
        }

        // Restock returned items (skip quietly if catalog item was deleted)
        List<String> skippedRestock = new ArrayList<>();
        for (OrderItemEntity ri : refundItems) {
            boolean restocked = inventoryService.processReturnTransaction(
                    ri.getItemId(),
                    ri.getBarcode(),
                    (int) Math.max(1, Math.round(Math.abs(ri.getQuantity() != null ? ri.getQuantity() : 0))),
                    "REF-" + original.getOrderId());
            if (!restocked) {
                skippedRestock.add(ri.getName() != null ? ri.getName() : ri.getItemId());
            }
        }
        if (!skippedRestock.isEmpty()) {
            System.out.println("Refund completed without restock for deleted items: " + skippedRestock);
        }

        // POS refund for card payments (mock/provider controlled in service)
        if ("CARD".equalsIgnoreCase(request.getRefundMethod())) {
            try {
                String originalTxnId = original.getPaymentDetails() != null
                        ? original.getPaymentDetails().getPosTransactionId() : null;
                if (originalTxnId != null && refundAmount > 0) {
                    PosPaymentIO.RefundResponse rr = posPaymentService.refund(PosPaymentIO.RefundRequest.builder()
                            .originalTransactionId(originalTxnId)
                            .amount(java.math.BigDecimal.valueOf(refundAmount))
                            .currency("EUR")
                            .reason(request.getReason())
                            .build());
                    if (!"APPROVED".equalsIgnoreCase(rr.getStatus())) {
                        throw new RuntimeException("Card refund declined by provider");
                    }
                    if (original.getPaymentDetails() != null) {
                        original.getPaymentDetails().setPosRefundTransactionId(rr.getRefundTransactionId());
                    }
                }
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "POS refund failed: " + ex.getMessage());
            }
        }

        // Update returned map and decide status
        for (OrderItemEntity ri : refundItems) {
            alreadyReturned.merge(ri.getItemId(), Math.abs(ri.getQuantity()), Double::sum);
        }
        boolean fullyRefunded = original.getItems().stream().allMatch(oi -> {
            double origQty = oi.getQuantity() != null ? oi.getQuantity() : 0.0;
            return alreadyReturned.getOrDefault(oi.getItemId(), 0.0) >= origQty - 1e-9;
        });
        original.setStatus(fullyRefunded ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);
        orderEntityRepository.save(original);

        String cashier = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : original.getCashierUsername();

        OrderEntity refund = OrderEntity.builder()
                .customerName(original.getCustomerName())
                .phoneNumber(original.getPhoneNumber())
                .subtotal(-Math.abs(refundSubtotal))
                .tax(-Math.abs(refundTax))
                .grandTotal(-Math.abs(refundAmount))
                .paymentMethod(original.getPaymentMethod())
                .status(OrderStatus.REFUNDED)
                .originalOrderId(original.getOrderId())
                .cashierUsername(cashier)
                .build();
        refund.setItems(refundItems);
        refund = orderEntityRepository.save(refund);
        return convertToResponse(refund);
    }

    private Map<String, Double> getAlreadyReturnedQuantities(String orderId) {
        Map<String, Double> map = new HashMap<>();
        for (OrderEntity refund : orderEntityRepository.findByOriginalOrderId(orderId)) {
            if (refund.getItems() == null) continue;
            for (OrderItemEntity item : refund.getItems()) {
                if (item.getItemId() == null || item.getQuantity() == null) continue;
                map.merge(item.getItemId(), Math.abs(item.getQuantity()), Double::sum);
            }
        }
        return map;
    }

    private OrderItemEntity convertToOrderItemEntity(OrderRequest.OrderItemRequest orderItemRequest) {
        Double vatRate = orderItemRequest.getVatRate();
        if (vatRate == null) {
            vatRate = 0.20;
        } else if (vatRate > 1.0) {
            vatRate = vatRate / 100.0;
        }
        return OrderItemEntity.builder()
                .itemId(orderItemRequest.getItemId())
                .name(orderItemRequest.getName())
                .barcode(orderItemRequest.getBarcode())
                .price(orderItemRequest.getPrice())
                .quantity(orderItemRequest.getQuantity())
                .vatRate(vatRate)
                .build();
    }

    private OrderResponse convertToResponse(OrderEntity newOrder) {
        boolean isOriginalSale = newOrder.getOriginalOrderId() == null;
        Map<String, Double> returned = isOriginalSale
                ? getAlreadyReturnedQuantities(newOrder.getOrderId())
                : Map.of();

        return OrderResponse.builder()
                .orderId(newOrder.getOrderId())
                .customerName(newOrder.getCustomerName())
                .phoneNumber(newOrder.getPhoneNumber())
                .subtotal(newOrder.getSubtotal())
                .tax(newOrder.getTax())
                .grandTotal(newOrder.getGrandTotal())
                .paymentMethod(newOrder.getPaymentMethod())
                .cashierUsername(newOrder.getCashierUsername())
                .orderStatus(newOrder.getStatus())
                .originalOrderId(newOrder.getOriginalOrderId())
                .items(newOrder.getItems().stream()
                        .map(oi -> convertToItemResponse(oi, returned, isOriginalSale))
                        .collect(Collectors.toList()))
                .paymentDetails(newOrder.getPaymentDetails())
                .createdAt(newOrder.getCreatedAt())
                .build();
                
    }

    private OrderResponse.OrderItemResponse convertToItemResponse(OrderItemEntity orderItemEntity,
                                                                  Map<String, Double> alreadyReturned,
                                                                  boolean isOriginalSale) {
        double qty = orderItemEntity.getQuantity() != null ? orderItemEntity.getQuantity() : 0.0;
        Double refunded = null;
        Double returnable = null;
        if (isOriginalSale) {
            refunded = alreadyReturned.getOrDefault(orderItemEntity.getItemId(), 0.0);
            returnable = Math.max(0, qty - refunded);
        }
        return OrderResponse.OrderItemResponse.builder()
                .itemId(orderItemEntity.getItemId())
                .name(orderItemEntity.getName())
                .barcode(orderItemEntity.getBarcode())
                .price(orderItemEntity.getPrice())
                .quantity(orderItemEntity.getQuantity())
                .vatRate(orderItemEntity.getVatRate())
                .refundedQuantity(refunded)
                .returnableQuantity(returnable)
                .build();
    }

    private OrderEntity convertToOrderEntity(OrderRequest request) {
        return OrderEntity.builder()
                .customerName(request.getCustomerName())
                .phoneNumber(request.getPhoneNumber())
                .subtotal(request.getSubtotal())
                .tax(request.getTax())
                .grandTotal(request.getGrandTotal())
                .paymentMethod(PaymentMethod.valueOf(request.getPaymentMethod()))
                .build();
    }

    @Override
    @Transactional
    public void deleteOrder(String orderId) {
        OrderEntity existingOrder = orderEntityRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Restock sale lines when aborting an uncompleted sale (e.g. fiscal/payment failure)
        if (existingOrder.getOriginalOrderId() == null
                && existingOrder.getItems() != null
                && existingOrder.getStatus() != OrderStatus.REFUNDED
                && existingOrder.getStatus() != OrderStatus.PARTIALLY_REFUNDED
                && existingOrder.getStatus() != OrderStatus.VOIDED) {
            for (OrderItemEntity item : existingOrder.getItems()) {
                if (item.getItemId() == null || item.getQuantity() == null || item.getQuantity() <= 0) continue;
                try {
                    inventoryService.processReturnTransaction(
                            item.getItemId(),
                            item.getBarcode(),
                            (int) Math.max(1, Math.round(item.getQuantity())),
                            "ABORT-" + orderId);
                } catch (Exception ex) {
                    System.out.println("Abort restock skipped for " + item.getItemId() + ": " + ex.getMessage());
                }
            }
        }

        orderEntityRepository.delete(existingOrder);
    }

    @Override
    public OrderResponse getOrderById(String orderId) {
        OrderEntity existingOrder = orderEntityRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        return convertToResponse(existingOrder);
    }

    @Override
    public List<OrderResponse> getLatestOrders() {
        return orderEntityRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Double sumSalesByDate(LocalDate date) {
        return orderEntityRepository.sumSalesByDate(date);
    }

    @Override
    public Long countByOrderDate(LocalDate date) {
        return orderEntityRepository.countByOrderDate(date);
    }

    @Override
    public Double sumTaxByDate(LocalDate date) {
        Double tax = orderEntityRepository.sumTaxByDate(date);
        return tax != null ? tax : 0.0;
    }

    @Override
    public List<OrderResponse> findRecentOrders() {
        return orderEntityRepository.findRecentOrders(PageRequest.of(0, 5))
                .stream()
                .map(orderEntity -> convertToResponse(orderEntity))
                .collect(Collectors.toList());
    }

    @Override
    public Page<OrderResponse> getOrders(Pageable pageable) {
        Page<OrderEntity> page = orderEntityRepository.findAll(pageable);
        List<OrderResponse> content = page.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    public Page<OrderResponse> getOrders(Pageable pageable, String q, LocalDate fromDate, LocalDate toDate) {
        boolean noFilters = (q == null || q.isBlank()) && fromDate == null && toDate == null;
        Page<OrderEntity> page;
        if (noFilters) {
            page = orderEntityRepository.findAll(pageable);
        } else {
            // Avoid Postgres "could not determine data type of parameter" for NULL timestamps
            LocalDateTime from = fromDate == null
                    ? LocalDateTime.of(1970, 1, 1, 0, 0)
                    : fromDate.atStartOfDay();
            LocalDateTime to = toDate == null
                    ? LocalDateTime.of(2999, 12, 31, 23, 59, 59)
                    : toDate.atTime(23, 59, 59);
            String query = (q == null || q.isBlank()) ? "" : q.trim();
            page = orderEntityRepository.searchOrders(query, from, to, pageable);
        }
        List<OrderResponse> content = page.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }
}
