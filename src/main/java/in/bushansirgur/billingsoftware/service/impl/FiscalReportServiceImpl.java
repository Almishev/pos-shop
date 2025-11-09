package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.FiscalReportEntity;
import in.bushansirgur.billingsoftware.entity.CashDrawerSessionEntity;
import in.bushansirgur.billingsoftware.io.FiscalReportRequest;
import in.bushansirgur.billingsoftware.io.FiscalReportResponse;
import in.bushansirgur.billingsoftware.repository.FiscalReceiptRepository;
import in.bushansirgur.billingsoftware.repository.FiscalReportRepository;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.repository.CashDrawerSessionRepository;
import in.bushansirgur.billingsoftware.repository.UserRepository;
import in.bushansirgur.billingsoftware.config.MainFiscalDeviceProperties;
import in.bushansirgur.billingsoftware.service.FiscalReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalReportServiceImpl implements FiscalReportService {
    
    private final FiscalReportRepository fiscalReportRepository;
    private final FiscalReceiptRepository fiscalReceiptRepository;
    private final OrderEntityRepository orderEntityRepository;
    private final CashDrawerSessionRepository cashDrawerSessionRepository;
    private final UserRepository userRepository;
    private final MainFiscalDeviceProperties mainFiscalDeviceProperties;
    
    @Override
    public FiscalReportResponse generateDailyReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        
        // Изчисляване на статистика за деня
        LocalDateTime startOfDay = reportDate.atStartOfDay();
        LocalDateTime endOfDay = reportDate.atTime(LocalTime.MAX);
        
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.DAILY, reportDate, reportDate);
        
        if (!existingReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Daily report for date " + reportDate + " already exists");
        }
        
        // Извличане на данни за деня
        Long totalReceipts = fiscalReceiptRepository.countByDateRange(startOfDay, endOfDay);
        Double totalSales = fiscalReceiptRepository.sumGrandTotalByDateRange(startOfDay, endOfDay);
        Double totalVAT = fiscalReceiptRepository.sumVatAmountByDateRange(startOfDay, endOfDay);
        
        // Създаване на отчета
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.DAILY, reportDate))
                .reportType(FiscalReportEntity.ReportType.DAILY)
                .reportDate(reportDate)
                .totalReceipts(totalReceipts != null ? totalReceipts.intValue() : 0)
                .totalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO)
                .totalVAT(totalVAT != null ? BigDecimal.valueOf(totalVAT) : BigDecimal.ZERO)
                .totalNetSales(totalSales != null && totalVAT != null ? 
                        BigDecimal.valueOf(totalSales - totalVAT) : BigDecimal.ZERO)
                .cashierName(request.getCashierName())
                .deviceSerialNumber(request.getDeviceSerialNumber())
                .notes(request.getNotes())
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Daily report generated: {}", report.getReportNumber());
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse generateShiftReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        
        // Изчисляване на реални данни за смяната (Z-отчет) – само за текущия касиер и в рамките на активната му сесия
        Long totalReceipts = 0L;
        Double totalSales = 0.0;

        LocalDateTime sessionFrom = reportDate.atStartOfDay();
        LocalDateTime sessionTo = reportDate.atTime(LocalTime.MAX);

        // Определи прозорец на смяната от активната cash drawer сесия
        String aggUsername = null; // обикновено имейл (username)
        String displayName = null; // показвано име (например "Petq")
        String cashierName = null; // име, с което се записва в OrderEntity.cashier
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            log.info("Shift report authentication check -> auth: {}, authenticated: {}", 
                    auth != null ? auth.getClass().getSimpleName() : "null",
                    auth != null ? auth.isAuthenticated() : false);
            if (auth != null && auth.isAuthenticated()) {
                aggUsername = auth.getName();
                log.info("Shift report -> extracted username from auth: {}", aggUsername);
            } else {
                log.warn("Shift report -> No authenticated user found in SecurityContext!");
            }
        } catch (Exception e) {
            log.error("Shift report -> Error getting authentication: {}", e.getMessage(), e);
        }

        // опитай да вземеш displayName от БД спрямо имейла (aggUsername)
        if (aggUsername != null) {
            try {
                var u = userRepository.findByEmail(aggUsername).orElse(null);
                if (u != null && u.getName() != null && !u.getName().isBlank()) {
                    displayName = u.getName();
                    cashierName = u.getName();
                } else if (u != null) {
                    cashierName = aggUsername; // fallback
                } else {
                    // последен опит: обходи потребителите и намери username/email съвпадение
                    final String userKey = aggUsername;
                    var match = userRepository.findAll().stream()
                            .filter(x -> userKey.equalsIgnoreCase(x.getEmail()) ||
                                    (x.getName() != null && userKey.equalsIgnoreCase(x.getName())))
                            .findFirst().orElse(null);
                    if (match != null) {
                        displayName = match.getName();
                        cashierName = match.getName() != null && !match.getName().isBlank() ? match.getName() : aggUsername;
                    } else {
                        cashierName = aggUsername;
                    }
                }
            } catch (Exception ignore) {}
        }
        if (cashierName == null) cashierName = aggUsername;

        log.info("Shift report debug -> cashier username: {}", aggUsername);
        log.info("Shift report debug -> display name: {}", displayName);
        log.info("Shift report -> authenticated as '{}', cashierName used for query: '{}'", aggUsername, cashierName);

        String resolvedSessionCashier = null;
        java.util.Optional<CashDrawerSessionEntity> sessionOpt = java.util.Optional.empty();
        
        if (aggUsername != null) {
            // Първо винаги търси сесия по касиер (email или име) - това е най-сигурното
            sessionOpt = cashDrawerSessionRepository.findActiveSessionByCashierAndDate(aggUsername, reportDate);
            if (sessionOpt.isEmpty() && displayName != null) {
                sessionOpt = cashDrawerSessionRepository.findActiveSessionByCashierAndDate(displayName, reportDate);
            }
            
            // Ако не намери по касиер, опитай по device (но само ако device-ът е подаден)
            if (sessionOpt.isEmpty() && request.getDeviceSerialNumber() != null && !request.getDeviceSerialNumber().isBlank()) {
                var byDevice = cashDrawerSessionRepository.findActiveSessionByDeviceAndDate(request.getDeviceSerialNumber(), reportDate);
                if (byDevice.isPresent()) {
                    // Провери дали device session-а е за същия касиер
                    String deviceCashier = byDevice.get().getCashierUsername();
                    if (deviceCashier != null && (deviceCashier.equalsIgnoreCase(aggUsername) || 
                        (displayName != null && deviceCashier.equalsIgnoreCase(displayName)))) {
                        sessionOpt = byDevice;
                        log.info("Shift report debug -> Found session by device '{}' for cashier '{}'", 
                                request.getDeviceSerialNumber(), deviceCashier);
                    } else {
                        log.warn("Shift report debug -> Device '{}' session belongs to different cashier '{}', ignoring", 
                                request.getDeviceSerialNumber(), deviceCashier);
                    }
                }
            }
            
            // Ако намери сесия по касиер, използвай device-а от тази сесия (независимо какво е подадено)
            if (sessionOpt.isPresent()) {
                var s = sessionOpt.get();
                if (s.getSessionStartTime() != null) sessionFrom = s.getSessionStartTime();
                if (s.getSessionEndTime() != null) sessionTo = s.getSessionEndTime();
                
                // Device-ът от сесията ще се използва при създаването на отчета
                String sessionDevice = s.getDeviceSerialNumber();
                if (sessionDevice != null && !sessionDevice.isBlank()) {
                    log.info("Shift report debug -> Found active session with device '{}' (request had '{}')", 
                            sessionDevice, request.getDeviceSerialNumber());
                }
                
                String sessionCashier = s.getCashierUsername();
                if (sessionCashier != null && !sessionCashier.isBlank()) {
                    resolvedSessionCashier = sessionCashier;
                    log.info("Shift report debug -> Using cashier '{}' from active session", sessionCashier);
                }
            } else {
                log.warn("Shift report debug -> No active session found for cashier '{}' or device '{}'", 
                        aggUsername, request.getDeviceSerialNumber());
            }
            
            // НАП изискване: Не може да се генерира shift report без активна cash drawer session
            if (sessionOpt.isEmpty()) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.PRECONDITION_FAILED,
                        "Не може да се генерира сменен отчет без активна cash drawer сесия. " +
                        "Моля, започнете работен ден (Контрол на касата) с въведена начална сума и избрано фискално устройство. " +
                        "Това е задължително изискване на НАП.");
            }
            
            log.info("Shift report debug -> session from: {} to {}", sessionFrom, sessionTo);

            // сумирай по всички възможни ключове за максимална съвместимост
            final java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
            if (aggUsername != null && !aggUsername.isBlank()) keys.add(aggUsername);
            if (cashierName != null && !cashierName.isBlank()) keys.add(cashierName);
            if (resolvedSessionCashier != null && !resolvedSessionCashier.isBlank()) keys.add(resolvedSessionCashier);
            if (request.getCashierName() != null && !request.getCashierName().isBlank()) keys.add(request.getCashierName());

            long totalCnt = 0L;
            double totalSum = 0.0;
            for (String k : keys) {
                long c = orderEntityRepository.countByCashierBetween(k, sessionFrom, sessionTo);
                Double s = orderEntityRepository.sumSalesByCashierBetween(k, sessionFrom, sessionTo);
                log.info("Shift report key='{}' -> cnt={}, sum={}", k, c, s);
                totalCnt += c;
                if (s != null) totalSum += s;
            }
            totalReceipts = totalCnt;
            totalSales = totalSum;
            log.info("Shift report debug -> totalReceipts={}, totalSales={}", totalReceipts, totalSales);
        } else {
            // fallback: опитай да определиш касиера от подаденото име или устройство
            log.warn("Shift report -> No authentication available, using fallback logic");
            String resolved = null;
            String altName = null;
            
            // Първо опитай да намериш cash drawer session по device serial number
            if (request.getDeviceSerialNumber() != null && !request.getDeviceSerialNumber().isBlank()) {
                var byDevice = cashDrawerSessionRepository.findActiveSessionByDeviceAndDate(request.getDeviceSerialNumber(), reportDate);
                if (byDevice.isPresent()) {
                    var session = byDevice.get();
                    resolved = session.getCashierUsername();
                    log.info("Shift report fallback -> Found session by device: cashier={}", resolved);
                    if (session.getSessionStartTime() != null) sessionFrom = session.getSessionStartTime();
                    if (session.getSessionEndTime() != null) sessionTo = session.getSessionEndTime();
                }
            }
            
            // Ако не намери по устройство, опитай по cashier name от request
            if (resolved == null && request.getCashierName() != null && !request.getCashierName().isBlank()) {
                try {
                    var u = userRepository.findByEmail(request.getCashierName()).orElse(null);
                    if (u == null) {
                        // ако е лично име, опитай да го намериш по name
                        u = userRepository.findAll().stream()
                                .filter(x -> request.getCashierName().equalsIgnoreCase(x.getName()))
                                .findFirst().orElse(null);
                    }
                    if (u != null) {
                        resolved = u.getEmail();
                        altName = u.getName();
                        log.info("Shift report fallback -> Found user by cashierName: email={}, name={}", resolved, altName);
                    }
                } catch (Exception e) {
                    log.error("Shift report fallback -> Error finding user: {}", e.getMessage());
                }
            }
            
            if (resolved != null) {
                // Сумирай поръчките по всички възможни ключове
                final java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
                keys.add(resolved);
                if (altName != null && !altName.isBlank()) keys.add(altName);
                if (request.getCashierName() != null && !request.getCashierName().isBlank()) keys.add(request.getCashierName());
                
                long totalCnt = 0L;
                double totalSum = 0.0;
                for (String k : keys) {
                    long c = orderEntityRepository.countByCashierBetween(k, sessionFrom, sessionTo);
                    Double s = orderEntityRepository.sumSalesByCashierBetween(k, sessionFrom, sessionTo);
                    log.info("Shift report fallback key='{}' -> cnt={}, sum={}", k, c, s);
                    totalCnt += c;
                    if (s != null) totalSum += s;
                }
                totalReceipts = totalCnt;
                totalSales = totalSum;
                log.info("Shift report fallback -> totalReceipts={}, totalSales={}", totalReceipts, totalSales);
            } else {
                log.warn("Shift report fallback -> Could not determine cashier, returning zeros");
                // Не правим fallback към всички поръчки - оставяме нули
                totalReceipts = 0L;
                totalSales = 0.0;
            }
        }
        
        // Изчисляване на ДДС (20% от продажбите)
        Double totalVAT = totalSales != null ? totalSales * 0.20 : 0.0;
        Double totalNetSales = totalSales != null ? totalSales - totalVAT : 0.0;
        
        // Получаване на cash drawer данни за касиера
        BigDecimal cashDrawerStartAmount = BigDecimal.ZERO;
        BigDecimal cashDrawerEndAmount = BigDecimal.ZERO;
        
        // За намиране на активната сесия използваме сигурното потребителско име от SecurityContext (обикновено имейл)
        // а за показване в отчета оставяме display името от request
        String sessionUsername = request.getCashierName();
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                sessionUsername = auth.getName();
            }
        } catch (Exception ignore) {}
        // Ако преди това сме резолвнали касиера от активна сесия по устройство, използвай този ключ за затваряне
        if (resolvedSessionCashier != null) sessionUsername = resolvedSessionCashier;
        
        if (sessionUsername != null) {
            var cashDrawerSession = cashDrawerSessionRepository.findActiveSessionByCashierAndDate(sessionUsername, reportDate);
            if (cashDrawerSession.isEmpty()) {
                // ако активната сесия е от предишен ден, вземи я игнорирайки датата
                var sessions = cashDrawerSessionRepository.findActiveSessionsByCashier(sessionUsername);
                if (!sessions.isEmpty()) {
                    // wrap първата намерена активна сесия като Optional
                    cashDrawerSession = java.util.Optional.of(sessions.get(0));
                }
            }
            if (cashDrawerSession.isPresent()) {
                var s = cashDrawerSession.get();
                if (s.getStartAmount() != null) cashDrawerStartAmount = s.getStartAmount();
                if (request.getCashDrawerEndAmount() != null) {
                    cashDrawerEndAmount = BigDecimal.valueOf(request.getCashDrawerEndAmount());
                } else if (s.getEndAmount() != null) {
                    cashDrawerEndAmount = s.getEndAmount();
                }
            } else {
                if (request.getCashDrawerStartAmount() != null) {
                    cashDrawerStartAmount = BigDecimal.valueOf(request.getCashDrawerStartAmount());
                }
                if (request.getCashDrawerEndAmount() != null) {
                    cashDrawerEndAmount = BigDecimal.valueOf(request.getCashDrawerEndAmount());
                }
            }
        }
        
        // Създаване на Z-отчет
        // Използвай device-а от активната сесия, ако е наличен (независимо какво е подадено в request)
        String deviceSerialForReport = request.getDeviceSerialNumber();
        if (sessionOpt.isPresent()) {
            String sessionDevice = sessionOpt.get().getDeviceSerialNumber();
            if (sessionDevice != null && !sessionDevice.isBlank()) {
                deviceSerialForReport = sessionDevice;
                log.info("Shift report -> Using device '{}' from active session instead of request device '{}'", 
                        sessionDevice, request.getDeviceSerialNumber());
            }
        }
        
        // Използвай cashier name от активната сесия, ако е наличен
        String cashierNameForReport = request.getCashierName();
        if (cashierNameForReport == null || cashierNameForReport.isBlank()) {
            if (displayName != null && !displayName.isBlank()) {
                cashierNameForReport = displayName;
            } else if (resolvedSessionCashier != null && !resolvedSessionCashier.isBlank()) {
                cashierNameForReport = resolvedSessionCashier;
            }
        }
        
        String paymentBreakdownJson = buildPaymentBreakdownJson(sessionFrom, sessionTo, aggUsername, cashierName);
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.SHIFT, reportDate))
                .reportType(FiscalReportEntity.ReportType.SHIFT)
                .reportDate(reportDate)
                .totalReceipts(totalReceipts != null ? totalReceipts.intValue() : 0)
                .totalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO)
                .totalVAT(totalVAT != null ? BigDecimal.valueOf(totalVAT) : BigDecimal.ZERO)
                .totalNetSales(totalNetSales != null ? BigDecimal.valueOf(totalNetSales) : BigDecimal.ZERO)
                .cashierName(cashierNameForReport)
                .deviceSerialNumber(deviceSerialForReport)
                .notes(request.getNotes())
                .cashDrawerStartAmount(cashDrawerStartAmount)
                .cashDrawerEndAmount(cashDrawerEndAmount)
                .paymentBreakdown(paymentBreakdownJson)
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Z-Report (Shift) generated: {} - Receipts: {}, Sales: {}, VAT: {}", 
                report.getReportNumber(), report.getTotalReceipts(), report.getTotalSales(), report.getTotalVAT());
        
        // Нулираме данните след генериране на сменен отчет – използваме username от SecurityContext
        if (sessionUsername != null) {
            resetDataAfterShiftReport(sessionUsername, reportDate);
        }
        
        // Автоматично изчисляване на крайна сума в касата (начална + всички плащания в брой), ако липсва
        if (cashDrawerEndAmount == null || BigDecimal.ZERO.compareTo(cashDrawerEndAmount) == 0) {
            try {
                double cashOnly = safeSum(aggUsername, cashierName, sessionFrom, sessionTo, in.bushansirgur.billingsoftware.io.PaymentMethod.CASH);
                double splitCash = safeSplitCash(aggUsername, cashierName, sessionFrom, sessionTo);
                BigDecimal calc = (cashDrawerStartAmount != null ? cashDrawerStartAmount : BigDecimal.ZERO)
                        .add(BigDecimal.valueOf(cashOnly + splitCash));
                cashDrawerEndAmount = calc;
            } catch (Exception ignore) {}
        }

        // Увери се, че записваме изчислената крайна сума в отчета и я връщаме
        try {
            report.setCashDrawerEndAmount(cashDrawerEndAmount);
            report = fiscalReportRepository.save(report);
        } catch (Exception e) {
            log.warn("Unable to persist auto-calculated cash drawer end amount: {}", e.getMessage());
        }

        return FiscalReportResponse.fromEntity(report);
    }

    private String buildPaymentBreakdownJson(LocalDateTime from, LocalDateTime to, String emailKey, String nameKey) {
        try {
            String key = (nameKey != null && !nameKey.isBlank()) ? nameKey : emailKey;
            if (key == null) return null;

            var CASH = in.bushansirgur.billingsoftware.io.PaymentMethod.CASH;
            var CARD = in.bushansirgur.billingsoftware.io.PaymentMethod.CARD;
            var SPLIT = in.bushansirgur.billingsoftware.io.PaymentMethod.SPLIT;

            long cashCnt = safeCount(emailKey, nameKey, from, to, CASH);
            double cashSum = safeSum(emailKey, nameKey, from, to, CASH);

            long cardCnt = safeCount(emailKey, nameKey, from, to, CARD);
            double cardSum = safeSum(emailKey, nameKey, from, to, CARD);

            long splitCnt = safeCount(emailKey, nameKey, from, to, SPLIT);
            double splitSum = safeSum(emailKey, nameKey, from, to, SPLIT);
            double splitCash = safeSplitCash(emailKey, nameKey, from, to);
            double splitCard = safeSplitCard(emailKey, nameKey, from, to);

            String json = "{" +
                    "\"CASH\":{\"count\":"+cashCnt+",\"total\":"+cashSum+"}," +
                    "\"CARD\":{\"count\":"+cardCnt+",\"total\":"+cardSum+"}," +
                    "\"SPLIT\":{\"count\":"+splitCnt+",\"total\":"+splitSum+",\"cash\":"+splitCash+",\"card\":"+splitCard+"}" +
                    "}";
            return json;
        } catch (Exception e) {
            log.warn("Failed to build payment breakdown json: {}", e.getMessage());
            return null;
        }
    }

    private long safeCount(String emailKey, String nameKey, LocalDateTime from, LocalDateTime to, in.bushansirgur.billingsoftware.io.PaymentMethod method) {
        long cntEmail = 0L;
        long cntName = 0L;
        if (emailKey != null) cntEmail = orderEntityRepository.countByCashierBetweenAndMethod(emailKey, method, from, to);
        if (nameKey != null && (emailKey == null || !nameKey.equalsIgnoreCase(emailKey)))
            cntName = orderEntityRepository.countByCashierBetweenAndMethod(nameKey, method, from, to);
        return cntEmail + cntName;
    }

    private double safeSum(String emailKey, String nameKey, LocalDateTime from, LocalDateTime to, in.bushansirgur.billingsoftware.io.PaymentMethod method) {
        double sumEmail = 0.0;
        double sumName = 0.0;
        if (emailKey != null) {
            Double v = orderEntityRepository.sumGrandByCashierBetweenAndMethod(emailKey, method, from, to);
            if (v != null) sumEmail = v;
        }
        if (nameKey != null && (emailKey == null || !nameKey.equalsIgnoreCase(emailKey))) {
            Double v = orderEntityRepository.sumGrandByCashierBetweenAndMethod(nameKey, method, from, to);
            if (v != null) sumName = v;
        }
        return sumEmail + sumName;
    }

    private double safeSplitCash(String emailKey, String nameKey, LocalDateTime from, LocalDateTime to) {
        double sumEmail = 0.0;
        double sumName = 0.0;
        if (emailKey != null) {
            Double v = orderEntityRepository.sumSplitCashByCashierBetween(emailKey, from, to);
            if (v != null) sumEmail = v;
        }
        if (nameKey != null && (emailKey == null || !nameKey.equalsIgnoreCase(emailKey))) {
            Double v = orderEntityRepository.sumSplitCashByCashierBetween(nameKey, from, to);
            if (v != null) sumName = v;
        }
        return sumEmail + sumName;
    }

    private double safeSplitCard(String emailKey, String nameKey, LocalDateTime from, LocalDateTime to) {
        double sumEmail = 0.0;
        double sumName = 0.0;
        if (emailKey != null) {
            Double v = orderEntityRepository.sumSplitCardByCashierBetween(emailKey, from, to);
            if (v != null) sumEmail = v;
        }
        if (nameKey != null && (emailKey == null || !nameKey.equalsIgnoreCase(emailKey))) {
            Double v = orderEntityRepository.sumSplitCardByCashierBetween(nameKey, from, to);
            if (v != null) sumName = v;
        }
        return sumEmail + sumName;
    }
    
    @Override
    public FiscalReportResponse generateStoreDailyReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        LocalDateTime startOfDay = reportDate.atStartOfDay();
        LocalDateTime endOfDay = reportDate.atTime(LocalTime.MAX);
        
        // Проверка за съществуващ общ дневен отчет за деня
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.STORE_DAILY, reportDate, reportDate);
        
        LocalDateTime reportStartTime = startOfDay;
        String reportNotes = "Общ дневен отчет за целия магазин";
        
        // Ако има съществуващ отчет(и), намираме последния и изчисляваме данните само за продажбите след него
        if (!existingReports.isEmpty()) {
            // Намираме последния отчет (по generatedAt)
            FiscalReportEntity lastReport = existingReports.stream()
                    .max((r1, r2) -> r1.getGeneratedAt().compareTo(r2.getGeneratedAt()))
                    .orElse(null);
            
            if (lastReport != null) {
                reportStartTime = lastReport.getGeneratedAt();
                reportNotes = String.format("Допълнителен общ дневен отчет за магазина (след първия отчет от %s)", 
                        lastReport.getGeneratedAt().toString());
                log.info("Generating additional store daily report for date {} after previous report at {}", 
                        reportDate, reportStartTime);
            }
        }
        
        // Изчисляване на реални данни за целия магазин за периода (от началото на деня или след последния отчет)
        Long totalReceipts = orderEntityRepository.countOrdersBetween(reportStartTime, endOfDay);
        Double totalSales = orderEntityRepository.sumSalesBetween(reportStartTime, endOfDay);
        
        // Изчисляване на ДДС (20% от продажбите)
        Double totalVAT = totalSales != null ? totalSales * 0.20 : 0.0;
        Double totalNetSales = totalSales != null ? totalSales - totalVAT : 0.0;
        
        // Получаване на данни по касиери за периода
        List<Object[]> cashierData = orderEntityRepository.summarizeByCashier(reportStartTime, endOfDay);
        String cashierBreakdownJson = buildCashierBreakdownJson(cashierData, reportStartTime, endOfDay);
        
        // Генериране на обща разбивка по плащания за целия магазин
        String paymentBreakdownJson = buildStorePaymentBreakdownJson(reportStartTime, endOfDay);
        
        // Създаване на общ дневен отчет за магазина
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.STORE_DAILY, reportDate))
                .reportType(FiscalReportEntity.ReportType.STORE_DAILY)
                .reportDate(reportDate)
                .totalReceipts(totalReceipts != null ? totalReceipts.intValue() : 0)
                .totalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO)
                .totalVAT(totalVAT != null ? BigDecimal.valueOf(totalVAT) : BigDecimal.ZERO)
                .totalNetSales(totalNetSales != null ? BigDecimal.valueOf(totalNetSales) : BigDecimal.ZERO)
                .cashierName(null) // Общ отчет за магазина - няма конкретен касиер
                .deviceSerialNumber(mainFiscalDeviceProperties.getSerial()) // Използва главното фискално устройство
                .cashDrawerStartAmount(null) // Няма контрол на касата за общ отчет
                .cashDrawerEndAmount(null) // Няма контрол на касата за общ отчет
                .cashierBreakdown(cashierBreakdownJson) // Данни по касиери
                .paymentBreakdown(paymentBreakdownJson) // Разбивка по плащания
                .notes(request.getNotes() != null ? request.getNotes() : reportNotes)
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Store Daily Report generated: {} - Total Receipts: {}, Total Sales: {}, Total VAT: {}", 
                report.getReportNumber(), report.getTotalReceipts(), report.getTotalSales(), report.getTotalVAT());
        
        // Изпращаме отчета към НАП чрез главното фискално устройство
        boolean sentToNAP = sendStoreDailyReportToNAP(report.getId());
        if (sentToNAP) {
            log.info("Store daily report successfully sent to NAP");
        } else {
            log.warn("Failed to send store daily report to NAP");
        }
        
        // ВАЖНО: Не нулираме данните автоматично след генериране на отчет
        // Това позволява на магазина да генерира допълнителни отчети ако има допълнителни продажби
        // Управителят може ръчно да нулира данните чрез опция за край на работния ден
        // resetDataAfterStoreDailyReport(reportDate); // Коментирано - не нулираме автоматично
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse generateMonthlyReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        LocalDate startOfMonth = reportDate.withDayOfMonth(1);
        LocalDate endOfMonth = reportDate.withDayOfMonth(reportDate.lengthOfMonth());
        
        // Проверка за съществуващ месечен отчет (само за информационни цели, не блокираме)
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.MONTHLY, startOfMonth, endOfMonth);
        
        if (!existingReports.isEmpty()) {
            log.info("Generating additional monthly report for {} {}. Existing reports count: {}", 
                    reportDate.getMonth(), reportDate.getYear(), existingReports.size());
        }
        
        // Изчисляване на месечна статистика за целия магазин (всички каси и всички устройства)
        LocalDateTime startOfMonthDateTime = startOfMonth.atStartOfDay();
        LocalDateTime endOfMonthDateTime = endOfMonth.atTime(LocalTime.MAX);
        
        // Използваме orderEntityRepository за консистентност с дневния отчет
        Long totalReceipts = orderEntityRepository.countOrdersBetween(startOfMonthDateTime, endOfMonthDateTime);
        Double totalSales = orderEntityRepository.sumSalesBetween(startOfMonthDateTime, endOfMonthDateTime);
        
        // Изчисляване на ДДС (20% от продажбите)
        Double totalVAT = totalSales != null ? totalSales * 0.20 : 0.0;
        Double totalNetSales = totalSales != null ? totalSales - totalVAT : 0.0;
        
        // Получаване на данни по касиери за целия месец
        List<Object[]> cashierData = orderEntityRepository.summarizeByCashier(startOfMonthDateTime, endOfMonthDateTime);
        String cashierBreakdownJson = buildCashierBreakdownJson(cashierData, startOfMonthDateTime, endOfMonthDateTime);
        
        // Генериране на обща разбивка по плащания за целия магазин за месеца
        String paymentBreakdownJson = buildStorePaymentBreakdownJson(startOfMonthDateTime, endOfMonthDateTime);
        
        // За месечен отчет използваме главното фискално устройство или "Всички устройства"
        String deviceSerial = request.getDeviceSerialNumber();
        if (deviceSerial == null || deviceSerial.isBlank()) {
            // Ако не е посочено устройство, използваме главното от конфигурацията или "Всички устройства"
            try {
                String mainDeviceSerial = mainFiscalDeviceProperties.getSerial();
                if (mainDeviceSerial != null && !mainDeviceSerial.isBlank()) {
                    deviceSerial = mainDeviceSerial;
                } else {
                    deviceSerial = "Всички устройства";
                }
            } catch (Exception e) {
                deviceSerial = "Всички устройства";
            }
        }
        
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.MONTHLY, reportDate))
                .reportType(FiscalReportEntity.ReportType.MONTHLY)
                .reportDate(reportDate)
                .totalReceipts(totalReceipts != null ? totalReceipts.intValue() : 0)
                .totalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO)
                .totalVAT(totalVAT != null ? BigDecimal.valueOf(totalVAT) : BigDecimal.ZERO)
                .totalNetSales(totalNetSales != null ? BigDecimal.valueOf(totalNetSales) : BigDecimal.ZERO)
                .cashierName("Всички касиери") // Месечният отчет е за целия магазин
                .deviceSerialNumber(deviceSerial)
                .notes(request.getNotes())
                .cashierBreakdown(cashierBreakdownJson)
                .paymentBreakdown(paymentBreakdownJson)
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Monthly report generated: {} with cashier breakdown and payment breakdown", report.getReportNumber());
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse generateYearlyReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        LocalDate startOfYear = reportDate.withDayOfYear(1);
        LocalDate endOfYear = reportDate.withDayOfYear(reportDate.lengthOfYear());
        
        // Проверка за съществуващ годишен отчет
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.YEARLY, startOfYear, endOfYear);
        
        if (!existingReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Yearly report for " + reportDate.getYear() + " already exists");
        }
        
        // Изчисляване на годишна статистика
        LocalDateTime startOfYearDateTime = startOfYear.atStartOfDay();
        LocalDateTime endOfYearDateTime = endOfYear.atTime(LocalTime.MAX);
        
        Long totalReceipts = fiscalReceiptRepository.countByDateRange(startOfYearDateTime, endOfYearDateTime);
        Double totalSales = fiscalReceiptRepository.sumGrandTotalByDateRange(startOfYearDateTime, endOfYearDateTime);
        Double totalVAT = fiscalReceiptRepository.sumVatAmountByDateRange(startOfYearDateTime, endOfYearDateTime);
        
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.YEARLY, reportDate))
                .reportType(FiscalReportEntity.ReportType.YEARLY)
                .reportDate(reportDate)
                .totalReceipts(totalReceipts != null ? totalReceipts.intValue() : 0)
                .totalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO)
                .totalVAT(totalVAT != null ? BigDecimal.valueOf(totalVAT) : BigDecimal.ZERO)
                .totalNetSales(totalSales != null && totalVAT != null ? 
                        BigDecimal.valueOf(totalSales - totalVAT) : BigDecimal.ZERO)
                .cashierName(request.getCashierName())
                .deviceSerialNumber(request.getDeviceSerialNumber())
                .notes(request.getNotes())
                .build();
        
        report = fiscalReportRepository.save(report);
        log.info("Yearly report generated: {}", report.getReportNumber());
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public List<FiscalReportResponse> getAllReports() {
        return fiscalReportRepository.findAll().stream()
                .map(FiscalReportResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public FiscalReportResponse getReportById(Long reportId) {
        FiscalReportEntity report = fiscalReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with id: " + reportId));
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse getReportByNumber(String reportNumber) {
        FiscalReportEntity report = fiscalReportRepository.findByReportNumber(reportNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with number: " + reportNumber));
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public List<FiscalReportResponse> getReportsByDateRange(LocalDate startDate, LocalDate endDate) {
        return fiscalReportRepository.findByDateRange(startDate, endDate).stream()
                .map(FiscalReportResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<FiscalReportResponse> getReportsByType(String reportType) {
        try {
            FiscalReportEntity.ReportType type = FiscalReportEntity.ReportType.valueOf(reportType.toUpperCase());
            return fiscalReportRepository.findByReportType(type).stream()
                    .map(FiscalReportResponse::fromEntity)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report type: " + reportType);
        }
    }
    
    @Override
    public List<FiscalReportResponse> getReportsByDevice(String deviceSerialNumber) {
        return fiscalReportRepository.findByDeviceSerialNumber(deviceSerialNumber).stream()
                .map(FiscalReportResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean sendReportToNAF(Long reportId) {
        FiscalReportEntity report = fiscalReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with id: " + reportId));
        
        // Симулация на изпращане към НАП
        report.setStatus(FiscalReportEntity.ReportStatus.SENT_TO_NAF);
        fiscalReportRepository.save(report);
        
        log.info("Report sent to NAF: {}", report.getReportNumber());
        return true;
    }
    
    @Override
    public boolean sendReportToNAF(String reportNumber) {
        FiscalReportEntity report = fiscalReportRepository.findByReportNumber(reportNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with number: " + reportNumber));
        
        // Симулация на изпращане към НАП
        report.setStatus(FiscalReportEntity.ReportStatus.SENT_TO_NAF);
        fiscalReportRepository.save(report);
        
        log.info("Report sent to NAF: {}", report.getReportNumber());
        return true;
    }
    
    @Override
    public Double getTotalSalesForDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        return fiscalReceiptRepository.sumGrandTotalByDateRange(startOfDay, endOfDay);
    }
    
    @Override
    public Double getTotalVATForDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        return fiscalReceiptRepository.sumVatAmountByDateRange(startOfDay, endOfDay);
    }
    
    @Override
    public Integer getTotalReceiptsForDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        Long count = fiscalReceiptRepository.countByDateRange(startOfDay, endOfDay);
        return count != null ? count.intValue() : 0;
    }
    
    // Помощен метод за генериране на номер на отчет
    private String generateReportNumber(FiscalReportEntity.ReportType reportType, LocalDate date) {
        String typePrefix = switch (reportType) {
            case DAILY -> "DR";
            case STORE_DAILY -> "SDR";
            case SHIFT -> "SR";
            case MONTHLY -> "MR";
            case YEARLY -> "YR";
            case Z_REPORT -> "ZR";
            case X_REPORT -> "XR";
        };
        
        return typePrefix + "-" + date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + 
               "-" + String.format("%06d", (int)(Math.random() * 999999));
    }
    
    private String buildCashierBreakdownJson(List<Object[]> cashierData, LocalDateTime from, LocalDateTime to) {
        if (cashierData == null || cashierData.isEmpty()) {
            return "[]";
        }
        
        var CASH = in.bushansirgur.billingsoftware.io.PaymentMethod.CASH;
        var CARD = in.bushansirgur.billingsoftware.io.PaymentMethod.CARD;
        var SPLIT = in.bushansirgur.billingsoftware.io.PaymentMethod.SPLIT;
        
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < cashierData.size(); i++) {
            Object[] row = cashierData.get(i);
            String cashierEmail = (String) row[0];
            Long count = (Long) row[1];
            Double total = (Double) row[2];
            
            // Try to convert email to display name
            String displayName = cashierEmail;
            if (cashierEmail != null) {
                try {
                    var user = userRepository.findByEmail(cashierEmail).orElse(null);
                    if (user != null && user.getName() != null && !user.getName().isBlank()) {
                        displayName = user.getName();
                    }
                } catch (Exception ignored) {
                    // If lookup fails, use email as fallback
                }
            }
            
            // Get payment breakdown for this cashier
            long cashCnt = orderEntityRepository.countByCashierBetweenAndMethod(cashierEmail, CASH, from, to);
            Double cashSum = orderEntityRepository.sumGrandByCashierBetweenAndMethod(cashierEmail, CASH, from, to);
            long cardCnt = orderEntityRepository.countByCashierBetweenAndMethod(cashierEmail, CARD, from, to);
            Double cardSum = orderEntityRepository.sumGrandByCashierBetweenAndMethod(cashierEmail, CARD, from, to);
            long splitCnt = orderEntityRepository.countByCashierBetweenAndMethod(cashierEmail, SPLIT, from, to);
            Double splitSum = orderEntityRepository.sumGrandByCashierBetweenAndMethod(cashierEmail, SPLIT, from, to);
            Double splitCash = orderEntityRepository.sumSplitCashByCashierBetween(cashierEmail, from, to);
            Double splitCard = orderEntityRepository.sumSplitCardByCashierBetween(cashierEmail, from, to);
            
            if (i > 0) json.append(",");
            json.append("{")
                .append("\"cashier\":\"").append(displayName != null ? escapeJson(displayName) : "Неизвестен").append("\",")
                .append("\"ordersCount\":").append(count != null ? count : 0).append(",")
                .append("\"totalAmount\":").append(total != null ? total : 0.0).append(",")
                .append("\"payments\":{")
                    .append("\"CASH\":{\"count\":").append(cashCnt).append(",\"total\":").append(cashSum != null ? cashSum : 0.0).append("},")
                    .append("\"CARD\":{\"count\":").append(cardCnt).append(",\"total\":").append(cardSum != null ? cardSum : 0.0).append("},")
                    .append("\"SPLIT\":{\"count\":").append(splitCnt).append(",\"total\":").append(splitSum != null ? splitSum : 0.0)
                        .append(",\"cash\":").append(splitCash != null ? splitCash : 0.0)
                        .append(",\"card\":").append(splitCard != null ? splitCard : 0.0).append("}")
                .append("}")
                .append("}");
        }
        json.append("]");
        
        return json.toString();
    }
    
    private String buildStorePaymentBreakdownJson(LocalDateTime from, LocalDateTime to) {
        try {
            var CASH = in.bushansirgur.billingsoftware.io.PaymentMethod.CASH;
            var CARD = in.bushansirgur.billingsoftware.io.PaymentMethod.CARD;
            var SPLIT = in.bushansirgur.billingsoftware.io.PaymentMethod.SPLIT;
            
            long cashCnt = orderEntityRepository.countByPaymentMethodBetween(CASH, from, to);
            Double cashSum = orderEntityRepository.sumByPaymentMethodBetween(CASH, from, to);
            long cardCnt = orderEntityRepository.countByPaymentMethodBetween(CARD, from, to);
            Double cardSum = orderEntityRepository.sumByPaymentMethodBetween(CARD, from, to);
            long splitCnt = orderEntityRepository.countByPaymentMethodBetween(SPLIT, from, to);
            Double splitSum = orderEntityRepository.sumByPaymentMethodBetween(SPLIT, from, to);
            Double splitCash = orderEntityRepository.sumSplitCashBetween(from, to);
            Double splitCard = orderEntityRepository.sumSplitCardBetween(from, to);
            
            String json = "{" +
                    "\"CASH\":{\"count\":"+cashCnt+",\"total\":"+(cashSum != null ? cashSum : 0.0)+"}," +
                    "\"CARD\":{\"count\":"+cardCnt+",\"total\":"+(cardSum != null ? cardSum : 0.0)+"}," +
                    "\"SPLIT\":{\"count\":"+splitCnt+",\"total\":"+(splitSum != null ? splitSum : 0.0)+
                    ",\"cash\":"+(splitCash != null ? splitCash : 0.0)+
                    ",\"card\":"+(splitCard != null ? splitCard : 0.0)+"}" +
                    "}";
            return json;
        } catch (Exception e) {
            log.warn("Failed to build store payment breakdown json: {}", e.getMessage());
            return null;
        }
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    @Override
    public void resetDataAfterShiftReport(String cashierUsername, LocalDate date) {
        log.info("=== STARTING SHIFT REPORT RESET for cashier: {} on date: {} ===", cashierUsername, date);
        
        try {
            // 1. Приключваме всички активни сесии за този касиер (без оглед на датата)
            log.info("Step 1: Closing active cash drawer sessions for cashier: {}", cashierUsername);
            var activeSessions = cashDrawerSessionRepository.findActiveSessionsByCashier(cashierUsername);
            if (activeSessions.isEmpty()) {
                // fallback и по дата (старото поведение)
                var byDate = cashDrawerSessionRepository.findActiveSessionByCashierAndDate(cashierUsername, date);
                byDate.ifPresent(s -> activeSessions.add(s));
            }
            for (CashDrawerSessionEntity session : activeSessions) {
                session.setStatus(CashDrawerSessionEntity.SessionStatus.CLOSED);
                session.setSessionEndTime(LocalDateTime.now());
                session.setNotes("Автоматично приключена след сменен Z-отчет");
                cashDrawerSessionRepository.save(session);
                log.info("Closed session for cashier: {} on device: {}", session.getCashierUsername(), session.getDeviceSerialNumber());
            }
            
            // 2. Нулираме фискалното устройство за този касиер
            log.info("Step 2: Resetting fiscal device for cashier: {}", cashierUsername);
            // В реална система тук бихме изпратили команда към конкретното фискално устройство
            // за нулиране на данните за тази каса
            
            // 3. Логваме успешното завършване
            log.info("=== SHIFT REPORT RESET COMPLETED ===");
            log.info("Cashier: {} ready for new shift", cashierUsername);
            
        } catch (Exception e) {
            log.error("Error during shift report reset for cashier {}: {}", cashierUsername, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Грешка при нулиране на данните след сменен Z-отчет: " + e.getMessage());
        }
    }
    
    @Override
    public void resetDataAfterStoreDailyReport(LocalDate date) {
        log.info("=== STARTING STORE DAILY REPORT RESET for date: {} ===", date);
        
        try {
            // 1. Приключваме всички активни cash drawer сесии
            log.info("Step 1: Closing all active cash drawer sessions...");
            List<CashDrawerSessionEntity> activeSessions = cashDrawerSessionRepository.findAll()
                    .stream()
                    .filter(session -> session.getStatus() == CashDrawerSessionEntity.SessionStatus.ACTIVE)
                    .collect(Collectors.toList());
            
            for (CashDrawerSessionEntity session : activeSessions) {
                session.setStatus(CashDrawerSessionEntity.SessionStatus.CLOSED);
                session.setSessionEndTime(LocalDateTime.now());
                session.setNotes("Автоматично приключена след общ Z-отчет");
                cashDrawerSessionRepository.save(session);
                log.info("Closed session for cashier: {} on device: {}", 
                        session.getCashierUsername(), session.getDeviceSerialNumber());
            }
            
            // 2. Нулираме всички фискални устройства (маркираме като готови за нов ден)
            log.info("Step 2: Resetting all fiscal devices...");
            // В реална система тук бихме изпратили команди към всички фискални устройства
            // за нулиране на натрупаните данни
            
            // 3. Изчистваме временните данни (ако има такива)
            log.info("Step 3: Clearing temporary data...");
            // В реална система тук бихме изчистили кеш, временни файлове и т.н.
            
            // 4. Логваме успешното завършване
            log.info("=== STORE DAILY REPORT RESET COMPLETED ===");
            log.info("Closed {} active sessions", activeSessions.size());
            log.info("All fiscal devices reset for new day");
            log.info("Temporary data cleared");
            
        } catch (Exception e) {
            log.error("Error during store daily report reset: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Грешка при нулиране на данните след общ Z-отчет: " + e.getMessage());
        }
    }
    
    @Override
    public boolean sendStoreDailyReportToNAP(Long reportId) {
        log.info("=== SENDING STORE DAILY REPORT TO NAP: {} ===", reportId);
        
        try {
            FiscalReportEntity report = fiscalReportRepository.findById(reportId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Report not found with id: " + reportId));
            
            if (report.getReportType() != FiscalReportEntity.ReportType.STORE_DAILY) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Report is not a store daily report");
            }
            
            // ВАЖНО: В реална система фискалното устройство автоматично изпраща Z-отчета към НАП
            // чрез интернет връзка. Този метод е за проследяване/логиране или за ръчно изпращане
            // в специални случаи (напр. ако фискалното устройство не е успело да изпрати).
            // 
            // В реална система тук бихме:
            // 1. Проверили дали фискалното устройство е изпратило автоматично
            // 2. Ако не - изпратили данните ръчно чрез API на фискалното устройство
            // 3. Получили потвърждение от НАП
            // 4. Записали статуса на изпращането
            
            log.info("Store daily report {} sent to NAP successfully", report.getReportNumber());
            log.info("Report details: Date={}, Total Sales={}, Total VAT={}", 
                    report.getReportDate(), report.getTotalSales(), report.getTotalVAT());
            
            return true;
            
        } catch (Exception e) {
            log.error("Error sending store daily report to NAP: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendShiftReportToNAP(Long reportId) {
        log.info("=== SENDING SHIFT REPORT TO NAP: {} ===", reportId);
        
        try {
            FiscalReportEntity report = fiscalReportRepository.findById(reportId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Report not found with id: " + reportId));
            
            if (report.getReportType() != FiscalReportEntity.ReportType.SHIFT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Report is not a shift report");
            }
            
            // ВАЖНО: X-отчетът (сменен отчет) е ВЪТРЕШЕН отчет и НЕ се изпраща към НАП.
            // Само Z-отчетът (дневен финансов отчет) се изпраща автоматично от фискалното устройство.
            // Този метод е за проследяване/логиране на вътрешни отчети.
            //
            // В реална система:
            // - X-отчетът се използва само за управление и контрол в магазина
            // - Z-отчетът се изпраща автоматично от фискалното устройство към НАП
            
            log.info("Shift report (X-report) {} logged successfully", report.getReportNumber());
            log.info("Report details: Cashier={}, Device={}, Total Sales={}", 
                    report.getCashierName(), report.getDeviceSerialNumber(), report.getTotalSales());
            
            return true;
            
        } catch (Exception e) {
            log.error("Error sending shift report to NAP: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String exportReportToXML(Long reportId) {
        FiscalReportEntity report = fiscalReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with id: " + reportId));
        return generateXMLFromReport(report);
    }
    
    @Override
    public String exportReportToXML(String reportNumber) {
        FiscalReportEntity report = fiscalReportRepository.findByReportNumber(reportNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Report not found with number: " + reportNumber));
        return generateXMLFromReport(report);
    }
    
    private String generateXMLFromReport(FiscalReportEntity report) {
        try {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<FiscalReport xmlns=\"http://www.nap.bg/fiscal/report\">\n");
            
            // Header information
            xml.append("  <Header>\n");
            xml.append("    <ReportNumber>").append(escapeXml(report.getReportNumber())).append("</ReportNumber>\n");
            xml.append("    <ReportType>").append(escapeXml(report.getReportType().name())).append("</ReportType>\n");
            xml.append("    <ReportDate>").append(report.getReportDate().toString()).append("</ReportDate>\n");
            xml.append("    <GeneratedAt>").append(report.getGeneratedAt().toString()).append("</GeneratedAt>\n");
            xml.append("    <Status>").append(escapeXml(report.getStatus().name())).append("</Status>\n");
            if (report.getDeviceSerialNumber() != null) {
                xml.append("    <DeviceSerialNumber>").append(escapeXml(report.getDeviceSerialNumber())).append("</DeviceSerialNumber>\n");
            }
            if (report.getCashierName() != null) {
                xml.append("    <CashierName>").append(escapeXml(report.getCashierName())).append("</CashierName>\n");
            }
            xml.append("  </Header>\n");
            
            // Financial summary
            xml.append("  <FinancialSummary>\n");
            xml.append("    <TotalReceipts>").append(report.getTotalReceipts() != null ? report.getTotalReceipts() : 0).append("</TotalReceipts>\n");
            xml.append("    <TotalSales>").append(report.getTotalSales() != null ? report.getTotalSales().toString() : "0.00").append("</TotalSales>\n");
            xml.append("    <TotalVAT>").append(report.getTotalVAT() != null ? report.getTotalVAT().toString() : "0.00").append("</TotalVAT>\n");
            xml.append("    <TotalNetSales>").append(report.getTotalNetSales() != null ? report.getTotalNetSales().toString() : "0.00").append("</TotalNetSales>\n");
            if (report.getCashDrawerStartAmount() != null) {
                xml.append("    <CashDrawerStartAmount>").append(report.getCashDrawerStartAmount().toString()).append("</CashDrawerStartAmount>\n");
            }
            if (report.getCashDrawerEndAmount() != null) {
                xml.append("    <CashDrawerEndAmount>").append(report.getCashDrawerEndAmount().toString()).append("</CashDrawerEndAmount>\n");
            }
            xml.append("  </FinancialSummary>\n");
            
            // Payment breakdown
            if (report.getPaymentBreakdown() != null && !report.getPaymentBreakdown().trim().isEmpty()) {
                xml.append("  <PaymentBreakdown>\n");
                try {
                    // Parse JSON payment breakdown
                    String paymentJson = report.getPaymentBreakdown();
                    // Simple JSON parsing for payment breakdown
                    if (paymentJson.contains("\"CASH\"")) {
                        xml.append("    <PaymentMethod type=\"CASH\">\n");
                        String cashCount = extractJsonValue(paymentJson, "CASH", "count");
                        String cashTotal = extractJsonValue(paymentJson, "CASH", "total");
                        xml.append("      <Count>").append(cashCount != null ? cashCount : "0").append("</Count>\n");
                        xml.append("      <Total>").append(cashTotal != null ? cashTotal : "0.00").append("</Total>\n");
                        xml.append("    </PaymentMethod>\n");
                    }
                    if (paymentJson.contains("\"CARD\"")) {
                        xml.append("    <PaymentMethod type=\"CARD\">\n");
                        String cardCount = extractJsonValue(paymentJson, "CARD", "count");
                        String cardTotal = extractJsonValue(paymentJson, "CARD", "total");
                        xml.append("      <Count>").append(cardCount != null ? cardCount : "0").append("</Count>\n");
                        xml.append("      <Total>").append(cardTotal != null ? cardTotal : "0.00").append("</Total>\n");
                        xml.append("    </PaymentMethod>\n");
                    }
                    if (paymentJson.contains("\"SPLIT\"")) {
                        xml.append("    <PaymentMethod type=\"SPLIT\">\n");
                        String splitCount = extractJsonValue(paymentJson, "SPLIT", "count");
                        String splitTotal = extractJsonValue(paymentJson, "SPLIT", "total");
                        String splitCash = extractJsonValue(paymentJson, "SPLIT", "cash");
                        String splitCard = extractJsonValue(paymentJson, "SPLIT", "card");
                        xml.append("      <Count>").append(splitCount != null ? splitCount : "0").append("</Count>\n");
                        xml.append("      <Total>").append(splitTotal != null ? splitTotal : "0.00").append("</Total>\n");
                        if (splitCash != null) {
                            xml.append("      <CashAmount>").append(splitCash).append("</CashAmount>\n");
                        }
                        if (splitCard != null) {
                            xml.append("      <CardAmount>").append(splitCard).append("</CardAmount>\n");
                        }
                        xml.append("    </PaymentMethod>\n");
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse payment breakdown JSON: {}", e.getMessage());
                    xml.append("    <RawData>").append(escapeXml(report.getPaymentBreakdown())).append("</RawData>\n");
                }
                xml.append("  </PaymentBreakdown>\n");
            }
            
            // Cashier breakdown (for store daily and monthly reports)
            if (report.getCashierBreakdown() != null && !report.getCashierBreakdown().trim().isEmpty()) {
                xml.append("  <CashierBreakdown>\n");
                try {
                    // Parse JSON cashier breakdown
                    String cashierJson = report.getCashierBreakdown();
                    // Extract cashier entries from JSON array
                    int startIdx = cashierJson.indexOf('[');
                    int endIdx = cashierJson.lastIndexOf(']');
                    if (startIdx >= 0 && endIdx > startIdx) {
                        String arrayContent = cashierJson.substring(startIdx + 1, endIdx);
                        // Simple parsing - find each cashier entry
                        String[] entries = arrayContent.split("\\},\\s*\\{");
                        for (String entry : entries) {
                            entry = entry.replaceAll("^\\{", "").replaceAll("\\}$", "");
                            String cashierName = extractJsonValue(entry, "cashier");
                            String cashierOrders = extractJsonValue(entry, "ordersCount");
                            String cashierTotal = extractJsonValue(entry, "totalAmount");
                            
                            if (cashierName != null) {
                                xml.append("    <Cashier>\n");
                                xml.append("      <Name>").append(escapeXml(cashierName)).append("</Name>\n");
                                xml.append("      <TotalOrders>").append(cashierOrders != null ? cashierOrders : "0").append("</TotalOrders>\n");
                                xml.append("      <TotalAmount>").append(cashierTotal != null ? cashierTotal : "0.00").append("</TotalAmount>\n");
                                
                                // Payment breakdown for this cashier - extract from "payments" object
                                String paymentsJson = extractJsonValue(entry, "payments");
                                if (paymentsJson != null) {
                                    String cashierCashCount = extractJsonValue(paymentsJson, "CASH", "count");
                                    String cashierCashTotal = extractJsonValue(paymentsJson, "CASH", "total");
                                    String cashierCardCount = extractJsonValue(paymentsJson, "CARD", "count");
                                    String cashierCardTotal = extractJsonValue(paymentsJson, "CARD", "total");
                                    String cashierSplitCount = extractJsonValue(paymentsJson, "SPLIT", "count");
                                    String cashierSplitTotal = extractJsonValue(paymentsJson, "SPLIT", "total");
                                    
                                    if (cashierCashCount != null || cashierCardCount != null || cashierSplitCount != null) {
                                        xml.append("      <PaymentBreakdown>\n");
                                        if (cashierCashCount != null) {
                                            xml.append("        <PaymentMethod type=\"CASH\">\n");
                                            xml.append("          <Count>").append(cashierCashCount).append("</Count>\n");
                                            xml.append("          <Total>").append(cashierCashTotal != null ? cashierCashTotal : "0.00").append("</Total>\n");
                                            xml.append("        </PaymentMethod>\n");
                                        }
                                        if (cashierCardCount != null) {
                                            xml.append("        <PaymentMethod type=\"CARD\">\n");
                                            xml.append("          <Count>").append(cashierCardCount).append("</Count>\n");
                                            xml.append("          <Total>").append(cashierCardTotal != null ? cashierCardTotal : "0.00").append("</Total>\n");
                                            xml.append("        </PaymentMethod>\n");
                                        }
                                        if (cashierSplitCount != null) {
                                            xml.append("        <PaymentMethod type=\"SPLIT\">\n");
                                            xml.append("          <Count>").append(cashierSplitCount).append("</Count>\n");
                                            xml.append("          <Total>").append(cashierSplitTotal != null ? cashierSplitTotal : "0.00").append("</Total>\n");
                                            String splitCash = extractJsonValue(paymentsJson, "SPLIT", "cash");
                                            String splitCard = extractJsonValue(paymentsJson, "SPLIT", "card");
                                            if (splitCash != null) {
                                                xml.append("          <CashAmount>").append(splitCash).append("</CashAmount>\n");
                                            }
                                            if (splitCard != null) {
                                                xml.append("          <CardAmount>").append(splitCard).append("</CardAmount>\n");
                                            }
                                            xml.append("        </PaymentMethod>\n");
                                        }
                                        xml.append("      </PaymentBreakdown>\n");
                                    }
                                }
                                
                                xml.append("    </Cashier>\n");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse cashier breakdown JSON: {}", e.getMessage());
                    xml.append("    <RawData>").append(escapeXml(report.getCashierBreakdown())).append("</RawData>\n");
                }
                xml.append("  </CashierBreakdown>\n");
            }
            
            // Notes
            if (report.getNotes() != null && !report.getNotes().trim().isEmpty()) {
                xml.append("  <Notes>").append(escapeXml(report.getNotes())).append("</Notes>\n");
            }
            
            xml.append("</FiscalReport>");
            
            log.info("XML generated successfully for report: {}", report.getReportNumber());
            return xml.toString();
            
        } catch (Exception e) {
            log.error("Error generating XML from report: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Грешка при генериране на XML: " + e.getMessage());
        }
    }
    
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }
    
    private String extractJsonValue(String json, String key) {
        try {
            // Try string value first
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            // Try numeric value (integer or decimal)
            pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            // Try object value (for nested objects like "payments")
            pattern = "\"" + key + "\"\\s*:\\s*\\{([^}]+(?:\\{[^}]*\\}[^}]*)*)\\}";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(json);
            if (m.find()) {
                return "{" + m.group(1) + "}";
            }
        } catch (Exception e) {
            log.debug("Failed to extract JSON value for key {}: {}", key, e.getMessage());
        }
        return null;
    }
    
    private String extractJsonValue(String json, String parentKey, String childKey) {
        try {
            // Find the parent object first - handle nested objects
            String parentPattern = "\"" + parentKey + "\"\\s*:\\s*\\{([^}]+(?:\\{[^}]*\\}[^}]*)*)\\}";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(parentPattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                String parentContent = m.group(1);
                return extractJsonValue(parentContent, childKey);
            }
        } catch (Exception e) {
            log.debug("Failed to extract JSON value for {}.{}: {}", parentKey, childKey, e.getMessage());
        }
        return null;
    }
}
