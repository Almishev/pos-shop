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
@RequestMapping("/cash-drawer")
@RequiredArgsConstructor
public class CashDrawerSessionController {
    
    private final CashDrawerSessionService cashDrawerSessionService;
    
    @PostMapping("/start")
    public ResponseEntity<CashDrawerSessionResponse> startWorkDay(@RequestBody CashDrawerSessionRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            request.setCashierUsername(authentication.getName());
        }
        
        CashDrawerSessionResponse response = cashDrawerSessionService.startWorkDay(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/end/{sessionId}")
    public ResponseEntity<CashDrawerSessionResponse> endWorkDay(
            @PathVariable Long sessionId, 
            @RequestBody CashDrawerSessionRequest request) {
        
        CashDrawerSessionResponse response = cashDrawerSessionService.endWorkDay(sessionId, request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/active")
    public ResponseEntity<CashDrawerSessionResponse> getActiveSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            CashDrawerSessionResponse response = cashDrawerSessionService.getActiveSession(
                    authentication.getName(), LocalDate.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
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
    
    @GetMapping("/active-sessions")
    public ResponseEntity<List<CashDrawerSessionResponse>> getActiveSessions() {
        List<CashDrawerSessionResponse> sessions = cashDrawerSessionService.getActiveSessions();
        return ResponseEntity.ok(sessions);
    }
    
    @GetMapping("/sessions/{date}")
    public ResponseEntity<List<CashDrawerSessionResponse>> getSessionsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<CashDrawerSessionResponse> sessions = cashDrawerSessionService.getSessionsByDate(date);
        return ResponseEntity.ok(sessions);
    }
    
    @PostMapping("/force-end/{sessionId}")
    public ResponseEntity<CashDrawerSessionResponse> forceEndSession(@PathVariable Long sessionId) {
        CashDrawerSessionResponse response = cashDrawerSessionService.forceEndSession(sessionId);
        return ResponseEntity.ok(response);
    }
}
