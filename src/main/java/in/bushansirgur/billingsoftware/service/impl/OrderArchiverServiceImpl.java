package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.service.OrderArchiverService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class OrderArchiverServiceImpl implements OrderArchiverService {

    private final OrderEntityRepository orderRepository;
    private final S3Client s3Client;

    @Value("${orders.archive.enabled:true}")
    private boolean enabled;
    @Value("${orders.archive.retentionMonths:6}")
    private int retentionMonths;
    @Value("${orders.archive.bucket}")
    private String bucket;
    @Value("${orders.archive.prefix:orders}")
    private String prefix;

    @Override
    public int archiveOldOrders() {
        if (!enabled) return 0;
        LocalDate cutoff = LocalDate.now().minusMonths(retentionMonths).withDayOfMonth(1);
        YearMonth targetMonth = YearMonth.from(cutoff.minusMonths(1));
        return exportMonth(targetMonth.getYear(), targetMonth.getMonthValue());
    }

    @Override
    public int exportMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().atTime(23,59,59);

        // naive fetch; for large data, stream in batches
        List<OrderEntity> orders = orderRepository.findAllByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        if (orders.isEmpty()) return 0;

        String key = String.format("%s/year=%04d/month=%02d/orders-%s.jsonl.gz", prefix, year, month, ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));

        byte[] gz = toJsonlGzip(orders);
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/gzip").build(), RequestBody.fromBytes(gz));

        // Optional: purge after successful archive
        orders.forEach(orderRepository::delete);
        return orders.size();
    }

    private byte[] toJsonlGzip(List<OrderEntity> orders) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                for (OrderEntity o : orders) {
                    String line = toJsonLine(o);
                    gzip.write(line.getBytes(StandardCharsets.UTF_8));
                    gzip.write('\n');
                }
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to gzip orders", e);
        }
    }

    private String toJsonLine(OrderEntity o) {
        // minimal JSON; for production use ObjectMapper
        StringBuilder sb = new StringBuilder();
        sb.append('{')
          .append("\"orderId\":\"").append(o.getOrderId()).append("\",")
          .append("\"customerName\":\"").append(nullSafe(o.getCustomerName())).append("\",")
          .append("\"phoneNumber\":\"").append(nullSafe(o.getPhoneNumber())).append("\",")
          .append("\"subtotal\":").append(o.getSubtotal()).append(',')
          .append("\"tax\":").append(o.getTax()).append(',')
          .append("\"grandTotal\":").append(o.getGrandTotal()).append(',')
          .append("\"paymentMethod\":\"").append(String.valueOf(o.getPaymentMethod())).append("\",")
          .append("\"createdAt\":\"").append(String.valueOf(o.getCreatedAt())).append("\"")
          .append('}');
        return sb.toString();
    }

    private String nullSafe(String v) { return v == null ? "" : v.replace("\"", "\\\""); }

    // monthly at 03:00 (configurable)
    @Scheduled(cron = "${orders.archive.schedule.cron:0 0 3 1 * *}")
    public void scheduledArchive() {
        archiveOldOrders();
    }
}


