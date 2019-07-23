package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PaymentOutcome {

    @JsonProperty("status")
    private String status;

    @JsonProperty("code")
    private String code;

    @JsonProperty("supplemental")
    private Supplemental supplemental;

    public PaymentOutcome() {
    }

    public PaymentOutcome(String status, String code, Supplemental supplemental) {
        // For testing deserialization
        this.status = status;
        this.code = code;
        this.supplemental = supplemental;
    }

    public String getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Supplemental getSupplemental() {
        return supplemental;
    }
}
