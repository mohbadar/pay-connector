package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.RandomStringUtils;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeSearch;
import uk.gov.pay.connector.dao.EventDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.dao.ChargeSearch.aChargeSearch;
import static uk.gov.pay.connector.fixture.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class ChargeDaoITest {

    private static final Long GATEWAY_ACCOUNT_ID = 564532435L;
    private static final String RETURN_URL = "http://service.com/success-page";
    private static final String REFERENCE = "Test reference";
    private static final String FROM_DATE = "2016-01-01T01:00:00Z";
    private static final String TO_DATE = "2026-01-08T01:00:00Z";
    private static final String DESCRIPTION = "Test description";
    private static final Long AMOUNT = 101L;
    private static final Long CHARGE_ID = 977L;
    private static final String PAYMENT_PROVIDER = "test_provider";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;
    private EventDao eventDao;
    public GuicedTestEnvironment env;
    private DatabaseTestHelper databaseTestHelper;

    @Before
    public void setUp() throws Exception {
        env = GuicedTestEnvironment.from(app.getPersistModule())
                .start();

        chargeDao = env.getInstance(ChargeDao.class);
        eventDao = env.getInstance(EventDao.class);

        databaseTestHelper = app.getDatabaseTestHelper();

        databaseTestHelper.addGatewayAccount(GATEWAY_ACCOUNT_ID.toString(), PAYMENT_PROVIDER);
        databaseTestHelper.addCharge(String.valueOf(CHARGE_ID), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, "", REFERENCE, ZonedDateTime.now());
    }

    @After
    public void tearDown() {
        env.stop();
    }

    @Test
    public void searchChargesByGatewayAccountIdOnly() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByFullReferenceOnly() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByPartialReferenceOnly() throws Exception {

        // given
        String paymentReference = "Council Tax Payment reference 2";
        Long chargeId = System.currentTimeMillis();
        databaseTestHelper.addCharge(chargeId.toString(), GATEWAY_ACCOUNT_ID.toString(), AMOUNT, CREATED, RETURN_URL, UUID.randomUUID().toString(), paymentReference, ZonedDateTime.now());

        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike("reference");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(1).getId(), is(CHARGE_ID));
        assertThat(charges.get(1).getReference(), is(REFERENCE));
        assertThat(charges.get(0).getReference(), is(paymentReference));

        for (ChargeEntity charge : charges) {
            assertThat(charge.getAmount(), is(AMOUNT));
            assertThat(charge.getDescription(), is(DESCRIPTION));
            assertThat(charge.getStatus(), is(CREATED.getValue()));
            assertDateMatch(charge.getCreatedDate().toString());
        }
    }

    @Test
    public void searchChargeByReferenceAndStatusOnly() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE))
                .withCreatedDateTo(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByMultipleStatuses() {

        // given
        Long chargeId = System.currentTimeMillis();
        databaseTestHelper.addCharge(chargeId.toString(), GATEWAY_ACCOUNT_ID.toString(), AMOUNT, ENTERING_CARD_DETAILS, RETURN_URL, UUID.randomUUID().toString(), REFERENCE, ZonedDateTime.now());

        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED, ENTERING_CARD_DETAILS)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(0).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
        assertThat(charges.get(1).getStatus(), is(CREATED.getValue()));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateTo(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange() throws Exception {

        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(TO_DATE));

        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange() throws Exception {

        ChargeSearch queryBuilder = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateTo(ZonedDateTime.parse(FROM_DATE));

        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {

        // given
        Long id = System.currentTimeMillis();
        databaseTestHelper.addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT, CREATED, RETURN_URL, UUID.randomUUID().toString(), REFERENCE, ZonedDateTime.now());

        // when
        ChargeEntity charge = chargeDao.findById(id).get();

        // then
        assertThat(charge.getId(), is(id));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(charge.getReturnUrl(), is(RETURN_URL));
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime createdDate = charge.getCreatedDate();
        assertThat(createdDate, ZonedDateTimeMatchers.isDayOfMonth(now.getDayOfMonth()));
        assertThat(createdDate, ZonedDateTimeMatchers.isMonth(now.getMonth()));
        assertThat(createdDate, ZonedDateTimeMatchers.isYear(now.getYear()));
        MatcherAssert.assertThat(createdDate, ZonedDateTimeMatchers.within(1, ChronoUnit.MINUTES, now));
    }

    private Matcher<? super List<ChargeEventEntity>> shouldIncludeStatus(ChargeStatus... expectedStatuses) {
        return new TypeSafeMatcher<List<ChargeEventEntity>>() {
            @Override
            protected boolean matchesSafely(List<ChargeEventEntity> chargeEvents) {
                List<ChargeStatus> actualStatuses = chargeEvents.stream()
                        .map(ChargeEventEntity::getStatus)
                        .collect(toList());
                return actualStatuses.containsAll(asList(expectedStatuses));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("does not contain [%s]", expectedStatuses));
            }
        };
    }

    @Deprecated
    private ImmutableMap<String, Object> newCharge(long amount, String reference) {
        return ImmutableMap.of(
                "amount", amount,
                "reference", reference,
                "description", DESCRIPTION,
                "return_url", RETURN_URL);
    }

    private void assertDateMatch(String createdDateString) {
        ZonedDateTime createdDateTime = DateTimeUtils.toUTCZonedDateTime(createdDateString).get();
        assertThat(createdDateTime, ZonedDateTimeMatchers.within(1, ChronoUnit.MINUTES, ZonedDateTime.now()));
    }

    @Test
    public void shouldUpdateEventsWhenMergeWithChargeEntityWithNewStatus() {

        Long chargeId = 56735L;
        String transactionId = "345654";
        databaseTestHelper.addCharge(String.valueOf(chargeId), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, ZonedDateTime.now(ZoneId.of("UTC")));

        Optional<ChargeEntity> charge = chargeDao.findById(chargeId);
        ChargeEntity entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);

        chargeDao.mergeAndNotifyStatusHasChanged(entity);

        List<ChargeEventEntity> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, chargeId);

        assertThat(events, hasSize(1));
        assertThat(events, shouldIncludeStatus(ENTERING_CARD_DETAILS));
    }

    @Test
    public void invalidSizeOfReference() throws Exception {
        expectedEx.expect(RuntimeException.class);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(PAYMENT_PROVIDER, new HashMap<>());
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);
        chargeDao.persist(aValidChargeEntity().withReference(RandomStringUtils.randomAlphanumeric(255)).build());
    }

    @Test
    public void shouldCreateANewCharge() {

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(PAYMENT_PROVIDER, new HashMap<>());
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        assertThat(chargeEntity.getId(), is(notNullValue()));
    }

    @Test
    public void shouldReturnNullFindingByIdWhenChargeDoesNotExist() {

        Optional<ChargeEntity> charge = chargeDao.findById(5686541L);

        assertThat(charge.isPresent(), is(false));
    }

    @Test
    public void shouldFindChargeEntityByProviderAndTransactionId() {

        // given
        String transactionId = "7826782163";
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        Long chargeId = 9999L;

        databaseTestHelper.addCharge(String.valueOf(chargeId), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, createdDate);

        // when
        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(PAYMENT_PROVIDER, transactionId);

        // then
        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(RETURN_URL));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
    }

    @Test
    public void shouldGetGatewayAccountWhenFindingChargeEntityByProviderAndTransactionId() {

        String transactionId = "7826782163";
        ZonedDateTime createdDate = ZonedDateTime.now();
        databaseTestHelper.addCharge(String.valueOf(8888L), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, createdDate);

        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(PAYMENT_PROVIDER, transactionId);

        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
        GatewayAccountEntity gatewayAccount = charge.getGatewayAccount();
        assertThat(gatewayAccount, is(notNullValue()));
        assertThat(gatewayAccount.getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(gatewayAccount.getGatewayName(), is(PAYMENT_PROVIDER));
        assertThat(gatewayAccount.getCredentials(), is(Collections.EMPTY_MAP));
    }

    @Test
    public void shouldGetChargeByChargeIdWithCorrectAssociatedAccountId() {

        String transactionId = "7826782163";
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        Long chargeId = 876786L;

        databaseTestHelper.addCharge(String.valueOf(chargeId), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, createdDate);

        ChargeEntity charge = chargeDao.findByIdAndGatewayAccount(chargeId, GATEWAY_ACCOUNT_ID).get();

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(RETURN_URL));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
    }

    @Test
    public void shouldGetChargeByChargeIdAsNullWhenAccountIdDoesNotMatch() {
        Optional<ChargeEntity> chargeForAccount = chargeDao.findByIdAndGatewayAccount(CHARGE_ID, 456781L);
        assertThat(chargeForAccount.isPresent(), is(false));
    }
}
