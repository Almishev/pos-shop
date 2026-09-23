package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.OrderRequest;
import in.bushansirgur.billingsoftware.io.OrderResponse;
import in.bushansirgur.billingsoftware.io.OrderRefundRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request);

    void deleteOrder(String orderId);

    OrderResponse getOrderById(String orderId);

    List<OrderResponse> getLatestOrders();

    Double sumSalesByDate(LocalDate date);

    Long countByOrderDate(LocalDate date);

    Double sumTaxByDate(LocalDate date);

    List<OrderResponse> findRecentOrders();

    /**
     * Returns paginated list of orders sorted according to pageable.
     */
    Page<OrderResponse> getOrders(Pageable pageable);

    Page<OrderResponse> getOrders(Pageable pageable, String q, LocalDate fromDate, LocalDate toDate);

    OrderResponse refundOrder(OrderRefundRequest request);
}
