package uk.gov.pay.connector.refund.model.domain;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.sql.Timestamp;
import java.time.ZonedDateTime;

/**
 * Entity which represents Refunds history (a record for each event for a particular refund)
 * Each time a new status is set for RefundEntity a new record is created in RefundsHistory (Refunds_History table in database)
 * which happens magically by @Customizer(HistoryCustomizer.class) annotation on RefundEntity
 */
public class RefundHistory extends RefundEntity {

    private ZonedDateTime historyStartDate;
    private ZonedDateTime historyEndDate;

    public RefundHistory(Long id, String externalId, Long amount, String status, Long chargeId, Timestamp createdDate, 
                         Long version, String reference, Timestamp historyStartDate, Timestamp historyEndDate, 
                         String userExternalId, String gatewayTransactionId, String chargeExternalId, Long gatewayAccountId) {
        super();
        setId(id);
        setExternalId(externalId);
        setAmount(amount);
        setStatus(status);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(gatewayAccountId);
        
        ChargeEntity charge = new ChargeEntity();
        charge.setId(chargeId);
        charge.setExternalId(chargeExternalId);
        charge.setGatewayAccount(gatewayAccount);
        
        setChargeEntity(charge);

        setUserExternalId(userExternalId);
        setCreatedDate(new UTCDateTimeConverter().convertToEntityAttribute(createdDate));
        setVersion(version);
        setReference(reference);
        setGatewayTransactionId(gatewayTransactionId);

        setHistoryStartDate(new UTCDateTimeConverter().convertToEntityAttribute(historyStartDate));
        setHistoryEndDate(new UTCDateTimeConverter().convertToEntityAttribute(historyEndDate));

    }

    public ZonedDateTime getHistoryStartDate() {
        return historyStartDate;
    }

    public void setHistoryStartDate(ZonedDateTime historyStartDate) {
        this.historyStartDate = historyStartDate;
    }

    public ZonedDateTime getHistoryEndDate() {
        return historyEndDate;
    }

    public void setHistoryEndDate(ZonedDateTime historyEndDate) {
        this.historyEndDate = historyEndDate;
    }
}
