package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.AuditLogEntity;
import in.bushansirgur.billingsoftware.entity.UserEntity;
import in.bushansirgur.billingsoftware.io.AuditAction;
import in.bushansirgur.billingsoftware.repository.AuditLogRepository;
import in.bushansirgur.billingsoftware.repository.UserRepository;
import in.bushansirgur.billingsoftware.service.AuditService;
import in.bushansirgur.billingsoftware.util.OperatorNames;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void record(AuditAction action, String description, String uniqueSaleNumber) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            email = auth.getName();
        }
        recordFor(email, action, description, uniqueSaleNumber);
    }

    @Override
    @Transactional
    public void recordFor(String actorEmail, AuditAction action, String description, String uniqueSaleNumber) {
        UserEntity actor = actorEmail == null ? null : userRepository.findByEmail(actorEmail).orElse(null);
        String text = description == null ? "" : description;
        if (text.length() > 4000) {
            text = text.substring(0, 4000);
        }
        auditLogRepository.save(AuditLogEntity.builder()
                .operatorName(actor == null ? "система" : OperatorNames.display(actor))
                .operatorCode(actor == null ? null : actor.getOperatorCode())
                .role(actor == null ? null : actor.getRole())
                .occurredAt(LocalDateTime.now())
                .action(action)
                .description(text)
                .uniqueSaleNumber(uniqueSaleNumber)
                .build());
    }

    @Override
    public List<AuditLogEntity> search(LocalDate from, LocalDate to, String operatorCode, AuditAction action) {
        LocalDateTime start = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now() : to).atTime(23, 59, 59);
        String code = operatorCode == null ? "" : operatorCode.trim();
        return auditLogRepository.findByOccurredAtBetweenOrderByOccurredAtDesc(start, end).stream()
                .filter(row -> code.isEmpty() || code.equals(row.getOperatorCode()))
                .filter(row -> action == null || action == row.getAction())
                .toList();
    }
}
