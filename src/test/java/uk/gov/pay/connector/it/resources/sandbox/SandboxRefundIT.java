package uk.gov.pay.connector.it.resources.sandbox;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SandboxRefundIT extends ChargingITestBase {

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;

    public SandboxRefundIT() {
        super("sandbox");
    }

    @Before
    public void setUp() {
        super.setUp();
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(Long.valueOf(accountId));

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withAmount(100L)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .insert();
    }

    @Test
    public void shouldBeAbleToRequestARefund_partialAmount() {
        Long refundAmount = 50L;
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());

        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getChargeId(), refundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(defaultTestCharge);
    }

    @Test
    public void shouldBeAbleToRefundTwoRequestsWhereAmountAvailableMatch() {
        Long refundAmount = 50L;
        //first refund request
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        //second refund request with updated refundAmountAvailable
        validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount() - refundAmount);
        String refundId_2 = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(2));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getChargeId(), refundAmount, "REFUNDED")));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId_2, is(notNullValue()), defaultTestCharge.getChargeId(), refundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForTwoRefunds(defaultTestCharge);
    }

    @Test
    public void shouldRespond_412_WhenSecondRefundRequestAmountAvailableMismatches() {
        Long refundAmount = 50L;
        //first refund request
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        //second refund request with wrong refundAmountAvailable
        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
            .statusCode(PRECONDITION_FAILED.getStatusCode())
            .body("message", contains("Refund Amount Available Mismatch"))
            .body("error_identifier", is(ErrorIdentifier.REFUND_AMOUNT_AVAILABLE_MISMATCH.toString()));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getChargeId(), refundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(defaultTestCharge);
    }

    @Test
    public void shouldBeAbleToRequestARefund_fullAmount() {

        Long refundAmount = defaultTestCharge.getAmount();

        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getChargeId(), refundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(defaultTestCharge);
    }

    @Test
    public void shouldBeAbleToRequestARefund_multiplePartialAmounts_andRefundShouldBeInFullStatus() {
        Long firstRefundAmount = 80L;
        Long secondRefundAmount = 20L;
        Long chargeId = defaultTestCharge.getChargeId();
        String externalChargeId = defaultTestCharge.getExternalChargeId();

        ValidatableResponse firstValidatableResponse = postRefundFor(externalChargeId, firstRefundAmount, defaultTestCharge.getAmount());
        String firstRefundId = assertRefundResponseWith(firstRefundAmount, firstValidatableResponse, ACCEPTED.getStatusCode());

        ValidatableResponse secondValidatableResponse = postRefundFor(externalChargeId, secondRefundAmount, defaultTestCharge.getAmount() - firstRefundAmount);
        String secondRefundId = assertRefundResponseWith(secondRefundAmount, secondValidatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(chargeId);
        assertThat(refundsFoundByChargeId.size(), is(2));

        assertThat(refundsFoundByChargeId, hasItems(
                aRefundMatching(secondRefundId, is(notNullValue()), chargeId, secondRefundAmount, "REFUNDED"),
                aRefundMatching(firstRefundId, is(notNullValue()), chargeId, firstRefundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForTwoRefunds(defaultTestCharge);

        connectorRestApiClient.withChargeId(externalChargeId)
                .getCharge()
                .statusCode(200)
                .body("refund_summary.status", is("full"))
                .body("refund_summary.amount_available", is(0))
                .body("refund_summary.amount_submitted", is(100));
    }

    @Test
    public void shouldFailRequestingARefund_whenChargeStatusMakesItNotRefundable() {

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withAmount(100L)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .insert();

        Long refundAmount = 20L;

        postRefundFor(testCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("pending"))
                .body("message", contains(format("Charge with id [%s] not available for refund.", testCharge.getExternalChargeId())))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenChargeRefundIsFull() {

        Long refundAmount = defaultTestCharge.getAmount();
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        Long chargeId = defaultTestCharge.getChargeId();

        postRefundFor(externalChargeId, refundAmount, defaultTestCharge.getAmount())
                .statusCode(ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(chargeId);
        assertThat(refundsFoundByChargeId.size(), is(1));

        postRefundFor(externalChargeId, 1L, defaultTestCharge.getAmount() - refundAmount)
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("full"))
                .body("message", contains(format("Charge with id [%s] not available for refund.", externalChargeId)))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));
    }

    @Test
    public void shouldFailRequestingARefund_whenAmountIsBiggerThanChargeAmount() {
        Long refundAmount = defaultTestCharge.getAmount() + 20;

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_not_available"))
                .body("message", contains("Not sufficient amount available for refund"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));

        List<String> refundsHistory = databaseTestHelper.getRefundsHistoryByChargeId(defaultTestCharge.getChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenAmountIsBiggerThanAllowedChargeAmount() {

        Long refundAmount = 10000001L;

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_not_available"))
                .body("message", contains("Not sufficient amount available for refund"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));

        List<String> refundsHistory = databaseTestHelper.getRefundsHistoryByChargeId(defaultTestCharge.getChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenAmountIsLessThanOnePence() {

        Long refundAmount = 0L;

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_min_validation"))
                .body("message", contains("Validation error for amount. Minimum amount for a refund is 1"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));

        List<String> refundsHistory = databaseTestHelper.getRefundsHistoryByChargeId(defaultTestCharge.getChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenAPartialRefundMakesTotalRefundedAmountBiggerThanChargeAmount() {
        Long firstRefundAmount = 80L;
        Long secondRefundAmount = 30L; // 10 more than available

        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), firstRefundAmount, defaultTestCharge.getAmount());
        String firstRefundId = assertRefundResponseWith(firstRefundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getChargeId(), firstRefundAmount, "REFUNDED")));

        postRefundFor(defaultTestCharge.getExternalChargeId(), secondRefundAmount, defaultTestCharge.getAmount() - firstRefundAmount)
                .statusCode(400)
                .body("reason", is("amount_not_available"))
                .body("message", contains("Not sufficient amount available for refund"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeId1 = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId1.size(), is(1));
        assertThat(refundsFoundByChargeId1, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getChargeId(), firstRefundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(defaultTestCharge);
    }

    private ValidatableResponse postRefundFor(String chargeId, Long refundAmount, long refundAmountAvlbl) {
        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", refundAmount, "refund_amount_available", refundAmountAvlbl);
        String refundPayload = new Gson().toJson(refundData);

        return givenSetup()
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", chargeId))
                .then();
    }

    private String assertRefundResponseWith(Long refundAmount, ValidatableResponse validatableResponse, int expectedStatusCode) {
        ValidatableResponse response = validatableResponse
                .statusCode(expectedStatusCode)
                .body("refund_id", is(notNullValue()))
                .body("amount", is(refundAmount.intValue()))
                .body("status", is("success"))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("created_date", isWithin(10, SECONDS));

        String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                testContext.getPort(), defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId());

        String refundId = response.extract().path("refund_id");
        response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                .body("_links.payment.href", is(paymentUrl));

        return refundId;
    }

    private void assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(DatabaseFixtures.TestCharge defaultTestCharge) {
        List<String> refundsHistory = databaseTestHelper.getRefundsHistoryByChargeId(defaultTestCharge.getChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(3));
        assertThat(refundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED"));
    }

    private void assertRefundsHistoryInOrderInDBForTwoRefunds(DatabaseFixtures.TestCharge defaultTestCharge) {
        List<String> refundsHistory = databaseTestHelper.getRefundsHistoryByChargeId(defaultTestCharge.getChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(6));
        assertThat(refundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED", "REFUNDED", "REFUND SUBMITTED", "CREATED"));
    }
}
