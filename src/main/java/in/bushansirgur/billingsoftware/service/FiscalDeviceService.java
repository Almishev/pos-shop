package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.io.FiscalReceiptRequest;
import in.bushansirgur.billingsoftware.io.FiscalReceiptResponse;

import java.util.List;

public interface FiscalDeviceService {
    
    // Фискално устройство операции
    List<FiscalDeviceEntity> getAllDevices();
    
    FiscalDeviceEntity getDeviceBySerialNumber(String serialNumber);
    
    FiscalDeviceEntity registerDevice(FiscalDeviceEntity device);
    
    FiscalDeviceEntity updateDevice(FiscalDeviceEntity device);
    
    void deleteDevice(Long deviceId);
    
    // Фискални разписки
    FiscalReceiptResponse sendReceiptToFiscalDevice(FiscalReceiptRequest request);
    
    FiscalReceiptResponse getReceiptStatus(String fiscalNumber);
    
    // Проверка на статуса на устройството
    boolean isDeviceConnected(String serialNumber);
    
    boolean isDeviceReady(String serialNumber);
    
    // Генериране на отчети
    String generateXReport(String deviceSerialNumber);
    
    String generateZReport(String deviceSerialNumber);
}
