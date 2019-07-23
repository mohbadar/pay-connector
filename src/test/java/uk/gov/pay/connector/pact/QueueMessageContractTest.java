package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import au.com.dius.pact.provider.junit.target.AmqpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.runner.RunWith;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentDetailsEnteredEventDetails;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByUser;
import uk.gov.pay.connector.events.model.refund.RefundSubmitted;
import uk.gov.pay.connector.events.model.refund.RefundSucceeded;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;

import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.pact.RefundHistoryEntityFixture.aValidRefundHistoryEntity;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "pact-broker-test.cloudapps.digital", tags = {"${PACT_CONSUMER_TAG}"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"),
        consumers = {"ledger"})
@IgnoreNoPactsToVerify
public class QueueMessageContractTest {

    @TestTarget
    public final Target target = new AmqpTarget();

    private String resourceId = "anExternalResourceId";

    @PactVerifyProvider("a payment created message")
    public String verifyPaymentCreatedEvent() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withExternalMetadata(new ExternalMetadata(ImmutableMap.of("key", "value")))
                .withCorporateSurcharge(55L)
                .build();

        PaymentCreated paymentCreatedEvent = new PaymentCreated(
                resourceId,
                PaymentCreatedEventDetails.from(charge),
                ZonedDateTime.now()
        );

        return paymentCreatedEvent.toJsonString();
    }

    @PactVerifyProvider("a capture confirmed message")
    public String verifyCaptureConfirmedEvent() throws JsonProcessingException {
        ChargeEventEntity chargeEventEntity = ChargeEventEntityFixture
                .aValidChargeEventEntity()
                .withGatewayEventDate(ZonedDateTime.now())
                .build();

        CaptureConfirmed captureConfirmedEvent = new CaptureConfirmed(
                resourceId,
                CaptureConfirmedEventDetails.from(chargeEventEntity),
                ZonedDateTime.now()
        );

        return captureConfirmedEvent.toJsonString();
    }

    @PactVerifyProvider("a payment details entered message")
    public String verifyPaymentDetailsEnteredEvent() throws JsonProcessingException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withTransactionId("gateway_transaction_id")
                .withCorporateSurcharge(55L)
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .build();

        PaymentDetailsEntered captureConfirmedEvent = new PaymentDetailsEntered(
                resourceId,
                PaymentDetailsEnteredEventDetails.from(charge),
                ZonedDateTime.now()
        );

        return captureConfirmedEvent.toJsonString();
    }

    @PactVerifyProvider("a refund created by user message")
    public String verifyRefundCreatedByUserEvent() throws JsonProcessingException {
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .withUserExternalId(RandomStringUtils.randomAlphanumeric(10))
                .build();
        RefundCreatedByUser refundCreatedByUser = RefundCreatedByUser.from(refundHistory);

        return refundCreatedByUser.toJsonString();
    }

    @PactVerifyProvider("a refund submitted message")
    public String verifyRefundSubmittedEvent() throws JsonProcessingException {
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .build();
        RefundSubmitted refundSubmitted = RefundSubmitted.from(refundHistory);

        return refundSubmitted.toJsonString();
    }

    @PactVerifyProvider("a refund succeeded message")
    public String verifyRefundedEvent() throws JsonProcessingException {
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUNDED.getValue())
                .withReference(RandomStringUtils.randomAlphanumeric(14))
                .build();
        RefundSucceeded refundSucceeded = RefundSucceeded.from(refundHistory);

        return refundSucceeded.toJsonString();
    }
}
