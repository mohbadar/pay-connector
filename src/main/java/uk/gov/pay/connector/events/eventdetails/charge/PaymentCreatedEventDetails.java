package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Map;
import java.util.Objects;

public class PaymentCreatedEventDetails extends EventDetails {
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private final Long amount;
    private final String description;
    private final String reference;
    private final String returnUrl;
    private final Long gatewayAccountId;
    private final String paymentProvider;
    private final String language;
    private final boolean delayedCapture;
    private final Map<String, Object> externalMetadata;

    public PaymentCreatedEventDetails(Long amount, String description, String reference, String returnUrl,
                                      Long gatewayAccountId, String paymentProvider, String language,
                                      boolean delayedCapture, Map<String, Object> externalMetadata) {
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.returnUrl = returnUrl;
        this.gatewayAccountId = gatewayAccountId;
        this.paymentProvider = paymentProvider;
        this.language = language;
        this.delayedCapture = delayedCapture;
        this.externalMetadata = externalMetadata;
    }

    public static PaymentCreatedEventDetails from(ChargeEntity charge) {
        return new PaymentCreatedEventDetails(
                charge.getAmount(),
                charge.getDescription(),
                charge.getReference().toString(),
                charge.getReturnUrl(),
                charge.getGatewayAccount().getId(),
                charge.getGatewayAccount().getGatewayName(),
                charge.getLanguage().toString(),
                charge.isDelayedCapture(),
                charge.getExternalMetadata().map(ExternalMetadata::getMetadata).orElse(null));
    }

    public Long getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId.toString();
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isDelayedCapture() {
        return delayedCapture;
    }

    public String getExternalMetadata() throws JsonProcessingException {
        return MAPPER.writeValueAsString(externalMetadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentCreatedEventDetails that = (PaymentCreatedEventDetails) o;
        return Objects.equals(amount, that.amount) &&
                Objects.equals(description, that.description) &&
                Objects.equals(reference, that.reference) &&
                Objects.equals(returnUrl, that.returnUrl) &&
                Objects.equals(gatewayAccountId, that.gatewayAccountId) &&
                Objects.equals(paymentProvider, that.paymentProvider) &&
                Objects.equals(language, that.language) &&
                Objects.equals(delayedCapture, that.delayedCapture) &&
                Objects.equals(externalMetadata, that.externalMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, description, reference, returnUrl, gatewayAccountId, paymentProvider, language,
                delayedCapture, externalMetadata);
    }
}
