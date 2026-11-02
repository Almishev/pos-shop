package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.entity.FiscalDeviceEntity;
import in.bushansirgur.billingsoftware.entity.OrderEntity;
import in.bushansirgur.billingsoftware.entity.OrderItemEntity;
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

    /**
     * Prints a reversal receipt on ErpNet.FP for the original sale.
     * Uses the same unique sale number. Quantities on {@code refundLines} may be negative.
     */
    void printReversalReceipt(OrderEntity original, List<OrderItemEntity> refundLines);

    /**
     * 8-digit manufacturer number for the unique sale number.
     * Copies an 8-digit serial when the field is still empty.
     */
    String requireIndividualNumber(String serialNumber);
}
