package in.bushansirgur.billingsoftware.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.bushansirgur.billingsoftware.io.LicenseStatusResponse;
import in.bushansirgur.billingsoftware.service.LicenseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class LicenseServiceImpl implements LicenseService {

    private static final String INACTIVE_MESSAGE =
            "Абонаментът не е активен. Свържете се с доставчика на софтуера.";
    private static final String OFFLINE_MESSAGE =
            "Няма връзка за проверка на лиценза и липсва валиден офлайн кеш. Опитайте отново по-късно.";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${license.enabled:false}")
    private boolean enabled;

    @Value("${license.checkUrl:}")
    private String checkUrl;

    @Value("${license.shopId:}")
    private String shopId;

    @Value("${license.key:}")
    private String licenseKey;

    @Value("${license.graceDays:7}")
    private int graceDays;

    @Value("${license.cacheFile:./archives/license-cache.json}")
    private String cacheFile;

    @Value("${license.timeoutSeconds:10}")
    private int timeoutSeconds;

    public LicenseServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void assertLicenseActive() {
        if (!enabled) {
            return;
        }
        LicenseStatusResponse status = getStatus();
        if (!status.isActive()) {
            String msg = status.getMessage() != null && !status.getMessage().isBlank()
                    ? status.getMessage()
                    : INACTIVE_MESSAGE;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }
    }

    @Override
    public LicenseStatusResponse getStatus() {
        if (!enabled) {
            return LicenseStatusResponse.builder()
                    .enabled(false)
                    .active(true)
                    .status("disabled_check")
                    .reason("license_check_disabled")
                    .source("disabled")
                    .message("License check is disabled")
                    .build();
        }

        if (isBlank(checkUrl) || isBlank(shopId) || isBlank(licenseKey)) {
            return LicenseStatusResponse.builder()
                    .enabled(true)
                    .active(false)
                    .reason("misconfigured")
                    .source("remote")
                    .message("Лицензът не е конфигуриран (LICENSE_CHECK_URL / SHOP_ID / KEY).")
                    .build();
        }

        try {
            LicenseStatusResponse remote = fetchRemote();
            if (remote.isActive()) {
                writeCache(remote);
            }
            return remote;
        } catch (Exception e) {
            log.warn("License remote check failed: {}", e.getMessage());
            LicenseStatusResponse cached = readCacheIfFresh();
            if (cached != null) {
                cached.setSource("cache");
                cached.setMessage("Използва се офлайн кеш на лиценза (grace period).");
                return cached;
            }
            return LicenseStatusResponse.builder()
                    .enabled(true)
                    .active(false)
                    .shopId(shopId)
                    .reason("offline_no_cache")
                    .source("remote")
                    .message(OFFLINE_MESSAGE)
                    .build();
        }
    }

    private LicenseStatusResponse fetchRemote() throws IOException, InterruptedException {
        String url = checkUrl.trim();
        String sep = url.contains("?") ? "&" : "?";
        String full = url + sep
                + "shop_id=" + URLEncoder.encode(shopId.trim(), StandardCharsets.UTF_8)
                + "&license_key=" + URLEncoder.encode(licenseKey.trim(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(full))
                .timeout(Duration.ofSeconds(Math.max(3, timeoutSeconds)))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("License endpoint HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        boolean ok = root.path("ok").asBoolean(false);
        boolean active = root.path("active").asBoolean(false);
        String status = text(root, "status");
        String reason = text(root, "reason");
        String validUntil = text(root, "valid_until");
        String remoteShopId = text(root, "shop_id");
        String shopName = text(root, "shop_name");

        boolean allowed = ok && active;
        return LicenseStatusResponse.builder()
                .enabled(true)
                .active(allowed)
                .status(status)
                .shopId(remoteShopId.isBlank() ? shopId : remoteShopId)
                .shopName(shopName)
                .validUntil(validUntil)
                .reason(reason.isBlank() ? (allowed ? "ok" : "inactive") : reason)
                .source("remote")
                .message(allowed ? "Лицензът е активен." : INACTIVE_MESSAGE)
                .build();
    }

    private void writeCache(LicenseStatusResponse status) {
        try {
            Path path = Path.of(cacheFile).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("checkedAt", Instant.now().toString());
            payload.put("active", status.isActive());
            payload.put("status", status.getStatus());
            payload.put("shopId", status.getShopId());
            payload.put("shopName", status.getShopName());
            payload.put("validUntil", status.getValidUntil());
            payload.put("reason", status.getReason());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), payload);
        } catch (Exception e) {
            log.warn("Failed to write license cache: {}", e.getMessage());
        }
    }

    private LicenseStatusResponse readCacheIfFresh() {
        try {
            Path path = Path.of(cacheFile).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(path.toFile());
            Instant checkedAt = Instant.parse(root.path("checkedAt").asText());
            if (checkedAt.isBefore(Instant.now().minus(Math.max(1, graceDays), ChronoUnit.DAYS))) {
                return null;
            }
            boolean active = root.path("active").asBoolean(false);
            if (!active) {
                return null;
            }
            return LicenseStatusResponse.builder()
                    .enabled(true)
                    .active(true)
                    .status(text(root, "status"))
                    .shopId(text(root, "shopId"))
                    .shopName(text(root, "shopName"))
                    .validUntil(text(root, "validUntil"))
                    .reason(text(root, "reason"))
                    .source("cache")
                    .build();
        } catch (Exception e) {
            log.warn("Failed to read license cache: {}", e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.path(field);
        return n.isMissingNode() || n.isNull() ? "" : n.asText("").trim();
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
