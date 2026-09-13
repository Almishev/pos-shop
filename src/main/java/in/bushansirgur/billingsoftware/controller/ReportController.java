package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.ArchiveDestination;
import in.bushansirgur.billingsoftware.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportExportService reportExportService;

    @PostMapping("/export")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String exportOrders(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                               @RequestParam(defaultValue = "local") String destination) {
        ArchiveDestination dest = ArchiveDestination.from(destination);
        String location = reportExportService.exportOrdersCsv(dateFrom, dateTo, dest);
        return "Report generated: " + location;
    }

    @GetMapping("/cashiers")
    public List<in.bushansirgur.billingsoftware.io.CashierSummaryResponse> cashierSummaries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return reportExportService.getCashierSummaries(dateFrom, dateTo);
    }
}
