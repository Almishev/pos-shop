package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.io.CashDrawerSessionRequest;
import in.bushansirgur.billingsoftware.io.CashDrawerSessionResponse;
import in.bushansirgur.billingsoftware.service.CashDrawerSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/v1.0/cash-drawer", "/cash-drawer"})
@RequiredArgsConstructor
// CORS is handled globally in SecurityConfig - no need for @CrossOrigin here
public class CashDrawerSessionController {
    
    private final CashDrawerSessionService cashDrawerSessionService;
    
    // Започване на работен ден
    @PostMapping("/start")
    public ResponseEntity<CashDrawerSessionResponse> startWorkDay(@RequestBody CashDrawerSessionRequest request) {
        System.out.println("=== CashDrawerSessionController.startWorkDay called ===");
        System.out.println("Payload startAmount=" + request.getStartAmount() + 
                ", deviceSerialNumber=" + request.getDeviceSerialNumber() + 
                ", registerId=" + request.getRegisterId());
        // Автоматично попълване на касиера от authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            request.setCashierUsername(authentication.getName());
            System.out.println("Authenticated user=" + authentication.getName());
        }
        
        CashDrawerSessionResponse response = cashDrawerSessionService.startWorkDay(request);
        return ResponseEntity.ok(response);
    }
    
    // Приключване на работен ден
    @PostMapping("/end/{sessionId}")
    public ResponseEntity<CashDrawerSessionResponse> endWorkDay(
            @PathVariable Long sessionId, 
            @RequestBody CashDrawerSessionRequest request) {
        
        CashDrawerSessionResponse response = cashDrawerSessionService.endWorkDay(sessionId, request);
        return ResponseEntity.ok(response);
    }
    
    // Получаване на активна сесия за текущия касиер
    @GetMapping("/active")
    public ResponseEntity<CashDrawerSessionResponse> getActiveSession() {
        System.out.println("=== getActiveSession called ===");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            System.out.println("No authentication found");
            return ResponseEntity.notFound().build();
        }
        
        System.out.println("Authenticated user: " + authentication.getName());
        try {
            CashDrawerSessionResponse response = cashDrawerSessionService.getActiveSession(
                    authentication.getName(), LocalDate.now());
            System.out.println("Active session found: " + response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("No active session found for user: " + authentication.getName() + ", error: " + e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Получаване на всички сесии за текущия касиер
    @GetMapping("/my-sessions")
    public ResponseEntity<List<CashDrawerSessionResponse>> getMySessions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.notFound().build();
        }
        
        List<CashDrawerSessionResponse> sessions = cashDrawerSessionService.getSessionsByCashier(
                authentication.getName());
        return ResponseEntity.ok(sessions);
    }
    
    // Получаване на всички активни сесии (само за админи)
    @GetMapping("/active-sessions")
    public ResponseEntity<List<CashDrawerSessionResponse>> getActiveSessions() {
        List<CashDrawerSessionResponse> sessions = cashDrawerSessionService.getActiveSessions();
        return ResponseEntity.ok(sessions);
    }
    
    // Получаване на сесии за дата (само за админи)
    @GetMapping("/sessions/{date}")
    public ResponseEntity<List<CashDrawerSessionResponse>> getSessionsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<CashDrawerSessionResponse> sessions = cashDrawerSessionService.getSessionsByDate(date);
        return ResponseEntity.ok(sessions);
    }
    
    // Debug endpoint за всички сесии
    @GetMapping("/debug/all-sessions")
    public ResponseEntity<List<CashDrawerSessionResponse>> getAllSessions() {
        System.out.println("=== getAllSessions called ===");
        List<CashDrawerSessionResponse> sessions = cashDrawerSessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }
    
    // Принудително приключване на сесия (само за админи)
    @PostMapping("/force-end/{sessionId}")
    public ResponseEntity<CashDrawerSessionResponse> forceEndSession(@PathVariable Long sessionId) {
        System.out.println("=== forceEndSession called for sessionId: " + sessionId + " ===");
        CashDrawerSessionResponse response = cashDrawerSessionService.forceEndSession(sessionId);
        return ResponseEntity.ok(response);
    }
}
