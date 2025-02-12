package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;

import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SearchChargesIT {

    @DropwizardTestContext
    private TestContext testContext;
    private DatabaseTestHelper databaseTestHelper;
    private String accountId;
    private RestAssuredClient connectorRestApiClient;

    @Before
    public void setupGatewayAccount() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(nextLong());
        databaseTestHelper.addGatewayAccount(accountId, "sandbox");
        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);
    }

    @Test
    public void searchChargesReturnsExpectedGatewayTransactionId() {
        addCharge("txId-1234", ChargeStatus.CAPTURED);
        
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("payment_states", "captured")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("results[0].gateway_transaction_id", Is.is("txId-1234"));
    }

    private void addCharge(String gatewayTransactionId, ChargeStatus chargeStatus) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(chargeStatus)
                .withTransactionId(gatewayTransactionId)
                .build());
    }
}
