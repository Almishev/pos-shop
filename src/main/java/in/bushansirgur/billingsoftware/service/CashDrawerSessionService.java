package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.CashDrawerSessionRequest;
import in.bushansirgur.billingsoftware.io.CashDrawerSessionResponse;

import java.time.LocalDate;
import java.util.List;

public interface CashDrawerSessionService {
    
    // Започване на работен ден
    CashDrawerSessionResponse startWorkDay(CashDrawerSessionRequest request);
    
    // Приключване на работен ден
    CashDrawerSessionResponse endWorkDay(Long sessionId, CashDrawerSessionRequest request);
    
    // Получаване на активна сесия за касиер
    CashDrawerSessionResponse getActiveSession(String cashierUsername, LocalDate date);
    
    // Получаване на всички сесии за касиер
    List<CashDrawerSessionResponse> getSessionsByCashier(String cashierUsername);
    
    // Получаване на всички сесии за дата
    List<CashDrawerSessionResponse> getSessionsByDate(LocalDate date);
    
    // Получаване на всички активни сесии
    List<CashDrawerSessionResponse> getActiveSessions();
    
    // Debug: Получаване на всички сесии
    List<CashDrawerSessionResponse> getAllSessions();
    
    // Принудително приключване на сесия
    CashDrawerSessionResponse forceEndSession(Long sessionId);
}
