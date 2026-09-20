package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.io.ArchiveDestination;
import in.bushansirgur.billingsoftware.io.BackupFileInfo;
import in.bushansirgur.billingsoftware.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupServiceImpl implements BackupService {

    private static final Pattern JDBC_URL = Pattern.compile(
            "^jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)"
    );
    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^backup_\\d{8}_\\d{6}\\.sql\\.gz$"
    );
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final S3Client s3Client;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${backup.localDir:./archives/db-backups}")
    private String localDir;

    @Value("${backup.retentionDays:7}")
    private int retentionDays;

    @Value("${backup.s3.bucket:pos-reports-supermarket}")
    private String s3Bucket;

    @Value("${backup.s3.prefix:db-backups}")
    private String s3Prefix;

    @Value("${backup.enabled:true}")
    private boolean backupEnabled;

    @Value("${backup.schedule.enabled:true}")
    private boolean scheduleEnabled;

    /** Hour of daily scheduled backup (matches default cron 03:00). */
    @Value("${backup.schedule.hour:3}")
    private int scheduleHour;

    @Override
    public String createBackup(ArchiveDestination destination) {
        if (!backupEnabled) {
            throw new IllegalStateException("Database backup is disabled (backup.enabled=false).");
        }

        String timestamp = LocalDateTime.now().format(FILE_TS);
        String filename = "backup_" + timestamp + ".sql.gz";
        Path tempSql = null;
        Path gzPath = null;

        try {
            Path base = Path.of(localDir).toAbsolutePath().normalize();
            Files.createDirectories(base);

            tempSql = Files.createTempFile(base, "pgdump-", ".sql");
            runPgDump(tempSql);

            gzPath = base.resolve(filename);
            gzipFile(tempSql, gzPath);
            Files.deleteIfExists(tempSql);
            tempSql = null;

            if (destination == ArchiveDestination.S3) {
                String key = s3Prefix + "/" + filename;
                try {
                    byte[] bytes = Files.readAllBytes(gzPath);
                    s3Client.putObject(PutObjectRequest.builder()
                                    .bucket(s3Bucket)
                                    .key(key)
                                    .contentType("application/gzip")
                                    .build(),
                            RequestBody.fromBytes(bytes));
                    log.info("Database backup uploaded to s3://{}/{}", s3Bucket, key);
                    // Keep local copy as well for download list / USB copy
                    pruneOldLocalBackups();
                    return "s3://" + s3Bucket + "/" + key;
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Failed to upload database backup to AWS S3. Check credentials and bucket config.", e);
                }
            }

            log.info("Database backup written to {}", gzPath);
            pruneOldLocalBackups();
            return gzPath.toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create database backup: " + e.getMessage(), e);
        } finally {
            if (tempSql != null) {
                try {
                    Files.deleteIfExists(tempSql);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public List<BackupFileInfo> listLocalBackups() {
        Path base = Path.of(localDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        List<BackupFileInfo> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> SAFE_FILENAME.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(this::lastModifiedInstant).reversed())
                    .forEach(p -> {
                        try {
                            FileTime ft = Files.getLastModifiedTime(p);
                            String created = LocalDateTime.ofInstant(ft.toInstant(), ZoneId.systemDefault())
                                    .format(DISPLAY_TS);
                            result.add(BackupFileInfo.builder()
                                    .filename(p.getFileName().toString())
                                    .sizeBytes(Files.size(p))
                                    .createdAt(created)
                                    .build());
                        } catch (IOException e) {
                            log.warn("Skipping backup file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list local backups", e);
        }
        return result;
    }

    @Override
    public Path resolveLocalBackup(String filename) {
        if (filename == null || !SAFE_FILENAME.matcher(filename).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup filename");
        }
        Path base = Path.of(localDir).toAbsolutePath().normalize();
        Path file = base.resolve(filename).normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup file not found");
        }
        return file;
    }

    @Override
    public void pruneOldLocalBackups() {
        if (retentionDays <= 0) {
            return;
        }
        Path base = Path.of(localDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(base)) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> SAFE_FILENAME.matcher(p.getFileName().toString()).matches())
                    .filter(p -> lastModifiedInstant(p).isBefore(cutoff))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            log.info("Pruned old database backup {}", p.getFileName());
                        } catch (IOException e) {
                            log.warn("Failed to prune {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to prune old backups: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${backup.schedule.cron:0 0 3 * * *}")
    public void scheduledBackup() {
        if (!backupEnabled || !scheduleEnabled) {
            return;
        }
        try {
            String location = createBackup(ArchiveDestination.LOCAL);
            log.info("Scheduled database backup completed: {}", location);
        } catch (Exception e) {
            log.error("Scheduled database backup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * If the PC/backend was off at the nightly slot, run a local backup on next startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReadyCatchUpBackup() {
        if (!backupEnabled || !scheduleEnabled) {
            return;
        }
        Thread t = new Thread(this::catchUpMissedBackup, "db-backup-catchup");
        t.setDaemon(true);
        t.start();
    }

    private void catchUpMissedBackup() {
        try {
            // Brief delay so datasource / postgres are fully ready after boot
            Thread.sleep(15_000);
            if (!isBackupMissingSinceLastSchedule()) {
                log.info("Startup backup catch-up skipped — recent backup already exists");
                return;
            }
            String location = createBackup(ArchiveDestination.LOCAL);
            log.info("Startup catch-up database backup completed (missed nightly slot): {}", location);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Startup backup catch-up interrupted");
        } catch (Exception e) {
            log.error("Startup catch-up database backup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * True when there is no local backup at or after the last expected nightly schedule time.
     */
    private boolean isBackupMissingSinceLastSchedule() {
        Instant lastExpected = lastExpectedScheduleInstant();
        Optional<Instant> newest = findNewestBackupInstant();
        if (newest.isEmpty()) {
            return true;
        }
        return newest.get().isBefore(lastExpected);
    }

    private Instant lastExpectedScheduleInstant() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        int hour = Math.max(0, Math.min(23, scheduleHour));
        LocalTime slot = LocalTime.of(hour, 0);
        LocalDateTime candidate = now.toLocalDate().atTime(slot);
        if (now.isBefore(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate.atZone(zone).toInstant();
    }

    private Optional<Instant> findNewestBackupInstant() {
        Path base = Path.of(localDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(base)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(base)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> SAFE_FILENAME.matcher(p.getFileName().toString()).matches())
                    .map(this::lastModifiedInstant)
                    .max(Comparator.naturalOrder());
        } catch (IOException e) {
            log.warn("Could not scan backups for catch-up: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void runPgDump(Path outputSql) throws IOException, InterruptedException {
        DbTarget db = parseJdbcUrl(datasourceUrl);

        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "-h", db.host(),
                "-p", db.port(),
                "-U", datasourceUsername,
                "-d", db.database(),
                "-F", "p",
                "--no-owner",
                "--no-acl",
                "-f", outputSql.toAbsolutePath().toString()
        );
        pb.environment().put("PGPASSWORD", datasourcePassword == null ? "" : datasourcePassword);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        boolean finished = process.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("pg_dump timed out after 30 minutes");
        }
        int code = process.exitValue();
        if (code != 0) {
            throw new IllegalStateException("pg_dump failed (exit " + code + "): " + output);
        }
    }

    private void gzipFile(Path source, Path target) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
             OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
            in.transferTo(out);
        }
    }

    private DbTarget parseJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("spring.datasource.url is not configured");
        }
        Matcher m = JDBC_URL.matcher(url.trim());
        if (!m.find()) {
            throw new IllegalStateException("Unsupported JDBC URL for backup: " + url);
        }
        String host = m.group(1);
        String port = m.group(2) != null ? m.group(2) : "5432";
        String database = m.group(3);
        int slash = database.indexOf('/');
        if (slash >= 0) {
            database = database.substring(0, slash);
        }
        return new DbTarget(host, port, database);
    }

    private Instant lastModifiedInstant(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private record DbTarget(String host, String port, String database) {}
}
