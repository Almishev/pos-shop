package in.bushansirgur.billingsoftware.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.entity.FiscalReceiptEntity;
import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.entity.OrderItemEntity;
import in.bushansirgur.billingsoftware.io.FiscalReceiptRequest;
import in.bushansirgur.billingsoftware.io.FiscalReceiptResponse;
import in.bushansirgur.billingsoftware.io.PaymentDetails;
import in.bushansirgur.billingsoftware.io.PaymentMethod;
import in.bushansirgur.billingsoftware.repository.FiscalDeviceRepository;
import in.bushansirgur.billingsoftware.repository.CashDrawerSessionRepository;
import in.bushansirgur.billingsoftware.repository.FiscalReceiptRepository;
import in.bushansirgur.billingsoftware.repository.OrderEntityRepository;
import in.bushansirgur.billingsoftware.service.FiscalDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalDeviceServiceImpl implements FiscalDeviceService {
    
    private final FiscalDeviceRepository fiscalDeviceRepository;
    private final CashDrawerSessionRepository cashDrawerSessionRepository;
    private final FiscalReceiptRepository fiscalReceiptRepository;
    private final OrderEntityRepository orderEntityRepository;
    private final ErpNetFpClient erpNetFpClient;
    
    @Override
    public List<FiscalDeviceEntity> getAllDevices() {
        System.out.println("=== FiscalDeviceServiceImpl.getAllDevices called ===");
        try {
            List<FiscalDeviceEntity> devices = fiscalDeviceRepository.findAll();
            // annotate locked flag based on active sessions
            for (FiscalDeviceEntity d : devices) {
                boolean locked = cashDrawerSessionRepository.existsByDeviceSerialNumberAndStatus(
                        d.getSerialNumber(), in.bushansirgur.billingsoftware.entity.CashDrawerSessionEntity.SessionStatus.ACTIVE);
                d.setLocked(locked);
                System.out.println("Device " + d.getSerialNumber() + " - Status: " + d.getStatus() + ", Locked: " + locked);
                
                // Debug: проверка на активни сесии за това устройство
                var activeSessions = cashDrawerSessionRepository.findByDeviceSerialNumberAndStatus(
                        d.getSerialNumber(), in.bushansirgur.billingsoftware.entity.CashDrawerSessionEntity.SessionStatus.ACTIVE);
                System.out.println("  Active sessions for " + d.getSerialNumber() + ": " + activeSessions.size());
                for (var session : activeSessions) {
                    System.out.println("    Session ID: " + session.getId() + ", Cashier: " + session.getCashierUsername() + 
                                     ", Status: " + session.getStatus() + ", Date: " + session.getSessionDate());
                }
            }
            System.out.println("Repository returned " + devices.size() + " devices");
            for (FiscalDeviceEntity device : devices) {
                System.out.println("Device from DB: ID=" + device.getId() + 
                                 ", Serial=" + device.getSerialNumber() + 
                                 ", Status=" + device.getStatus());
            }
            return devices;
        } catch (Exception e) {
            System.err.println("Error in FiscalDeviceServiceImpl.getAllDevices: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Override
    public FiscalDeviceEntity getDeviceBySerialNumber(String serialNumber) {
        return fiscalDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Fiscal device not found with serial number: " + serialNumber));
    }
    
    @Override
    public FiscalDeviceEntity registerDevice(FiscalDeviceEntity device) {
        System.out.println("=== FiscalDeviceServiceImpl.registerDevice called ===");
        System.out.println("Device serial number: " + device.getSerialNumber());
        System.out.println("Device manufacturer: " + device.getManufacturer());
        System.out.println("Device model: " + device.getModel());
        
        // Проверка дали устройството вече съществува
        if (fiscalDeviceRepository.findBySerialNumber(device.getSerialNumber()).isPresent()) {
            System.err.println("Device with serial number " + device.getSerialNumber() + " already exists");
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Fiscal device with serial number " + device.getSerialNumber() + " already exists");
        }

        device.setIndividualNumber(resolveIndividualNumber(device.getIndividualNumber(), device.getSerialNumber(), null));
        
        // Set default status if not provided
        if (device.getStatus() == null) {
            device.setStatus(FiscalDeviceEntity.DeviceStatus.ACTIVE);
            System.out.println("Set default status to ACTIVE");
        }
        
        // Set registration date
        device.setRegistrationDate(LocalDateTime.now());
        System.out.println("Set registration date to: " + device.getRegistrationDate());
        
        FiscalDeviceEntity savedDevice = fiscalDeviceRepository.save(device);
        System.out.println("Device saved successfully with ID: " + savedDevice.getId());
        
        return savedDevice;
    }
    
    @Override
    public FiscalDeviceEntity updateDevice(FiscalDeviceEntity device) {
        FiscalDeviceEntity existingDevice = getDeviceBySerialNumber(device.getSerialNumber());
        
        existingDevice.setManufacturer(device.getManufacturer());
        existingDevice.setModel(device.getModel());
        existingDevice.setFiscalMemoryNumber(device.getFiscalMemoryNumber());
        existingDevice.setApiEndpoint(device.getApiEndpoint());
        existingDevice.setApiKey(device.getApiKey());
        existingDevice.setStatus(device.getStatus());
        existingDevice.setLocation(device.getLocation());
        existingDevice.setNotes(device.getNotes());
        applyIndividualNumber(existingDevice, device.getIndividualNumber());

        return fiscalDeviceRepository.save(existingDevice);
    }
    
    @Override
    public void deleteDevice(Long deviceId) {
        FiscalDeviceEntity device = fiscalDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Fiscal device not found with id: " + deviceId));
        
        fiscalDeviceRepository.delete(device);
    }
    
    @Override
    public FiscalReceiptResponse sendReceiptToFiscalDevice(FiscalReceiptRequest request) {
        FiscalDeviceEntity device = getDeviceBySerialNumber(request.getDeviceSerialNumber());
        
        if (device.getStatus() != FiscalDeviceEntity.DeviceStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Fiscal device is not active: " + request.getDeviceSerialNumber());
        }
        
        try {
            OrderEntity order = orderEntityRepository.findByOrderId(request.getOrderId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Поръчката не е намерена: " + request.getOrderId()));
            if (order.getUniqueSaleNumber() == null || order.getUniqueSaleNumber().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Продажбата няма уникален номер и не може да се отпечата");
            }

            JsonNode printer = resolvePrinter(device);
            String printerId = printer.get("id").asText();
            int textLimit = printer.path("itemTextMaxLength").asInt(36);
            Map<String, Object> body = receiptBody(order, order.getItems(), textLimit, false, null);
            JsonNode printed = erpNetFpClient.printReceipt(printerId, body);
            FiscalReceiptEntity fiscalReceipt = savePrintedReceipt(printed, request.getOrderId(),
                    request.getDeviceSerialNumber(), request.getSubtotal(), request.getVatAmount(), request.getGrandTotal());
            log.info("Fiscal receipt printed by ErpNet.FP: {}", fiscalReceipt.getFiscalNumber());
            return FiscalReceiptResponse.fromEntity(fiscalReceipt);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error sending receipt to fiscal device: {}", e.getMessage());
        
            FiscalReceiptEntity errorReceipt = FiscalReceiptEntity.builder()
                    .orderId(request.getOrderId())
                    .deviceSerialNumber(request.getDeviceSerialNumber())
                    .subtotal(request.getSubtotal())
                    .vatAmount(request.getVatAmount())
                    .grandTotal(request.getGrandTotal())
                    .status(FiscalReceiptEntity.FiscalStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .build();
            
            fiscalReceiptRepository.save(errorReceipt);
            
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to send receipt to fiscal device: " + e.getMessage());
        }
    }
    
    @Override
    public FiscalReceiptResponse getReceiptStatus(String fiscalNumber) {
        FiscalReceiptEntity receipt = fiscalReceiptRepository.findByFiscalNumber(fiscalNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Fiscal receipt not found: " + fiscalNumber));
        
        return FiscalReceiptResponse.fromEntity(receipt);
    }
    
    @Override
    public boolean isDeviceConnected(String serialNumber) {
        return isDeviceReady(serialNumber);
    }

    @Override
    public boolean isDeviceReady(String serialNumber) {
        Optional<FiscalDeviceEntity> device = fiscalDeviceRepository.findBySerialNumber(serialNumber);
        if (device.isEmpty() || device.get().getStatus() != FiscalDeviceEntity.DeviceStatus.ACTIVE) {
            return false;
        }
        try {
            JsonNode printer = resolvePrinter(device.get());
            JsonNode status = erpNetFpClient.status(printer.get("id").asText());
            return ErpNetFpClient.isOk(status);
        } catch (RuntimeException ex) {
            log.warn("ErpNet.FP is not ready for {}: {}", serialNumber, ex.getMessage());
            return false;
        }
    }

    @Override
    public String generateXReport(String deviceSerialNumber) {
        FiscalDeviceEntity device = getDeviceBySerialNumber(deviceSerialNumber);
        JsonNode printer = resolvePrinter(device);
        erpNetFpClient.xReport(printer.get("id").asText());
        return "X отчет отпечатан от фискалното устройство";
    }

    @Override
    public String generateZReport(String deviceSerialNumber) {
        FiscalDeviceEntity device = getDeviceBySerialNumber(deviceSerialNumber);
        JsonNode printer = resolvePrinter(device);
        erpNetFpClient.zReport(printer.get("id").asText());
        return "Z отчет отпечатан от фискалното устройство";
    }

    @Override
    public void printReversalReceipt(OrderEntity original, List<OrderItemEntity> refundLines) {
        FiscalReceiptEntity saleReceipt = fiscalReceiptRepository.findByOrderId(original.getOrderId())
                .filter(receipt -> receipt.getStatus() == FiscalReceiptEntity.FiscalStatus.CONFIRMED)
                .filter(receipt -> receipt.getFiscalMemorySerialNumber() != null
                        && !receipt.getFiscalMemorySerialNumber().isBlank())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Няма фискален бон за тази продажба. Сторното не е отпечатано."));
        FiscalDeviceEntity device = getDeviceBySerialNumber(saleReceipt.getDeviceSerialNumber());
        JsonNode printer = resolvePrinter(device);
        int textLimit = printer.path("itemTextMaxLength").asInt(36);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("receiptNumber", saleReceipt.getFiscalNumber());
        extra.put("receiptDateTime", saleReceipt.getFiscalDateTime().toString());
        extra.put("fiscalMemorySerialNumber", saleReceipt.getFiscalMemorySerialNumber());
        extra.put("reason", "refund");
        Map<String, Object> body = receiptBody(original, refundLines, textLimit, true, extra);
        JsonNode printed = erpNetFpClient.printReversal(printer.get("id").asText(), body);
        BigDecimal total = lineTotal(refundLines);
        savePrintedReceipt(printed, "STORNO-" + original.getOrderId() + "-" + printed.path("receiptNumber").asText(""),
                device.getSerialNumber(), total, null, total);
    }

    @Override
    public String requireIndividualNumber(String serialNumber) {
        FiscalDeviceEntity device = getDeviceBySerialNumber(serialNumber);
        if (device.getIndividualNumber() != null && device.getIndividualNumber().matches("\\d{8}")) {
            return device.getIndividualNumber();
        }
        String resolved = resolveIndividualNumber(null, device.getSerialNumber(), device.getId());
        device.setIndividualNumber(resolved);
        fiscalDeviceRepository.save(device);
        return resolved;
    }

    private String resolveIndividualNumber(String requested, String serialNumber, Long currentDeviceId) {
        String candidate = requested == null ? "" : requested.trim();
        if (candidate.isEmpty() && serialNumber != null && serialNumber.trim().matches("\\d{8}")) {
            candidate = serialNumber.trim();
        }
        if (!candidate.matches("\\d{8}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Индивидуалният номер на фискалното устройство трябва да е 8 цифри");
        }
        fiscalDeviceRepository.findByIndividualNumber(candidate).ifPresent(existing -> {
            if (currentDeviceId == null || !currentDeviceId.equals(existing.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Индивидуалният номер вече е записан на друго устройство");
            }
        });
        return candidate;
    }

    private void applyIndividualNumber(FiscalDeviceEntity existing, String requested) {
        if (requested == null || requested.isBlank()) {
            if (existing.getIndividualNumber() == null
                    && existing.getSerialNumber() != null
                    && existing.getSerialNumber().trim().matches("\\d{8}")) {
                existing.setIndividualNumber(resolveIndividualNumber(null, existing.getSerialNumber(), existing.getId()));
            }
            return;
        }
        existing.setIndividualNumber(resolveIndividualNumber(requested, existing.getSerialNumber(), existing.getId()));
    }

    private JsonNode resolvePrinter(FiscalDeviceEntity device) {
        JsonNode printers = erpNetFpClient.printers();
        if (printers == null || !printers.isObject() || printers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ErpNet.FP работи, но не вижда фискално устройство. Провери кабела и дали апаратът е включен.");
        }
        String serial = device.getSerialNumber() == null ? "" : device.getSerialNumber().trim();
        String individual = device.getIndividualNumber() == null ? "" : device.getIndividualNumber().trim();
        JsonNode only = null;
        String onlyId = null;
        int count = 0;
        var fields = printers.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            count++;
            only = field.getValue();
            onlyId = field.getKey();
            String printerSerial = field.getValue().path("serialNumber").asText("");
            String memory = field.getValue().path("fiscalMemorySerialNumber").asText("");
            if (sameDevice(serial, printerSerial) || sameDevice(serial, memory)
                    || sameDevice(individual, printerSerial) || sameDevice(individual, memory)
                    || sameDevice(serial, field.getKey())) {
                return withId(field.getValue(), field.getKey());
            }
        }
        if (count == 1) {
            log.info("Using the only ErpNet.FP printer {} for device {}", onlyId, serial);
            return withId(only, onlyId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Има няколко устройства в ErpNet.FP. Запиши серийния номер на касата така, както го показва ErpNet.FP.");
    }

    private static JsonNode withId(JsonNode printer, String id) {
        if (printer instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.put("id", id);
            return objectNode;
        }
        return printer;
    }

    private static boolean sameDevice(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        return left.equalsIgnoreCase(right.trim());
    }

    private Map<String, Object> receiptBody(OrderEntity order, List<OrderItemEntity> lines, int textLimit,
                                            boolean reversal, Map<String, Object> extra) {
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (OrderItemEntity line : lines) {
                double qty = line.getQuantity() == null ? 0 : Math.abs(line.getQuantity());
                if (qty <= 0 || line.getPrice() == null) {
                    continue;
                }
                BigDecimal unit = BigDecimal.valueOf(line.getPrice()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                total = total.add(lineTotal);
                String name = line.getName() == null ? "Стока" : line.getName().trim();
                if (name.length() > textLimit && textLimit > 0) {
                    name = name.substring(0, textLimit);
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("text", name);
                item.put("quantity", qty);
                item.put("unitPrice", unit);
                item.put("taxGroup", taxGroup(line.getVatRate()));
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Бонът няма редове за печат");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uniqueSaleNumber", order.getUniqueSaleNumber());
        body.put("items", items);
        body.put("payments", payments(order, total, reversal));
        if (extra != null) {
            body.putAll(extra);
        }
        return body;
    }

    private List<Map<String, Object>> payments(OrderEntity order, BigDecimal total, boolean reversal) {
        List<Map<String, Object>> payments = new ArrayList<>();
        PaymentDetails details = order.getPaymentDetails();
        if (!reversal && order.getPaymentMethod() == PaymentMethod.SPLIT && details != null
                && details.getCashAmount() != null && details.getCardAmount() != null) {
            payments.add(payment("cash", details.getCashAmount()));
            payments.add(payment("card", details.getCardAmount()));
            return payments;
        }
        String type = order.getPaymentMethod() == PaymentMethod.CARD ? "card" : "cash";
        payments.add(payment(type, total));
        return payments;
    }

    private static Map<String, Object> payment(String type, double amount) {
        return payment(type, BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP));
    }

    private static Map<String, Object> payment(String type, BigDecimal amount) {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentType", type);
        payment.put("amount", amount.setScale(2, RoundingMode.HALF_UP));
        return payment;
    }

    private static BigDecimal lineTotal(List<OrderItemEntity> lines) {
        BigDecimal total = BigDecimal.ZERO;
        if (lines == null) {
            return total;
        }
        for (OrderItemEntity line : lines) {
            if (line.getPrice() == null || line.getQuantity() == null) {
                continue;
            }
            total = total.add(BigDecimal.valueOf(line.getPrice())
                    .multiply(BigDecimal.valueOf(Math.abs(line.getQuantity())))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return total;
    }

    /**
     * Bulgarian devices: 1 = А (0%), 2 = Б (20%), 3 = В (9%).
     * Accepts both 0.20 and 20.
     */
    static int taxGroup(Double vatRate) {
        double rate = vatRate == null ? 0.20 : vatRate;
        if (rate > 1) {
            rate = rate / 100.0;
        }
        if (rate >= 0.15) {
            return 2;
        }
        if (rate >= 0.05) {
            return 3;
        }
        return 1;
    }

    private FiscalReceiptEntity savePrintedReceipt(JsonNode printed, String orderId, String deviceSerial,
                                                   BigDecimal subtotal, BigDecimal vatAmount, BigDecimal grandTotal) {
        String receiptNumber = printed.path("receiptNumber").asText("");
        if (receiptNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ErpNet.FP не върна номер на фискален бон");
        }
        LocalDateTime printedAt = LocalDateTime.now();
        String dateText = printed.path("receiptDateTime").asText("");
        if (!dateText.isBlank()) {
            try {
                printedAt = LocalDateTime.parse(dateText);
            } catch (java.time.format.DateTimeParseException ex) {
                log.warn("ErpNet.FP receipt time was not parsed: {}", dateText);
            }
        }
        FiscalReceiptEntity fiscalReceipt = FiscalReceiptEntity.builder()
                .fiscalNumber(receiptNumber)
                .orderId(orderId)
                .deviceSerialNumber(deviceSerial)
                .fiscalDateTime(printedAt)
                .subtotal(subtotal)
                .vatAmount(vatAmount)
                .grandTotal(grandTotal)
                .fiscalMemorySerialNumber(printed.path("fiscalMemorySerialNumber").asText(null))
                .status(FiscalReceiptEntity.FiscalStatus.CONFIRMED)
                .build();
        return fiscalReceiptRepository.save(fiscalReceipt);
    }
}
