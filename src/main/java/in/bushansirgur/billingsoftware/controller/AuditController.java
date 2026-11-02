package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.entity.AuditLogEntity;
import in.bushansirgur.billingsoftware.io.AuditAction;
import in.bushansirgur.billingsoftware.io.UserResponse;
import in.bushansirgur.billingsoftware.service.AuditService;
import in.bushansirgur.billingsoftware.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final UserService userService;

    @GetMapping("/operators")
    public List<UserResponse> operators() {
        return userService.readUsers();
    }

    @GetMapping
    public List<AuditLogEntity> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String operatorCode,
            @RequestParam(required = false) AuditAction action) {
        return auditService.search(from, to, operatorCode, action);
    }
}
