package in.bushansirgur.billingsoftware.service;

public interface SaleNumberService {

    /** Next unique sale number for this fiscal device and operator. Increments the device counter by 1. */
    String next(String individualNumber, String operatorCode);
}
