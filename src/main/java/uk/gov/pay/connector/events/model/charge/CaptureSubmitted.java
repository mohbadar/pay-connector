package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureSubmittedEventDetails;

import java.time.ZonedDateTime;

public class CaptureSubmitted extends PaymentEvent {
    public CaptureSubmitted(String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static CaptureSubmitted from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();

        return new CaptureSubmitted(
                charge.getExternalId(),
                CaptureSubmittedEventDetails.from(chargeEvent),
                chargeEvent.getUpdated()
        );
    }
}
