package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.io.FiscalReceiptRequest;
import in.bushansirgur.billingsoftware.io.FiscalReceiptResponse;

import java.util.List;

public interface FiscalDeviceService {
    
    List<FiscalDeviceEntity> getAllDevices();
    
    FiscalDeviceEntity getDeviceBySerialNumber(String serialNumber);
    
    FiscalDeviceEntity registerDevice(FiscalDeviceEntity device);
    
    FiscalDeviceEntity updateDevice(FiscalDeviceEntity device);
    
    void deleteDevice(Long deviceId);
    

    FiscalReceiptResponse sendReceiptToFiscalDevice(FiscalReceiptRequest request);
    
    FiscalReceiptResponse getReceiptStatus(String fiscalNumber);
    
    boolean isDeviceConnected(String serialNumber);
    
    boolean isDeviceReady(String serialNumber);
    
    String generateXReport(String deviceSerialNumber);
    
    String generateZReport(String deviceSerialNumber);
}
