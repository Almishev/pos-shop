package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.Annex29TableResponse;
import in.bushansirgur.billingsoftware.service.Annex29ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/reports/annex29")
@RequiredArgsConstructor
public class Annex29Controller {

    private final Annex29ExportService annex29ExportService;

    @GetMapping
    public Annex29TableResponse table(
            @RequestParam String table,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String device,
            @RequestParam(required = false) String operatorCode) {
        return annex29ExportService.table(table, from, to, device, operatorCode);
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(
            @RequestParam String table,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String device,
            @RequestParam(required = false) String operatorCode) {
        Annex29TableResponse data = annex29ExportService.table(table, from, to, device, operatorCode);
        byte[] body = annex29ExportService.csv(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + table + ".csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }
}
