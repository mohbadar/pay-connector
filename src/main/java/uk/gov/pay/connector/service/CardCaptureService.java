package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;

public class CardCaptureService extends CardService implements TransactionalGatewayOperation {

    private static ChargeStatus[] legalStatuses = new ChargeStatus[]{
            AUTHORISATION_SUCCESS
    };

    private final PaymentProviders providers;

    @Inject
    public CardCaptureService(ChargeDao chargeDao, PaymentProviders providers) {
        super(chargeDao, providers);
        this.providers = providers;
    }

    public GatewayResponse doCapture(String chargeId) {
        return chargeDao
                .findByExternalId(chargeId)
                .map(TransactionalGatewayOperation.super::executeGatewayOperationFor)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }
    @Transactional
    @Override
    public ChargeEntity preOperation(ChargeEntity chargeEntity) {
        return preOperation(chargeEntity, CardService.OperationType.CAPTURE, legalStatuses, ChargeStatus.CAPTURE_READY);
    }

    @Override
    public GatewayResponse operation(ChargeEntity chargeEntity) {
        return getPaymentProviderFor(chargeEntity)
                .capture(CaptureRequest.valueOf(chargeEntity));
    }

    @Transactional
    @Override
    public GatewayResponse postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CaptureResponse captureResponse = (CaptureResponse) operationResponse;

        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        reloadedCharge.setStatus(captureResponse.getStatus());

        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);

        return operationResponse;
    }
}