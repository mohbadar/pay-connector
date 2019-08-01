package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class CaptureSubmittedTest {
    private final ChargeEntityFixture chargeEntity = aValidChargeEntity();


    @Test
    public void serializesEventDetailsGivenChargeEvent() throws JsonProcessingException {
        ZonedDateTime updated = ZonedDateTime.parse("2018-03-12T16:25:01.123456Z");
        Long fee = 5L;

        chargeEntity.withFee(fee);
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getUpdated()).thenReturn(updated);

        String actual = CaptureSubmitted.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("CAPTURE_SUBMITTED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEvent.getChargeEntity().getExternalId())));

        assertThat(actual, hasJsonPath("$.event_details.capture_submit_date", equalTo("2018-03-12T16:25:01.123456Z")));
    }
}
