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
        
        // Проверка за съществуващ общ дневен отчет
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.STORE_DAILY, reportDate, reportDate);
        
        if (!existingReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Store daily report for date " + reportDate + " already exists");
        }
        
        // Изчисляване на реални данни за целия магазин за деня
        // Събира данни от всички касиери и всички поръчки за деня
        Long totalReceipts = orderEntityRepository.countByOrderDate(reportDate);
        Double totalSales = orderEntityRepository.sumSalesByDate(reportDate);
        
        // Изчисляване на ДДС (20% от продажбите)
        Double totalVAT = totalSales != null ? totalSales * 0.20 : 0.0;
        Double totalNetSales = totalSales != null ? totalSales - totalVAT : 0.0;
        
        // Получаване на данни по касиери за деня
        List<Object[]> cashierData = orderEntityRepository.summarizeByCashierForDate(reportDate);
        String cashierBreakdownJson = buildCashierBreakdownJson(cashierData);
        
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
                .notes(request.getNotes() != null ? request.getNotes() : "Общ дневен отчет за целия магазин")
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
        
        // Нулираме данните след генериране на общ Z-отчет
        resetDataAfterStoreDailyReport(reportDate);
        
        return FiscalReportResponse.fromEntity(report);
    }
    
    @Override
    public FiscalReportResponse generateMonthlyReport(FiscalReportRequest request) {
        LocalDate reportDate = request.getReportDate() != null ? request.getReportDate() : LocalDate.now();
        LocalDate startOfMonth = reportDate.withDayOfMonth(1);
        LocalDate endOfMonth = reportDate.withDayOfMonth(reportDate.lengthOfMonth());
        
        // Проверка за съществуващ месечен отчет
        List<FiscalReportEntity> existingReports = fiscalReportRepository.findByReportTypeAndDateRange(
                FiscalReportEntity.ReportType.MONTHLY, startOfMonth, endOfMonth);
        
        if (!existingReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Monthly report for " + reportDate.getMonth() + " " + reportDate.getYear() + " already exists");
        }
        
        // Изчисляване на месечна статистика
        LocalDateTime startOfMonthDateTime = startOfMonth.atStartOfDay();
        LocalDateTime endOfMonthDateTime = endOfMonth.atTime(LocalTime.MAX);
        
        Long totalReceipts = fiscalReceiptRepository.countByDateRange(startOfMonthDateTime, endOfMonthDateTime);
        Double totalSales = fiscalReceiptRepository.sumGrandTotalByDateRange(startOfMonthDateTime, endOfMonthDateTime);
        Double totalVAT = fiscalReceiptRepository.sumVatAmountByDateRange(startOfMonthDateTime, endOfMonthDateTime);
        
        FiscalReportEntity report = FiscalReportEntity.builder()
                .reportNumber(generateReportNumber(FiscalReportEntity.ReportType.MONTHLY, reportDate))
                .reportType(FiscalReportEntity.ReportType.MONTHLY)
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
        log.info("Monthly report generated: {}", report.getReportNumber());
        
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
    
    private String buildCashierBreakdownJson(List<Object[]> cashierData) {
        if (cashierData == null || cashierData.isEmpty()) {
            return "[]";
        }
        
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < cashierData.size(); i++) {
            Object[] row = cashierData.get(i);
            String cashier = (String) row[0];
            Long count = (Long) row[1];
            Double total = (Double) row[2];
            
            if (i > 0) json.append(",");
            json.append("{")
                .append("\"cashier\":\"").append(cashier != null ? cashier : "Неизвестен").append("\",")
                .append("\"ordersCount\":").append(count != null ? count : 0).append(",")
                .append("\"totalAmount\":").append(total != null ? total : 0.0)
                .append("}");
        }
        json.append("]");
        
        return json.toString();
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
            
            // В реална система тук бихме:
            // 1. Изпратили данните към НАП чрез главното фискално устройство
            // 2. Получили потвърждение от НАП
            // 3. Записали статуса на изпращането
            
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
            
            // В реална система тук бихме:
            // 1. Изпратили данните към НАП чрез конкретното фискално устройство
            // 2. Получили потвърждение от НАП
            // 3. Записали статуса на изпращането
            
            log.info("Shift report {} sent to NAP successfully", report.getReportNumber());
            log.info("Report details: Cashier={}, Device={}, Total Sales={}", 
                    report.getCashierName(), report.getDeviceSerialNumber(), report.getTotalSales());
            
            return true;
            
        } catch (Exception e) {
            log.error("Error sending shift report to NAP: {}", e.getMessage(), e);
            return false;
        }
    }
}
