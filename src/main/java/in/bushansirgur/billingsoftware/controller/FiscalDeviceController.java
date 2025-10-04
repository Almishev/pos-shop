package in.bushansirgur.billingsoftware.controller;

import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.io.FiscalReceiptRequest;
import in.bushansirgur.billingsoftware.io.FiscalReceiptResponse;
import in.bushansirgur.billingsoftware.service.FiscalDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FiscalDeviceController {
    
    private final FiscalDeviceService fiscalDeviceService;
    
    @GetMapping("/fiscal-devices")
    public ResponseEntity<List<FiscalDeviceEntity>> getAllDevices() {
        System.out.println("=== FiscalDeviceController.getAllDevices called ===");
        System.out.println("Request received for /api/v1.0/admin/fiscal-devices");
        
        try {
            List<FiscalDeviceEntity> devices = fiscalDeviceService.getAllDevices();
            System.out.println("Found " + devices.size() + " fiscal devices");
            for (FiscalDeviceEntity device : devices) {
                System.out.println("Device: " + device.getSerialNumber() + " - " + device.getStatus());
            }
            return ResponseEntity.ok(devices);
        } catch (Exception e) {
            System.err.println("Error in getAllDevices: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @GetMapping("/devices/{serialNumber}")
    public ResponseEntity<FiscalDeviceEntity> getDeviceBySerialNumber(@PathVariable String serialNumber) {
        return ResponseEntity.ok(fiscalDeviceService.getDeviceBySerialNumber(serialNumber));
    }
    
    @PostMapping("/devices")
    public ResponseEntity<FiscalDeviceEntity> registerDevice(@RequestBody FiscalDeviceEntity device) {
        return ResponseEntity.ok(fiscalDeviceService.registerDevice(device));
    }
    
    @PutMapping("/devices")
    public ResponseEntity<FiscalDeviceEntity> updateDevice(@RequestBody FiscalDeviceEntity device) {
        return ResponseEntity.ok(fiscalDeviceService.updateDevice(device));
    }
    
    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long deviceId) {
        fiscalDeviceService.deleteDevice(deviceId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/receipts")
    public ResponseEntity<FiscalReceiptResponse> sendReceiptToFiscalDevice(@RequestBody FiscalReceiptRequest request) {
        return ResponseEntity.ok(fiscalDeviceService.sendReceiptToFiscalDevice(request));
    }
    
    @GetMapping("/receipts/{fiscalNumber}")
    public ResponseEntity<FiscalReceiptResponse> getReceiptStatus(@PathVariable String fiscalNumber) {
        return ResponseEntity.ok(fiscalDeviceService.getReceiptStatus(fiscalNumber));
    }
    
    @GetMapping("/devices/{serialNumber}/status")
    public ResponseEntity<Boolean> isDeviceConnected(@PathVariable String serialNumber) {
        return ResponseEntity.ok(fiscalDeviceService.isDeviceConnected(serialNumber));
    }
    
    @GetMapping("/devices/{serialNumber}/ready")
    public ResponseEntity<Boolean> isDeviceReady(@PathVariable String serialNumber) {
        return ResponseEntity.ok(fiscalDeviceService.isDeviceReady(serialNumber));
    }
    
    @PostMapping("/devices/{serialNumber}/x-report")
    public ResponseEntity<String> generateXReport(@PathVariable String serialNumber) {
        return ResponseEntity.ok(fiscalDeviceService.generateXReport(serialNumber));
    }
    
    @PostMapping("/devices/{serialNumber}/z-report")
    public ResponseEntity<String> generateZReport(@PathVariable String serialNumber) {
        return ResponseEntity.ok(fiscalDeviceService.generateZReport(serialNumber));
    }
}
