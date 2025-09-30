package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.service.ReportExportService;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl implements ReportExportService {

    private final OrderEntityRepository orderRepository;
    private final S3Client s3Client;

    @Value("${reports.s3.bucket}")
    private String reportsBucket;

    @Value("${reports.s3.prefix:reports}")
    private String reportsPrefix;

    @Value("${reports.daily.enabled:true}")
    private boolean dailyEnabled;

    @Override
    public String exportOrdersCsv(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23,59,59);
        List<OrderEntity> orders = orderRepository.findAllByCreatedAtBetweenOrderByCreatedAtAsc(fromDt, toDt);
        String csv = toCsv(orders);
        String key = String.format("%s/orders-%s_to_%s.csv",
                reportsPrefix,
                from.format(DateTimeFormatter.ISO_DATE),
                to.format(DateTimeFormatter.ISO_DATE));
        s3Client.putObject(PutObjectRequest.builder().bucket(reportsBucket).key(key).contentType("text/csv").build(),
                RequestBody.fromBytes(csv.getBytes(StandardCharsets.UTF_8)));
        return key;
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
        LocalDateTime toDt = to.atTime(23,59,59);
        List<Object[]> rows = orderRepository.summarizeByCashier(fromDt, toDt);
        return rows.stream().map(r -> in.bushansirgur.billingsoftware.io.CashierSummaryResponse.builder()
                .cashierUsername((String) r[0])
                .totalOrders(((Number) r[1]).longValue())
                .totalAmount(((Number) r[2]).doubleValue())
                .build()).collect(Collectors.toList());
    }

    @Override
    public String generateDailyReport() {
        if (!dailyEnabled) return null;
        
        LocalDate yesterday = LocalDate.now().minusDays(1);
        return exportOrdersCsv(yesterday, yesterday);
    }

    // Daily report at midnight (00:00) for NAP compliance
    @Scheduled(cron = "${reports.daily.schedule.cron:0 0 0 * * *}")
    public void scheduledDailyReport() {
        try {
            String key = generateDailyReport();
            System.out.println("Daily report generated: " + key);
        } catch (Exception e) {
            System.err.println("Failed to generate daily report: " + e.getMessage());
        }
    }
}


