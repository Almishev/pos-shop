package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.io.ArchiveDestination;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.repository.UserRepository;
import in.bushansirgur.billingsoftware.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExportServiceImpl implements ReportExportService {

    private final OrderEntityRepository orderRepository;
    private final UserRepository userRepository;
    private final S3Client s3Client;

    @Value("${reports.s3.bucket}")
    private String reportsBucket;

    @Value("${reports.s3.prefix:reports}")
    private String reportsPrefix;

    @Value("${reports.export.localDir:./archives/order-exports}")
    private String localDir;

    @Override
    public String exportOrdersCsv(LocalDate from, LocalDate to, ArchiveDestination destination) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);
        List<OrderEntity> orders = orderRepository.findAllByCreatedAtBetweenOrderByCreatedAtAsc(fromDt, toDt);
        String csv = toCsv(orders);

        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
        byte[] csvWithBom = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, csvWithBom, 0, bom.length);
        System.arraycopy(csvBytes, 0, csvWithBom, bom.length, csvBytes.length);

        String fileName = String.format("orders-%s_to_%s.csv",
                from.format(DateTimeFormatter.ISO_DATE),
                to.format(DateTimeFormatter.ISO_DATE));

        if (destination == ArchiveDestination.S3) {
            String key = reportsPrefix + "/" + fileName;
            try {
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(reportsBucket)
                                .key(key)
                                .contentType("text/csv; charset=utf-8")
                                .build(),
                        RequestBody.fromBytes(csvWithBom));
                log.info("Exported orders CSV to s3://{}/{}", reportsBucket, key);
                return "s3://" + reportsBucket + "/" + key;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to export CSV to AWS S3. Check credentials and bucket config.", e);
            }
        }

        try {
            Path base = Path.of(localDir).toAbsolutePath().normalize();
            Files.createDirectories(base);
            Path file = base.resolve(fileName);
            Files.write(file, csvWithBom);
            log.info("Exported orders CSV to {}", file);
            return file.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to export CSV to local disk", e);
        }
    }

    private String toCsv(List<OrderEntity> orders) {
        StringBuilder sb = new StringBuilder();
        sb.append("orderId,createdAt,customerName,phoneNumber,grandTotal,paymentMethod\n");
        for (OrderEntity o : orders) {
            sb.append(s(o.getOrderId())).append(',')
                    .append(s(String.valueOf(o.getCreatedAt()))).append(',')
                    .append(s(o.getCustomerName())).append(',')
                    .append(s(o.getPhoneNumber())).append(',')
                    .append(o.getGrandTotal() == null ? "" : o.getGrandTotal()).append(',')
                    .append(s(String.valueOf(o.getPaymentMethod())))
                    .append('\n');
        }
        return sb.toString();
    }

    private String s(String v) {
        if (v == null) return "";
        String escaped = v.replace("\"", "'");
        if (escaped.contains(",")) {
            return '"' + escaped + '"';
        }
        return escaped;
    }

    @Override
    public java.util.List<in.bushansirgur.billingsoftware.io.CashierSummaryResponse> getCashierSummaries(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);
        List<Object[]> rows = orderRepository.summarizeByCashier(fromDt, toDt);
        return rows.stream().map(r -> {
            String cashierEmail = (String) r[0];
            Long count = ((Number) r[1]).longValue();
            Double total = ((Number) r[2]).doubleValue();

            String displayName = cashierEmail;
            if (cashierEmail != null) {
                try {
                    var user = userRepository.findByEmail(cashierEmail).orElse(null);
                    if (user != null && user.getName() != null && !user.getName().isBlank()) {
                        displayName = user.getName();
                    }
                } catch (Exception ignored) {
                }
            }

            return in.bushansirgur.billingsoftware.io.CashierSummaryResponse.builder()
                    .cashierUsername(displayName)
                    .totalOrders(count)
                    .totalAmount(total)
                    .build();
        }).collect(Collectors.toList());
    }
}
