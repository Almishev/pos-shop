package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.entity.FiscalReceiptEntity;
import in.bushansirgur.billingsoftware.io.FiscalReceiptRequest;
import in.bushansirgur.billingsoftware.io.FiscalReceiptResponse;
import in.bushansirgur.billingsoftware.repository.FiscalDeviceRepository;
import in.bushansirgur.billingsoftware.repository.FiscalReceiptRepository;
import in.bushansirgur.billingsoftware.service.FiscalDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalDeviceServiceImpl implements FiscalDeviceService {
    
    private final FiscalDeviceRepository fiscalDeviceRepository;
    private final FiscalReceiptRepository fiscalReceiptRepository;
    
    @Override
    public List<FiscalDeviceEntity> getAllDevices() {
        return fiscalDeviceRepository.findAll();
    }
    
    @Override
    public FiscalDeviceEntity getDeviceBySerialNumber(String serialNumber) {
        return fiscalDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Fiscal device not found with serial number: " + serialNumber));
    }
    
    @Override
    public FiscalDeviceEntity registerDevice(FiscalDeviceEntity device) {
        // Проверка дали устройството вече съществува
        if (fiscalDeviceRepository.findBySerialNumber(device.getSerialNumber()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Fiscal device with serial number " + device.getSerialNumber() + " already exists");
        }
        
        return fiscalDeviceRepository.save(device);
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
         
            String fiscalNumber = generateFiscalNumber(device);
            
            FiscalReceiptEntity fiscalReceipt = FiscalReceiptEntity.builder()
                    .fiscalNumber(fiscalNumber)
                    .orderId(request.getOrderId())
                    .deviceSerialNumber(request.getDeviceSerialNumber())
                    .subtotal(request.getSubtotal())
                    .vatAmount(request.getVatAmount())
                    .grandTotal(request.getGrandTotal())
                    .qrCode(generateQRCode(fiscalNumber))
                    .fiscalUrl(generateFiscalUrl(fiscalNumber))
                    .status(FiscalReceiptEntity.FiscalStatus.CONFIRMED)
                    .build();
            
            fiscalReceipt = fiscalReceiptRepository.save(fiscalReceipt);
            
            log.info("Fiscal receipt sent successfully: {}", fiscalNumber);
            
            return FiscalReceiptResponse.fromEntity(fiscalReceipt);
            
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
        Optional<FiscalDeviceEntity> device = fiscalDeviceRepository.findBySerialNumber(serialNumber);
        return device.isPresent() && device.get().getStatus() == FiscalDeviceEntity.DeviceStatus.ACTIVE;
    }
    
    @Override
    public boolean isDeviceReady(String serialNumber) {
        return isDeviceConnected(serialNumber);
    }
    
    @Override
    public String generateXReport(String deviceSerialNumber) {
    
        log.info("Generating X report for device: {}", deviceSerialNumber);
        return "X_REPORT_" + deviceSerialNumber + "_" + System.currentTimeMillis();
    }
    
    @Override
    public String generateZReport(String deviceSerialNumber) {
        log.info("Generating Z report for device: {}", deviceSerialNumber);
        return "Z_REPORT_" + deviceSerialNumber + "_" + System.currentTimeMillis();
    }
    
    private String generateFiscalNumber(FiscalDeviceEntity device) {
       
        return "FU-" + device.getSerialNumber() + "-" + 
               LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
               String.format("%06d", (int)(Math.random() * 999999));
    }
    
    private String generateQRCode(String fiscalNumber) {

        return "https://qr.nap.bg/check/" + fiscalNumber;
    }
    
    private String generateFiscalUrl(String fiscalNumber) {

        return "https://fiscal.nap.bg/receipt/" + fiscalNumber;
    }
}
