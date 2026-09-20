package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.LicenseStatusResponse;
import in.bushansirgur.billingsoftware.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping("/status")
    public LicenseStatusResponse status() {
        return licenseService.getStatus();
    }
}
