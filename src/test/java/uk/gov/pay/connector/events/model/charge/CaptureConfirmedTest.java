package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.Optional;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class CaptureConfirmedTest {

    private final ChargeEntityFixture chargeEntity = aValidChargeEntity();

    @Test
    public void serializesEventDetailsGivenChargeEvent() throws JsonProcessingException {
        ZonedDateTime gatewayEventTime = ZonedDateTime.parse("2018-03-12T16:25:01.123456Z");
        ZonedDateTime updated = ZonedDateTime.parse("2018-03-12T16:25:02.123456Z");
        Long fee = 5L;

        chargeEntity.withFee(fee);
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.of(gatewayEventTime));
        when(chargeEvent.getUpdated()).thenReturn(updated);

        String actual = CaptureConfirmed.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("CAPTURE_CONFIRMED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEvent.getChargeEntity().getExternalId())));

        assertThat(actual, hasJsonPath("$.event_details.gateway_event_date", equalTo("2018-03-12T16:25:01.123456Z")));
        assertThat(actual, hasJsonPath("$.event_details.fee", equalTo(5)));
        assertThat(actual, hasJsonPath("$.event_details.net_amount", equalTo(495)));
        assertThat(actual, hasJsonPath("$.event_details.captured_date", equalTo("2018-03-12T16:25:02.123456Z")));
    }

    @Test
    public void serializesEventGivenNoDetailValues() throws JsonProcessingException {
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.empty());

        String actual = CaptureConfirmed.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("CAPTURE_CONFIRMED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEvent.getChargeEntity().getExternalId())));

        assertThat(actual, hasNoJsonPath("$.event_details.gateway_event_date"));
        assertThat(actual, hasNoJsonPath("$.event_details.fee"));
        assertThat(actual, hasNoJsonPath("$.event_details.net_amount"));
    }

}
