package in.bushansirgur.billingsoftware.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP client for the ErpNet.FP service on the Windows till.
 * The fiscal cable stays on that machine. A Linux server sets erpnet.fp.url
 * to the till address, for example http://192.168.1.20:8001.
 */
@Component
public class ErpNetFpClient {

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public ErpNetFpClient(@Value("${erpnet.fp.url:http://127.0.0.1:8001}") String baseUrl,
                          ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder().baseUrl(this.baseUrl).requestFactory(factory).build();
    }

    public JsonNode printers() {
        return get("/printers");
    }

    public JsonNode status(String printerId) {
        return get("/printers/" + printerId + "/status");
    }

    public JsonNode printReceipt(String printerId, Map<String, Object> body) {
        return post("/printers/" + printerId + "/receipt", body);
    }

    public JsonNode printReversal(String printerId, Map<String, Object> body) {
        return post("/printers/" + printerId + "/reversalreceipt", body);
    }

    public JsonNode xReport(String printerId) {
        return post("/printers/" + printerId + "/xreport", Map.of());
    }

    public JsonNode zReport(String printerId) {
        return post("/printers/" + printerId + "/zreport", Map.of());
    }

    public String baseUrl() {
        return baseUrl;
    }

    private JsonNode get(String path) {
        try {
            String raw = http.get().uri(path).retrieve().body(String.class);
            return read(raw);
        } catch (RestClientResponseException ex) {
            throw unreachableOrMessage(ex);
        } catch (ResourceAccessException ex) {
            throw offline();
        }
    }

    private JsonNode post(String path, Object body) {
        try {
            String raw = http.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode node = read(raw);
            if (!isOk(node)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, errorText(node));
            }
            return node;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw unreachableOrMessage(ex);
        } catch (ResourceAccessException ex) {
            throw offline();
        }
    }

    private JsonNode read(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ErpNet.FP върна нечетим отговор");
        }
    }

    static boolean isOk(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        JsonNode ok = node.get("ok");
        if (ok == null || ok.isNull()) {
            return true;
        }
        if (ok.isBoolean()) {
            return ok.asBoolean();
        }
        return "true".equalsIgnoreCase(ok.asText());
    }

    static String errorText(JsonNode node) {
        if (node == null) {
            return "Фискалното устройство отказа бона";
        }
        StringBuilder text = new StringBuilder();
        JsonNode messages = node.get("messages");
        if (messages != null && messages.isArray()) {
            for (JsonNode message : messages) {
                if (!"error".equalsIgnoreCase(message.path("type").asText(""))) {
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                String code = message.path("code").asText("");
                if (!code.isBlank()) {
                    text.append(code).append(": ");
                }
                text.append(message.path("text").asText(""));
            }
        }
        if (text.isEmpty()) {
            return "Фискалното устройство отказа бона";
        }
        return text.toString();
    }

    private ResponseStatusException unreachableOrMessage(RestClientResponseException ex) {
        try {
            JsonNode node = read(ex.getResponseBodyAsString());
            String text = errorText(node);
            if (!"Фискалното устройство отказа бона".equals(text)) {
                return new ResponseStatusException(HttpStatus.BAD_GATEWAY, text);
            }
        } catch (ResponseStatusException ignored) {
            // body was not JSON
        }
        return offline();
    }

    private ResponseStatusException offline() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "ErpNet.FP не отговаря на " + baseUrl);
    }
}
