package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.OrderRequest;
import in.bushansirgur.billingsoftware.io.OrderResponse;
import in.bushansirgur.billingsoftware.service.OrderService;
import in.bushansirgur.billingsoftware.service.OrderArchiverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderArchiverService orderArchiverService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{orderId}")
    public void deleteOrder(@PathVariable String orderId) {
        orderService.deleteOrder(orderId);
    }

    @GetMapping("/latest")
    public List<OrderResponse> getLatestOrders() {
        return orderService.getLatestOrders();
    }

    @GetMapping
    public Page<OrderResponse> getOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate dateTo
    ) {
        return orderService.getOrders(pageable, q, dateFrom, dateTo);
    }

    @PostMapping("/archive/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String runArchiveNow() {
        int count = orderArchiverService.archiveOldOrders();
        return "Archived and purged orders: " + count;
    }
}









