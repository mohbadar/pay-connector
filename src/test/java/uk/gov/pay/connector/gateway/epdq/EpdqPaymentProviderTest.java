package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import fj.data.Either;
import org.apache.http.message.BasicNameValuePair;
import org.hamcrest.core.IsNull;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;

@RunWith(MockitoJUnitRunner.class)
public class EpdqPaymentProviderTest extends BaseEpdqPaymentProviderTest {

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("epdq"));
    }

    @Test
    public void shouldGenerateNoTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    public void shouldAuthorise() {
        mockPaymentProviderResponse(200, successAuthResponse());
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());
        verifyPaymentProviderRequest(successAuthRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("3014644340"));
    }

    @Test
    public void shouldNotAuthoriseIfPaymentProviderReturnsUnexpectedStatusCode() {
        mockPaymentProviderResponse(200, errorAuthResponse());
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
    }

    @Test
    public void shouldNotAuthoriseIfPaymentProviderReturnsNon200HttpStatusCode() {
        mockPaymentProviderResponse(400, errorAuthResponse());
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected HTTP status code 400 from gateway",
                UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void shouldCancel() {
        mockPaymentProviderResponse(200, successCancelResponse());
        GatewayResponse<BaseCancelResponse> response = provider.cancel(buildTestCancelRequest());
        verifyPaymentProviderRequest(successCancelRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("3014644340"));
    }

    @Test
    public void shouldNotCancelIfPaymentProviderReturnsUnexpectedStatusCode() {
        mockPaymentProviderResponse(200, errorCancelResponse());
        GatewayResponse<BaseCancelResponse> response = provider.cancel(buildTestCancelRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
    }

    @Test
    public void shouldNotCancelIfPaymentProviderReturnsNon200HttpStatusCode() {
        mockPaymentProviderResponse(400, errorCancelResponse());
        GatewayResponse<BaseCancelResponse> response = provider.cancel(buildTestCancelRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected HTTP status code 400 from gateway",
                UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void shouldRefund() {
        mockPaymentProviderResponse(200, successRefundResponse());
        GatewayResponse<BaseRefundResponse> response = provider.refund(buildTestRefundRequest());
        verifyPaymentProviderRequest(successRefundRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getReference(), is(Optional.of("3014644340/1")));
    }

    @Test
    public void shouldRefundWithPaymentDeletion() {
        mockPaymentProviderResponse(200, successDeletionResponse());
        GatewayResponse<BaseRefundResponse> response = provider.refund(buildTestRefundRequest());
        verifyPaymentProviderRequest(successRefundRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getReference(), is(Optional.of("3014644340/1")));
    }

    @Test
    public void shouldNotRefundIfPaymentProviderReturnsErrorStatusCode() {
        mockPaymentProviderResponse(200, errorRefundResponse());
        GatewayResponse<BaseRefundResponse> response = provider.refund(buildTestRefundRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
    }

    @Test
    public void shouldNotRefundIfPaymentProviderReturnsNon200HttpStatusCode() {
        mockPaymentProviderResponse(400, errorRefundResponse());
        GatewayResponse<BaseRefundResponse> response = provider.refund(buildTestRefundRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected HTTP status code 400 from gateway",
                UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void shouldVerifyNotificationSignature() {
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(Collections.singletonMap(CREDENTIALS_SHA_OUT_PASSPHRASE, "passphrase"));

        when(mockNotification.getPayload()).thenReturn(Optional.of(Arrays.asList(
                new BasicNameValuePair("key1", "value1"),
                new BasicNameValuePair("SHASIGN", "signature")
        )));

        when(mockSignatureGenerator.sign(Collections.singletonList(new BasicNameValuePair
                ("key1", "value1")), "passphrase")).thenReturn("signature");

        assertThat(provider.verifyNotification(mockNotification, mockGatewayAccountEntity), is(true));
    }

    @Test
    public void shouldVerifyNotificationSignatureIgnoringCase() {
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(Collections.singletonMap(CREDENTIALS_SHA_OUT_PASSPHRASE, "passphrase"));

        when(mockNotification.getPayload()).thenReturn(Optional.of(Arrays.asList(
                new BasicNameValuePair("key1", "value1"),
                new BasicNameValuePair("SHASIGN", "SIGNATURE")
        )));

        when(mockSignatureGenerator.sign(Collections.singletonList(new BasicNameValuePair
                ("key1", "value1")), "passphrase")).thenReturn("signature");

        assertThat(provider.verifyNotification(mockNotification, mockGatewayAccountEntity), is(true));
    }

    @Test
    public void shouldNotVerifyNotificationIfWrongSignature() {
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(Collections.singletonMap(CREDENTIALS_SHA_OUT_PASSPHRASE, "passphrase"));

        when(mockNotification.getPayload()).thenReturn(Optional.of(Arrays.asList(
                new BasicNameValuePair("key1", "value1"),
                new BasicNameValuePair("SHASIGN", "signature")
        )));

        when(mockSignatureGenerator.sign(Collections.singletonList(new BasicNameValuePair
                ("key1", "value1")), "passphrase")).thenReturn("wrong signature");

        assertThat(provider.verifyNotification(mockNotification, mockGatewayAccountEntity), is(false));
    }

    @Test
    public void shouldNotVerifyNotificationIfEmptyPayload() {
        when(mockNotification.getPayload()).thenReturn(Optional.empty());

        assertThat(provider.verifyNotification(mockNotification, mockGatewayAccountEntity), is(false));
    }

    @Test
    public void parseNotification_shouldReturnNotificationsIfValidFormUrlEncoded() throws IOException {
        Either<String, Notifications<String>> response =
                provider.parseNotification(notificationPayloadForTransaction(NOTIFICATION_STATUS, NOTIFICATION_PAY_ID, NOTIFICATION_PAY_ID_SUB, NOTIFICATION_SHA_SIGN));

        assertThat(response.isRight(), is(true));

        ImmutableList<Notification<String>> notifications = response.right().value().get();

        assertThat(notifications.size(), is(1));

        Notification<String> notification = notifications.get(0);

        assertThat(notification.getTransactionId(), is(NOTIFICATION_PAY_ID));
        assertThat(notification.getReference(), is(NOTIFICATION_PAY_ID + "/" + NOTIFICATION_PAY_ID_SUB));
        assertThat(notification.getStatus(), is(NOTIFICATION_STATUS));
        assertThat(notification.getGatewayEventDate(), IsNull.nullValue());
    }
}
