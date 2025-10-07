package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.PosPaymentIO;

public interface PosPaymentService {
    PosPaymentIO.InitiateResponse initiate(PosPaymentIO.InitiateRequest request);
    PosPaymentIO.RefundResponse refund(PosPaymentIO.RefundRequest request);
}


