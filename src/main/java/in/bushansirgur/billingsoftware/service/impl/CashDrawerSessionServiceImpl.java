package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.CashDrawerSessionEntity;
import in.bushansirgur.billingsoftware.io.CashDrawerSessionRequest;
import in.bushansirgur.billingsoftware.io.CashDrawerSessionResponse;
import in.bushansirgur.billingsoftware.repository.CashDrawerSessionRepository;
import in.bushansirgur.billingsoftware.repository.FiscalDeviceRepository;
import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.service.CashDrawerSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashDrawerSessionServiceImpl implements CashDrawerSessionService {
    
    private final CashDrawerSessionRepository cashDrawerSessionRepository;
    private final FiscalDeviceRepository fiscalDeviceRepository;
    
    @Override
    public CashDrawerSessionResponse startWorkDay(CashDrawerSessionRequest request) {
        LocalDate today = LocalDate.now();
        log.info("Start work day requested: cashier={}, startAmount={}, deviceSerial={}, registerId={}",
                request.getCashierUsername(), request.getStartAmount(), request.getDeviceSerialNumber(), request.getRegisterId());
        
        // Проверка дали касиерът вече има активна сесия днес
        var existingSession = cashDrawerSessionRepository.findActiveSessionByCashierAndDate(
                request.getCashierUsername(), today);
        
        if (existingSession.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Касиерът " + request.getCashierUsername() + " вече има активна сесия днес");
        }
        
        // НАП изискване: Началната сума е задължителна и трябва да е по-голяма от 0
        if (request.getStartAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Началната сума в касата е задължителна според изискванията на НАП. Моля, въведете начална сума.");
        }
        if (request.getStartAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Началната сума в касата трябва да е по-голяма от 0.00 лв. според изискванията на НАП.");
        }
        log.info("Start amount validated: {} for cashier: {}", 
                request.getStartAmount(), request.getCashierUsername());
        
        // Авто-асоциация на устройство: 1) ако request.deviceSerialNumber е подаден и ACTIVE -> ползвай; 2) иначе първото ACTIVE устройство
        final String boundSerial;
        String requestedSerial = request.getDeviceSerialNumber();
        if (requestedSerial != null && !requestedSerial.isBlank()) {
            log.info("Requested specific serial: {}", requestedSerial);
            fiscalDeviceRepository.findBySerialNumber(requestedSerial)
                    .filter(d -> d.getStatus() == FiscalDeviceEntity.DeviceStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неактивно или липсващо устройство: " + requestedSerial));
            // Device lock check
            if (cashDrawerSessionRepository.existsByDeviceSerialNumberAndStatus(requestedSerial, CashDrawerSessionEntity.SessionStatus.ACTIVE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Устройството вече е заето от активна каса");
            }
            boundSerial = requestedSerial;
        } else {
            var activeDevices = fiscalDeviceRepository.findByStatus(FiscalDeviceEntity.DeviceStatus.ACTIVE);
            if (activeDevices.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Няма налично активно фискално устройство");
            }
            // pick first free active device
            String freeSerial = activeDevices.stream()
                    .map(FiscalDeviceEntity::getSerialNumber)
                    .filter(sn -> !cashDrawerSessionRepository.existsByDeviceSerialNumberAndStatus(sn, CashDrawerSessionEntity.SessionStatus.ACTIVE))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Всички активни устройства са заети"));
            boundSerial = freeSerial;
            log.info("Auto-selected serial: {}", boundSerial);
        }

        // Създаване на нова сесия
        CashDrawerSessionEntity session = CashDrawerSessionEntity.builder()
                .sessionDate(today)
                .cashierUsername(request.getCashierUsername())
                .startAmount(request.getStartAmount())
                .sessionStartTime(LocalDateTime.now())
                .status(CashDrawerSessionEntity.SessionStatus.ACTIVE)
                .notes(request.getNotes())
                .registerId(request.getRegisterId())
                .deviceSerialNumber(boundSerial)
                .build();
        
        session = cashDrawerSessionRepository.save(session);
        log.info("Work day started for cashier: {} with start amount: {}", 
                request.getCashierUsername(), request.getStartAmount());
        
        return CashDrawerSessionResponse.fromEntity(session);
    }
    
    @Override
    public CashDrawerSessionResponse endWorkDay(Long sessionId, CashDrawerSessionRequest request) {
        CashDrawerSessionEntity session = cashDrawerSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Сесията не е намерена"));
        
        if (session.getStatus() != CashDrawerSessionEntity.SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Сесията не е активна");
        }
        
        // Приключване на сесията
        session.setEndAmount(request.getEndAmount());
        session.setSessionEndTime(LocalDateTime.now());
        session.setStatus(CashDrawerSessionEntity.SessionStatus.CLOSED);
        if (request.getNotes() != null) {
            session.setNotes(request.getNotes());
        }
        
        session = cashDrawerSessionRepository.save(session);
        log.info("Work day ended for cashier: {} - Start: {}, End: {}", 
                session.getCashierUsername(), session.getStartAmount(), session.getEndAmount());
        System.out.println("=== Session closed for device: " + session.getDeviceSerialNumber() + " ===");
        System.out.println("Session status changed to: " + session.getStatus());
        
        // Debug: проверка дали устройството е все още заето
        boolean stillLocked = cashDrawerSessionRepository.existsByDeviceSerialNumberAndStatus(
                session.getDeviceSerialNumber(), CashDrawerSessionEntity.SessionStatus.ACTIVE);
        System.out.println("Device " + session.getDeviceSerialNumber() + " still locked: " + stillLocked);
        
        // Debug: проверка на всички сесии за това устройство
        var allSessions = cashDrawerSessionRepository.findByDeviceSerialNumberAndStatus(
                session.getDeviceSerialNumber(), CashDrawerSessionEntity.SessionStatus.ACTIVE);
        System.out.println("All ACTIVE sessions for device " + session.getDeviceSerialNumber() + ": " + allSessions.size());
        for (var s : allSessions) {
            System.out.println("  Session ID: " + s.getId() + ", Cashier: " + s.getCashierUsername() + 
                             ", Status: " + s.getStatus() + ", Date: " + s.getSessionDate());
        }
        
        // Debug: проверка на всички сесии за това устройство (включително CLOSED)
        var allSessionsAllStatuses = cashDrawerSessionRepository.findByDeviceSerialNumberAndStatus(
                session.getDeviceSerialNumber(), CashDrawerSessionEntity.SessionStatus.CLOSED);
        System.out.println("All CLOSED sessions for device " + session.getDeviceSerialNumber() + ": " + allSessionsAllStatuses.size());
        for (var s : allSessionsAllStatuses) {
            System.out.println("  Session ID: " + s.getId() + ", Cashier: " + s.getCashierUsername() + 
                             ", Status: " + s.getStatus() + ", Date: " + s.getSessionDate());
        }
        
        // Debug: проверка на всички сесии за това устройство (всички статуси)
        var allSessionsAllStatuses2 = cashDrawerSessionRepository.findByDeviceSerialNumberAndStatus(
                session.getDeviceSerialNumber(), CashDrawerSessionEntity.SessionStatus.ACTIVE);
        System.out.println("All ACTIVE sessions for device " + session.getDeviceSerialNumber() + ": " + allSessionsAllStatuses2.size());
        for (var s : allSessionsAllStatuses2) {
            System.out.println("  Session ID: " + s.getId() + ", Cashier: " + s.getCashierUsername() + 
                             ", Status: " + s.getStatus() + ", Date: " + s.getSessionDate());
        }
        
        // Debug: проверка на всички сесии за това устройство (всички статуси)
        var allSessionsAllStatuses3 = cashDrawerSessionRepository.findByDeviceSerialNumberAndStatus(
                session.getDeviceSerialNumber(), CashDrawerSessionEntity.SessionStatus.ACTIVE);
        System.out.println("All ACTIVE sessions for device " + session.getDeviceSerialNumber() + ": " + allSessionsAllStatuses3.size());
        for (var s : allSessionsAllStatuses3) {
            System.out.println("  Session ID: " + s.getId() + ", Cashier: " + s.getCashierUsername() + 
                             ", Status: " + s.getStatus() + ", Date: " + s.getSessionDate());
        }
        
        return CashDrawerSessionResponse.fromEntity(session);
    }
    
    @Override
    public CashDrawerSessionResponse getActiveSession(String cashierUsername, LocalDate date) {
        System.out.println("=== getActiveSession called for user: " + cashierUsername + ", date: " + date + " ===");
        
        // Първо търсим за конкретната дата
        var session = cashDrawerSessionRepository.findActiveSessionByCashierAndDate(cashierUsername, date);
        
        // Ако няма за днешна дата, търсим за всички дати (за да хванем "зависнали" сесии)
        if (session.isEmpty()) {
            System.out.println("No active session found for date " + date + ", searching all dates...");
            // Търсим всички активни сесии за този касиер
            var allActiveSessions = cashDrawerSessionRepository.findAll()
                    .stream()
                    .filter(s -> s.getCashierUsername().equals(cashierUsername) && 
                               s.getStatus() == CashDrawerSessionEntity.SessionStatus.ACTIVE)
                    .collect(Collectors.toList());
            
            if (!allActiveSessions.isEmpty()) {
                System.out.println("Found " + allActiveSessions.size() + " active sessions for user " + cashierUsername);
                session = Optional.of(allActiveSessions.get(0));
            }
        }
        
        if (session.isEmpty()) {
            System.out.println("No active session found for user: " + cashierUsername);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Няма активна сесия за касиер " + cashierUsername + " на " + date);
        }
        
        System.out.println("Active session found: ID=" + session.get().getId() + ", Date=" + session.get().getSessionDate());
        return CashDrawerSessionResponse.fromEntity(session.get());
    }
    
    @Override
    public List<CashDrawerSessionResponse> getSessionsByCashier(String cashierUsername) {
        return cashDrawerSessionRepository.findByCashierUsernameAndSessionDate(cashierUsername, LocalDate.now())
                .stream()
                .map(CashDrawerSessionResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<CashDrawerSessionResponse> getSessionsByDate(LocalDate date) {
        return cashDrawerSessionRepository.findBySessionDateAndStatus(date, CashDrawerSessionEntity.SessionStatus.ACTIVE)
                .stream()
                .map(CashDrawerSessionResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<CashDrawerSessionResponse> getActiveSessions() {
        return cashDrawerSessionRepository.findBySessionDateAndStatus(LocalDate.now(), CashDrawerSessionEntity.SessionStatus.ACTIVE)
                .stream()
                .map(CashDrawerSessionResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<CashDrawerSessionResponse> getAllSessions() {
        return cashDrawerSessionRepository.findAll()
                .stream()
                .map(CashDrawerSessionResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public CashDrawerSessionResponse forceEndSession(Long sessionId) {
        log.info("Force ending session: {}", sessionId);
        
        CashDrawerSessionEntity session = cashDrawerSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Сесията не е намерена: " + sessionId));
        
        if (session.getStatus() != CashDrawerSessionEntity.SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Сесията не е активна: " + sessionId);
        }
        
        // Принудително приключване
        session.setEndAmount(session.getStartAmount()); // Запазваме началната сума
        session.setSessionEndTime(LocalDateTime.now());
        session.setStatus(CashDrawerSessionEntity.SessionStatus.CLOSED);
        session.setNotes("Принудително приключена от администратор");
        
        session = cashDrawerSessionRepository.save(session);
        log.info("Session force ended: {} - Device: {}", sessionId, session.getDeviceSerialNumber());
        
        return CashDrawerSessionResponse.fromEntity(session);
    }
}
