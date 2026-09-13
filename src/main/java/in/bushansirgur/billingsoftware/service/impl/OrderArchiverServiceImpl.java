package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.io.ArchiveDestination;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.service.OrderArchiverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
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
    @Value("${orders.archive.localDir:./archives/orders}")
    private String localDir;
    @Value("${orders.archive.schedule.destination:local}")
    private String scheduleDestination;

    @Override
    public int archiveOldOrders() {
        return archiveOldOrders(ArchiveDestination.from(scheduleDestination));
    }

    @Override
    public int archiveOldOrders(ArchiveDestination destination) {
        if (!enabled) return 0;
        LocalDate cutoff = LocalDate.now().minusMonths(retentionMonths).withDayOfMonth(1);
        YearMonth targetMonth = YearMonth.from(cutoff.minusMonths(1));
        return exportMonth(targetMonth.getYear(), targetMonth.getMonthValue(), destination);
    }

    @Override
    public int archiveOrdersBefore(LocalDate cutoffDate, ArchiveDestination destination) {
        if (!enabled) return 0;

        LocalDateTime cutoffDateTime = cutoffDate.atStartOfDay();
        List<OrderEntity> orders = orderRepository.findAllByCreatedAtBeforeOrderByCreatedAtAsc(cutoffDateTime);

        if (orders.isEmpty()) return 0;

        Map<YearMonth, List<OrderEntity>> ordersByMonth = orders.stream()
                .collect(Collectors.groupingBy(o -> YearMonth.from(o.getCreatedAt().toLocalDate())));

        int totalArchived = 0;
        for (Map.Entry<YearMonth, List<OrderEntity>> entry : ordersByMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            List<OrderEntity> monthOrders = entry.getValue();
            String relativeKey = String.format("%s/year=%04d/month=%02d/orders-%s.jsonl.gz",
                    prefix, ym.getYear(), ym.getMonthValue(),
                    ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            byte[] gz = toJsonlGzip(monthOrders);
            store(gz, relativeKey, destination);
            monthOrders.forEach(orderRepository::delete);
            totalArchived += monthOrders.size();
        }

        return totalArchived;
    }

    @Override
    public int exportMonth(int year, int month, ArchiveDestination destination) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);

        List<OrderEntity> orders = orderRepository.findAllByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        if (orders.isEmpty()) return 0;

        String relativeKey = String.format("%s/year=%04d/month=%02d/orders-%s.jsonl.gz",
                prefix, year, month, ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        byte[] gz = toJsonlGzip(orders);
        store(gz, relativeKey, destination);
        orders.forEach(orderRepository::delete);
        return orders.size();
    }

    private void store(byte[] gz, String relativeKey, ArchiveDestination destination) {
        if (destination == ArchiveDestination.S3) {
            try {
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(relativeKey)
                                .contentType("application/gzip")
                                .build(),
                        RequestBody.fromBytes(gz));
                log.info("Archived orders to s3://{}/{}", bucket, relativeKey);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to archive orders to AWS S3. Check credentials and bucket config.", e);
            }
            return;
        }
        try {
            Path base = Path.of(localDir).toAbsolutePath().normalize();
            // relativeKey starts with prefix/; strip to year=/month=/file under localDir
            String withoutPrefix = relativeKey.startsWith(prefix + "/")
                    ? relativeKey.substring(prefix.length() + 1)
                    : relativeKey;
            Path file = base.resolve(withoutPrefix).normalize();
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                String name = file.getFileName().toString().replace(".jsonl.gz",
                        "-" + System.currentTimeMillis() + ".jsonl.gz");
                file = file.getParent().resolve(name);
            }
            Files.write(file, gz);
            log.info("Archived orders to {}", file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to archive orders to local disk", e);
        }
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

    private String nullSafe(String v) {
        return v == null ? "" : v.replace("\"", "\\\"");
    }

    @Scheduled(cron = "${orders.archive.schedule.cron:0 0 3 1 * *}")
    public void scheduledArchive() {
        archiveOldOrders();
    }
}
