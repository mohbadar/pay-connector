package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Supplemental {

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    public Supplemental() {
    }

    public Supplemental(String errorCode, String errorMessage) {
        // For testing deserialization
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
