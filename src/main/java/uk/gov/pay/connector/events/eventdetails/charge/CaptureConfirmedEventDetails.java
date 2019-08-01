package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.MicrosecondPrecisionDateTimeSerializer;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class CaptureConfirmedEventDetails extends EventDetails {
    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    private final ZonedDateTime gatewayEventDate;
    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    private final ZonedDateTime capturedDate;
    private final Long fee;
    private final Long netAmount;


    public CaptureConfirmedEventDetails(ZonedDateTime gatewayEventDate, ZonedDateTime capturedDate, Long fee, Long netAmount) {
        this.gatewayEventDate = gatewayEventDate;
        this.capturedDate = capturedDate;
        this.fee = fee;
        this.netAmount = netAmount;
    }

    public static CaptureConfirmedEventDetails from(ChargeEventEntity chargeEvent) {
        return new CaptureConfirmedEventDetails(
                chargeEvent.getGatewayEventDate().orElse(null),
                chargeEvent.getUpdated(),
                chargeEvent.getChargeEntity().getFeeAmount().orElse(null),
                chargeEvent.getChargeEntity().getNetAmount().orElse(null)
        );
    }

    public ZonedDateTime getCapturedDate() {
        return capturedDate;
    }

    public ZonedDateTime getGatewayEventDate() {
        return gatewayEventDate;
    }

    public Long getFee() {
        return fee;
    }

    public Long getNetAmount() {
        return netAmount;
    }
}
