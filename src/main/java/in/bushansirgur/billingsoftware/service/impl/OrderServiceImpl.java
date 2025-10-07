package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.entity.OrderItemEntity;
import in.bushansirgur.billingsoftware.io.*;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.service.InventoryService;
import in.bushansirgur.billingsoftware.service.OrderService;
import in.bushansirgur.billingsoftware.service.PosPaymentService;
import in.bushansirgur.billingsoftware.io.PosPaymentIO;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    
    private final OrderEntityRepository orderEntityRepository; 
    private final InventoryService inventoryService;
    private final PosPaymentService posPaymentService;

    @Override
    public OrderResponse createOrder(OrderRequest request) {
        OrderEntity newOrder = convertToOrderEntity(request);

        PaymentDetails paymentDetails = new PaymentDetails();
        paymentDetails.setStatus(newOrder.getPaymentMethod() == PaymentMethod.CASH ?
                PaymentDetails.PaymentStatus.COMPLETED : PaymentDetails.PaymentStatus.PENDING);
        // handle split amounts if provided
        if (newOrder.getPaymentMethod() == PaymentMethod.SPLIT) {
            paymentDetails.setCashAmount(request.getCashAmount());
            paymentDetails.setCardAmount(request.getCardAmount());
        }
        newOrder.setPaymentDetails(paymentDetails);
        
        List<OrderItemEntity> orderItems = request.getCartItems().stream()
                .map(this::convertToOrderItemEntity)
                .collect(Collectors.toList());
        newOrder.setItems(orderItems);

        // Set cashier username from authenticated principal
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                newOrder.setCashierUsername(auth.getName());
            }
        } catch (Exception ignored) {}
        
        newOrder = orderEntityRepository.save(newOrder);

        // Decrease inventory quantities based on items in the order
        try {
            for (OrderRequest.OrderItemRequest itemReq : request.getCartItems()) {
                inventoryService.processSaleTransaction(itemReq.getItemId(), itemReq.getQuantity(), newOrder.getOrderId());
            }
        } catch (Exception ex) {
            // Swallow inventory error to not block order creation, but log it
            // In production, replace with proper logger
            System.err.println("Inventory update failed for order " + newOrder.getOrderId() + ": " + ex.getMessage());
        }

        return convertToResponse(newOrder);
    }

    @Override
    @Transactional
    public OrderResponse refundOrder(in.bushansirgur.billingsoftware.io.OrderRefundRequest request) {
        OrderEntity original = orderEntityRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Restock items (partial or full)
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (in.bushansirgur.billingsoftware.io.OrderRefundRequest.RefundItem ri : request.getItems()) {
                try {
                    inventoryService.processPurchaseTransaction(ri.getItemId(), ri.getQuantity(), "REF-" + original.getOrderId());
                } catch (Exception ex) {
                    System.err.println("Restock failed for item " + ri.getItemId() + ": " + ex.getMessage());
                }
            }
        } else {
            // Full refund: restock all
            original.getItems().forEach(oi -> {
                try {
                    inventoryService.processPurchaseTransaction(oi.getItemId(), oi.getQuantity(), "REF-" + original.getOrderId());
                } catch (Exception ex) {
                    System.err.println("Restock failed for item " + oi.getItemId() + ": " + ex.getMessage());
                }
            });
        }

        // POS refund for card payments (mock/provider controlled in service)
        if ("CARD".equalsIgnoreCase(request.getRefundMethod())) {
            try {
                String originalTxnId = original.getPaymentDetails() != null ? original.getPaymentDetails().getPosTransactionId() : null;
                Double amount = request.getRefundAmount() != null ? request.getRefundAmount() : original.getGrandTotal();
                if (originalTxnId != null && amount != null && amount > 0) {
                    PosPaymentIO.RefundResponse rr = posPaymentService.refund(PosPaymentIO.RefundRequest.builder()
                            .originalTransactionId(originalTxnId)
                            .amount(java.math.BigDecimal.valueOf(amount))
                            .currency("BGN")
                            .reason(request.getReason())
                            .build());
                    if (!"APPROVED".equalsIgnoreCase(rr.getStatus())) {
                        throw new RuntimeException("Card refund declined by provider");
                    }
                    // store refund txn id on original order
                    if (original.getPaymentDetails() != null) {
                        original.getPaymentDetails().setPosRefundTransactionId(rr.getRefundTransactionId());
                        orderEntityRepository.save(original);
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException("POS refund failed: " + ex.getMessage(), ex);
            }
        }

        // Mark original as refunded to reflect in UI
        original.setStatus(in.bushansirgur.billingsoftware.io.OrderStatus.REFUNDED);
        orderEntityRepository.save(original);

        // Create refund items list (mirror quantities negative for readability)
        java.util.List<OrderItemEntity> refundItems;
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            java.util.Map<String, Integer> itemIdToQty = request.getItems().stream()
                    .collect(java.util.stream.Collectors.toMap(in.bushansirgur.billingsoftware.io.OrderRefundRequest.RefundItem::getItemId, in.bushansirgur.billingsoftware.io.OrderRefundRequest.RefundItem::getQuantity));
            refundItems = original.getItems().stream()
                    .filter(oi -> itemIdToQty.containsKey(oi.getItemId()))
                    .map(oi -> OrderItemEntity.builder()
                            .itemId(oi.getItemId())
                            .name(oi.getName())
                            .barcode(oi.getBarcode())
                            .price(oi.getPrice())
                            .quantity(-Math.abs(itemIdToQty.get(oi.getItemId())))
                            .build())
                    .collect(java.util.stream.Collectors.toList());
        } else {
            refundItems = original.getItems().stream()
                    .map(oi -> OrderItemEntity.builder()
                            .itemId(oi.getItemId())
                            .name(oi.getName())
                            .barcode(oi.getBarcode())
                            .price(oi.getPrice())
                            .quantity(-Math.abs(oi.getQuantity()))
                            .build())
                    .collect(java.util.stream.Collectors.toList());
        }

        // Create a refund order record (mirror) for audit
        OrderEntity refund = OrderEntity.builder()
                .customerName(original.getCustomerName())
                .phoneNumber(original.getPhoneNumber())
                .subtotal(-Math.abs(original.getSubtotal()))
                .tax(-Math.abs(original.getTax()))
                .grandTotal(-Math.abs(request.getRefundAmount() != null ? request.getRefundAmount() : original.getGrandTotal()))
                .paymentMethod(original.getPaymentMethod())
                .status(in.bushansirgur.billingsoftware.io.OrderStatus.REFUNDED)
                .originalOrderId(original.getOrderId())
                .cashierUsername(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName() : original.getCashierUsername())
                .build();

        refund.setItems(refundItems);
        refund = orderEntityRepository.save(refund);
        return convertToResponse(refund);
    }

    private OrderItemEntity convertToOrderItemEntity(OrderRequest.OrderItemRequest orderItemRequest) {
        return OrderItemEntity.builder()
                .itemId(orderItemRequest.getItemId())
                .name(orderItemRequest.getName())
                .barcode(orderItemRequest.getBarcode())
                .price(orderItemRequest.getPrice())
                .quantity(orderItemRequest.getQuantity())
                .build();
    }

    private OrderResponse convertToResponse(OrderEntity newOrder) {
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
                        .map(this::convertToItemResponse)
                        .collect(Collectors.toList()))
                .paymentDetails(newOrder.getPaymentDetails())
                .createdAt(newOrder.getCreatedAt())
                .build();
                
    }

    private OrderResponse.OrderItemResponse convertToItemResponse(OrderItemEntity orderItemEntity) {
        return OrderResponse.OrderItemResponse.builder()
                .itemId(orderItemEntity.getItemId())
                .name(orderItemEntity.getName())
                .barcode(orderItemEntity.getBarcode())
                .price(orderItemEntity.getPrice())
                .quantity(orderItemEntity.getQuantity())
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
    public void deleteOrder(String orderId) {
        OrderEntity existingOrder = orderEntityRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        orderEntityRepository.delete(existingOrder);
    }

    @Override
    public List<OrderResponse> getLatestOrders() {
        return orderEntityRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public OrderResponse verifyPayment(PaymentVerificationRequest request) {
        OrderEntity existingOrder = orderEntityRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!verifyRazorpaySignature(request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature())) {
            throw new RuntimeException("Payment verification failed");
        }

        PaymentDetails paymentDetails = existingOrder.getPaymentDetails();
        paymentDetails.setRazorpayOrderId(request.getRazorpayOrderId());
        paymentDetails.setRazorpayPaymentId(request.getRazorpayPaymentId());
        paymentDetails.setRazorpaySignature(request.getRazorpaySignature());
        paymentDetails.setStatus(PaymentDetails.PaymentStatus.COMPLETED);

        existingOrder = orderEntityRepository.save(existingOrder);
        return convertToResponse(existingOrder);

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
        Page<OrderEntity> page = noFilters
                ? orderEntityRepository.findAll(pageable)
                : orderEntityRepository.searchOrders(
                        (q == null || q.isBlank()) ? null : q,
                        fromDate == null ? null : fromDate.atStartOfDay(),
                        toDate == null ? null : toDate.atTime(23,59,59),
                        pageable
                );
        List<OrderResponse> content = page.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    private boolean verifyRazorpaySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        return true;
    }
}
