package uk.gov.pay.connector.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.util.CardUtils;

import javax.persistence.OptimisticLockException;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureServiceTest extends CardServiceTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();
    @Mock
    private ConfirmationDetailsService mockConfirmationDetailsService;
    @Mock
    private UserNotificationService mockUserNotificationService;
    private CardCaptureService cardCaptureService;

    @Before
    public void beforeTest() {
        cardCaptureService = new CardCaptureService(mockedChargeDao, mockedProviders, mockConfirmationDetailsService, mockUserNotificationService);
    }

    public void setupPaymentProviderMock(String transactionId, String errorCode) {
        WorldpayCaptureResponse worldpayResponse = mock(WorldpayCaptureResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponse captureResponse = GatewayResponse.with(worldpayResponse);
        when(mockedPaymentProvider.capture(any())).thenReturn(captureResponse);
    }

    @Test
    public void shouldCaptureAChargeForANonSandboxAccount() throws Exception {
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith("worldpay",1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity reloadedCharge = spy(charge);
        mockChargeDaoOperations(charge, reloadedCharge);

        setupPaymentProviderMock(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(reloadedCharge);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_READY);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_SUBMITTED);

        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).notifyPaymentSuccessEmail(reloadedCharge);
    }

    @Test
    public void shouldCaptureAChargeForASandboxAccount() throws Exception {
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith("sandbox",1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity reloadedCharge = spy(charge);
        mockChargeDaoOperations(charge, reloadedCharge);

        setupPaymentProviderMock(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(reloadedCharge);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_READY);
        inOrder.verify(reloadedCharge).setStatus(CAPTURED);

        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getStatus(), is(CAPTURED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).notifyPaymentSuccessEmail(reloadedCharge);
    }

    private void mockChargeDaoOperations(ChargeEntity charge, ChargeEntity reloadedCharge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(reloadedCharge)
                .thenReturn(reloadedCharge);
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldGetAChargeNotFoundWhenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());
        cardCaptureService.doCapture(chargeId);
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void shouldGetAOperationAlreadyInProgressWhenStatusIsCaptureReady() throws Exception {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CAPTURE_READY);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);
        exception.expect(OperationAlreadyInProgressRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertEquals(charge.getStatus(), is(ChargeStatus.CAPTURE_READY.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void shouldGetAIllegalErrorWhenInvalidStatus() throws Exception {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        exception.expect(IllegalStateRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertEquals(charge.getStatus(), is(ChargeStatus.CREATED.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void shouldGetAConflictErrorWhenConflicting() throws Exception {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenThrow(new OptimisticLockException());
        exception.expect(ConflictRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertEquals(charge.getStatus(), is(ChargeStatus.CREATED.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void shouldUpdateChargeWithCaptureErrorWhenCaptureFails() {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, gatewayTxId);
        ChargeEntity reloadedCharge = spy(charge);

        mockChargeDaoOperations(charge, reloadedCharge);

        setupPaymentProviderMock(gatewayTxId, "error-code");
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response.isFailed(), is(true));

        InOrder inOrder = Mockito.inOrder(reloadedCharge);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_READY);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_ERROR);

        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void shouldRemoveConfirmationDetailsIfCaptureReady() {
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity reloadedCharge = spy(charge);
        mockChargeDaoOperations(charge, reloadedCharge);
        setupPaymentProviderMock(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        cardCaptureService.doCapture(charge.getExternalId());
        verify(mockConfirmationDetailsService, times(1)).doRemove(reloadedCharge);
    }

    @Test
    public void shouldRemoveConfirmationDetailsIfCaptureFails() {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity reloadedCharge = spy(charge);
        mockChargeDaoOperations(charge, reloadedCharge);
        setupPaymentProviderMock(gatewayTxId, "error-code");
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        cardCaptureService.doCapture(charge.getExternalId());
        verify(mockConfirmationDetailsService, times(1)).doRemove(reloadedCharge);
    }
}