package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.Annex29TableResponse;

import java.time.LocalDate;

public interface Annex29ExportService {

    Annex29TableResponse table(String code, LocalDate from, LocalDate to, String device, String operatorCode);

    byte[] csv(Annex29TableResponse table);
}
