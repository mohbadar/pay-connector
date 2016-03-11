package uk.gov.pay.connector.it.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.util.TestClientFactory;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.io.Resources.getResource;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.fixture.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

public class SmartpayPaymentProviderTest {

    private String url = "https://pal-test.barclaycardsmartpay.com/pal/servlet/soap/Payment";
    private String username = envOrThrow("GDS_CONNECTOR_SMARTPAY_USER");
    private String password = envOrThrow("GDS_CONNECTOR_SMARTPAY_PASSWORD");
    private GatewayAccountEntity validGatewayAccount;

    @Before
    public void setUpAndCheckThatSmartpayIsUp() {
        try {
            new URL(url).openConnection().connect();
            Map<String, String> validSmartPayCredentials = ImmutableMap.of(
                    "username", username,
                    "password", password);
            validGatewayAccount = new GatewayAccountEntity();
            validGatewayAccount.setId(123L);
            validGatewayAccount.setGatewayName("smartpay");
            validGatewayAccount.setCredentials(validSmartPayCredentials);
        } catch (IOException ex) {
            Assume.assumeTrue(false);
        }
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        testCardAuthorisation(paymentProvider);
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        GatewayAccountEntity accountWithInvalidCredentials = new GatewayAccountEntity();
        accountWithInvalidCredentials.setId(11L);
        accountWithInvalidCredentials.setGatewayName("smartpay");
        accountWithInvalidCredentials.setCredentials(ImmutableMap.of(
                "username", "wrong-username",
                "password", "wrong-password"
        ));

        AuthorisationRequest request = getCardAuthorisationRequest(accountWithInvalidCredentials);
        AuthorisationResponse response = paymentProvider.authorise(request);

        assertFalse(response.isSuccessful());
        assertNotNull(response.getError());
    }

    @Test
    public void shouldSuccessfullySendACaptureRequest() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withTransactionId(response.getTransactionId())
                .withGatewayAccountEntity(validGatewayAccount)
                .build();

        CaptureResponse captureResponse = paymentProvider.capture(CaptureRequest.valueOf(chargeEntity));
        assertTrue(captureResponse.isSuccessful());
        assertNull(captureResponse.getError());
    }

    @Test
    public void shouldSuccessfullySendACancelRequest() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withTransactionId(response.getTransactionId())
                .withGatewayAccountEntity(validGatewayAccount)
                .build();

        CancelResponse cancelResponse = paymentProvider.cancel(CancelRequest.valueOf(chargeEntity));
        assertTrue(cancelResponse.isSuccessful());
        assertNull(cancelResponse.getError());

    }

    @Test
    public void shouldBeAbleToHandleNotification() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        Consumer<StatusUpdates> accountUpdater = mock(Consumer.class);

        String transactionId = response.getTransactionId();
        StatusUpdates statusResponse = paymentProvider.handleNotification(
                notificationPayloadForTransaction(transactionId),
                x -> true,
                x -> Optional.of(validGatewayAccount),
                accountUpdater
        );

        assertThat(statusResponse.getStatusUpdates(), hasItem(Pair.of(transactionId, CAPTURED)));
    }

    private AuthorisationResponse testCardAuthorisation(PaymentProvider paymentProvider) {
        AuthorisationRequest request = getCardAuthorisationRequest(validGatewayAccount);
        AuthorisationResponse response = paymentProvider.authorise(request);
        assertTrue(response.isSuccessful());

        return response;
    }

    private PaymentProvider getSmartpayPaymentProvider() throws Exception {
        Client client = TestClientFactory.createJerseyClient();
        GatewayClient gatewayClient = createGatewayClient(client, url);
        return new SmartpayPaymentProvider(gatewayClient, new ObjectMapper());
    }

    private String notificationPayloadForTransaction(String transactionId) throws IOException {
        URL resource = getResource("templates/smartpay/notification-capture.json");
        return Resources.toString(resource, Charset.defaultCharset()).replace("{{transactionId}}", transactionId);
    }

    public static AuthorisationRequest getCardAuthorisationRequest(GatewayAccountEntity gatewayAccount) {
        Address address = Address.anAddress();
        address.setLine1("41");
        address.setLine2("Scala Street");
        address.setCity("London");
        address.setCounty("London");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        Card card = aValidSmartpayCard();
        card.setAddress(address);

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount).build();

        return new AuthorisationRequest(chargeEntity, card);
    }

    public static Card aValidSmartpayCard() {
        String validSandboxCard = "5555444433331111";
        return buildCardDetails(validSandboxCard, "737", "08/18");
    }
}
