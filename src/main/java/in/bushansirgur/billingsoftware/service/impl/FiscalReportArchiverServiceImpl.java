package in.bushansirgur.billingsoftware.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.bushansirgur.billingsoftware.entity.FiscalReportEntity;
import in.bushansirgur.billingsoftware.io.ArchiveDestination;
import in.bushansirgur.billingsoftware.io.FiscalReportResponse;
import in.bushansirgur.billingsoftware.repository.FiscalReportRepository;
import in.bushansirgur.billingsoftware.service.FiscalReportArchiverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalReportArchiverServiceImpl implements FiscalReportArchiverService {

    private final FiscalReportRepository fiscalReportRepository;
    private final ObjectMapper objectMapper;
    private final S3Client s3Client;

    @Value("${reports.archive.enabled:true}")
    private boolean enabled;

    @Value("${reports.archive.retentionYears:5}")
    private int retentionYears;

    @Value("${reports.archive.localDir:./archives/fiscal-reports}")
    private String localDir;

    @Value("${reports.archive.s3.bucket:pos-reports-supermarket}")
    private String s3Bucket;

    @Value("${reports.archive.s3.prefix:fiscal-reports}")
    private String s3Prefix;

    @Value("${reports.archive.schedule.enabled:false}")
    private boolean scheduleEnabled;

    @Value("${reports.archive.schedule.destination:local}")
    private String scheduleDestination;

    @Override
    @Transactional
    public int archiveOldReports() {
        return archiveOldReports(ArchiveDestination.from(scheduleDestination));
    }

    @Override
    @Transactional
    public int archiveOldReports(ArchiveDestination destination) {
        if (!enabled) return 0;
        LocalDate cutoff = LocalDate.now().minusYears(retentionYears);
        return archiveReportsBefore(cutoff, destination);
    }

    @Override
    @Transactional
    public int archiveReportsBefore(LocalDate cutoffDate, ArchiveDestination destination) {
        if (!enabled) {
            log.info("Fiscal report archive is disabled");
            return 0;
        }
        if (cutoffDate == null) {
            throw new IllegalArgumentException("cutoffDate is required");
        }

        List<FiscalReportEntity> reports =
                fiscalReportRepository.findByReportDateBeforeOrderByReportDateAsc(cutoffDate);
        if (reports.isEmpty()) {
            return 0;
        }

        Map<YearMonth, List<FiscalReportEntity>> byMonth = reports.stream()
                .collect(Collectors.groupingBy(r -> YearMonth.from(r.getReportDate())));

        int total = 0;
        for (Map.Entry<YearMonth, List<FiscalReportEntity>> entry : byMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            List<FiscalReportEntity> monthReports = entry.getValue();
            String relative = String.format("year=%04d/month=%02d/reports-%s.jsonl.gz",
                    ym.getYear(), ym.getMonthValue(),
                    ym.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            try {
                byte[] gz = toJsonlGzip(monthReports);
                store(gz, relative, destination);
                fiscalReportRepository.deleteAll(monthReports);
                total += monthReports.size();
            } catch (IOException e) {
                throw new RuntimeException("Failed to archive fiscal reports for " + ym, e);
            }
        }
        return total;
    }

    private void store(byte[] gz, String relative, ArchiveDestination destination) throws IOException {
        if (destination == ArchiveDestination.S3) {
            String key = s3Prefix + "/" + relative;
            try {
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(s3Bucket)
                                .key(key)
                                .contentType("application/gzip")
                                .build(),
                        RequestBody.fromBytes(gz));
                log.info("Archived fiscal reports to s3://{}/{}", s3Bucket, key);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to archive fiscal reports to AWS S3. Check credentials and bucket config.", e);
            }
            return;
        }
        Path base = Path.of(localDir).toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path file = base.resolve(relative).normalize();
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) {
            String name = file.getFileName().toString().replace(".jsonl.gz",
                    "-" + System.currentTimeMillis() + ".jsonl.gz");
            file = file.getParent().resolve(name);
        }
        Files.write(file, gz);
        log.info("Archived fiscal reports to {}", file);
    }

    private byte[] toJsonlGzip(List<FiscalReportEntity> reports) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            for (FiscalReportEntity report : reports) {
                FiscalReportResponse dto = FiscalReportResponse.fromEntity(report);
                String line = objectMapper.writeValueAsString(dto) + "\n";
                gzip.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        return baos.toByteArray();
    }

    @Scheduled(cron = "${reports.archive.schedule.cron:0 0 4 1 * *}")
    public void scheduledArchive() {
        if (!enabled || !scheduleEnabled) return;
        try {
            int n = archiveOldReports();
            log.info("Scheduled fiscal report archive completed: {} reports", n);
        } catch (Exception ex) {
            log.error("Scheduled fiscal report archive failed: {}", ex.getMessage(), ex);
        }
    }
}
