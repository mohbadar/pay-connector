package uk.gov.pay.connector.gateway.util;

import com.google.common.collect.ImmutableList;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.util.RefundCalculator;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;

import java.util.List;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.UNDEFINED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_FULL;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_PENDING;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;

public class DefaultExternalRefundAvailabilityCalculator implements ExternalRefundAvailabilityCalculator {

    private static final List<ChargeStatus> STATUSES_THAT_MAP_TO_EXTERNAL_PENDING = ImmutableList.of(
            UNDEFINED,
            CREATED,
            ENTERING_CARD_DETAILS,
            AUTHORISATION_READY,
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_3DS_READY,
            AUTHORISATION_SUBMITTED,
            AUTHORISATION_SUCCESS,
            CAPTURE_READY,
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY,
            CAPTURE_SUBMITTED
    );
    private static final List<ChargeStatus> STATUSES_THAT_MAP_TO_EXTERNAL_AVAILABLE_OR_EXTERNAL_FULL = ImmutableList.of(CAPTURED);

    @Override
    public ExternalChargeRefundAvailability calculate(ChargeEntity chargeEntity) {
        return calculate(chargeEntity, STATUSES_THAT_MAP_TO_EXTERNAL_PENDING, STATUSES_THAT_MAP_TO_EXTERNAL_AVAILABLE_OR_EXTERNAL_FULL);
    }

    protected ExternalChargeRefundAvailability calculate(ChargeEntity chargeEntity, List<ChargeStatus> statusesThatMapToExternalPending,
                                                         List<ChargeStatus> statusesThatMapToExternalAvailableOrExternalFull) {
        if (chargeIsPending(chargeEntity, statusesThatMapToExternalPending)) {
            return EXTERNAL_PENDING;
        } else if (chargeIsAvailableOrFull(chargeEntity, statusesThatMapToExternalAvailableOrExternalFull)) {
            if (RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity) > 0) {
                return EXTERNAL_AVAILABLE;
            } else {
                return EXTERNAL_FULL;
            }
        }
        return EXTERNAL_UNAVAILABLE;
    }

    private boolean chargeIsAvailableOrFull(ChargeEntity chargeEntity, List<ChargeStatus> statusesThatMapToExternalAvailableOrExternalFull) {
        return statusesThatMapToExternalAvailableOrExternalFull.contains(ChargeStatus.fromString(chargeEntity.getStatus()));
    }

    private boolean chargeIsPending(ChargeEntity chargeEntity, List<ChargeStatus> statusesThatMapToExternalPending) {
        return statusesThatMapToExternalPending.contains(ChargeStatus.fromString(chargeEntity.getStatus()));
    }
}
