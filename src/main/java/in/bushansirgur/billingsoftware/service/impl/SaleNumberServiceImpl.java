package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.SaleSequenceEntity;
import in.bushansirgur.billingsoftware.repository.SaleSequenceRepository;
import in.bushansirgur.billingsoftware.service.SaleNumberService;
import in.bushansirgur.billingsoftware.service.UniqueSaleNumber;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SaleNumberServiceImpl implements SaleNumberService {

    private final SaleSequenceRepository saleSequenceRepository;
    private final TransactionTemplate newTransaction;

    public SaleNumberServiceImpl(SaleSequenceRepository saleSequenceRepository,
                                  PlatformTransactionManager transactionManager) {
        this.saleSequenceRepository = saleSequenceRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public String next(String individualNumber, String operatorCode) {
        if (!saleSequenceRepository.existsById(individualNumber)) {
            try {
                newTransaction.executeWithoutResult(status -> saleSequenceRepository.saveAndFlush(
                        SaleSequenceEntity.builder()
                                .individualNumber(individualNumber)
                                .lastNumber(0L)
                                .build()));
            } catch (DataIntegrityViolationException alreadyCreated) {
                // Another sale created the counter first.
            }
        }
        SaleSequenceEntity sequence = saleSequenceRepository.findForUpdate(individualNumber)
                .orElseThrow(() -> new IllegalStateException("Липсва брояч за устройство " + individualNumber));
        long next = sequence.getLastNumber() + 1;
        sequence.setLastNumber(next);
        saleSequenceRepository.save(sequence);
        return UniqueSaleNumber.format(individualNumber, operatorCode, next);
    }
}
