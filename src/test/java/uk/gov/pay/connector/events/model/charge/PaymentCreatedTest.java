package uk.gov.pay.connector.events.model.charge;

import org.junit.Before;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class PaymentCreatedTest {

    private final String paymentId = "jweojfewjoifewj";
    private final String time = "2018-03-12T16:25:01.123456Z";

    private final ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
            .withCreatedDate(ZonedDateTime.parse(time))
            .withExternalId(paymentId)
            .withDescription("new passport")
            .withReference(ServicePaymentReference.of("myref"))
            .withReturnUrl("http://example.com")
            .withAmount(100L)
            .withCorporateSurcharge(77L)
            .withExternalMetadata(new ExternalMetadata(ImmutableMap.of("key1", "value1", "key2", "value2")))
            .build();

    private final PaymentCreated paymentCreatedEvent = PaymentCreated.from(chargeEntity);
    private String actual;


    @Before
    public void setup() throws Exception {
        actual = paymentCreatedEvent.toJsonString();
    }

    @Test
    public void serializesTimeWithMicrosecondPrecision() {
        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
    }

    @Test
    public void serializesPaymentCreatedEventType() {
        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_CREATED")));
    }

    @Test
    public void serializesPaymentResourceType() {
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
    }

    @Test
    public void serializesPaymentResourceExternaId() {
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
    }

    @Test
    public void serializesPayloadFieldstoJsonString() {
        assertThat(actual, hasJsonPath("$.event_details.amount", equalTo(100)));
        assertThat(actual, hasJsonPath("$.event_details.description", equalTo("new passport")));
        assertThat(actual, hasJsonPath("$.event_details.reference", equalTo("myref")));
        assertThat(actual, hasJsonPath("$.event_details.return_url", equalTo("http://example.com")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_account_id", equalTo(chargeEntity.getGatewayAccount().getId().toString())));
        assertThat(actual, hasJsonPath("$.event_details.payment_provider", equalTo(chargeEntity.getGatewayAccount().getGatewayName())));
        assertThat(actual, hasJsonPath("$.event_details.language", equalTo(chargeEntity.getLanguage().toString())));
        assertThat(actual, hasJsonPath("$.event_details.delayed_capture", equalTo(chargeEntity.isDelayedCapture())));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata", equalTo("{\"key1\":\"value1\",\"key2\":\"value2\"}")));
    }
}
