package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.entity.AuditLogEntity;
import in.bushansirgur.billingsoftware.io.AuditAction;

import java.time.LocalDate;
import java.util.List;

public interface AuditService {

    void record(AuditAction action, String description, String uniqueSaleNumber);

    void recordFor(String actorEmail, AuditAction action, String description, String uniqueSaleNumber);

    List<AuditLogEntity> search(LocalDate from, LocalDate to, String operatorCode, AuditAction action);
}
