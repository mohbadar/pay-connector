package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.CardError;
import uk.gov.pay.connector.model.ChargeStatus;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;
import static uk.gov.pay.connector.model.ChargeStatus.STATUS_KEY;
import static uk.gov.pay.connector.model.SandboxCardNumbers.cardErrorFor;
import static uk.gov.pay.connector.model.SandboxCardNumbers.isInvalidCard;
import static uk.gov.pay.connector.model.SandboxCardNumbers.isValidCard;
import static uk.gov.pay.connector.resources.CardDetailsValidator.CARD_NUMBER_FIELD;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isWellFormattedCardDetails;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class CardDetailsResource {
    public static final String CARD_AUTH_FRONTEND_PATH = "/v1/frontend/charges/{chargeId}/cards";

    private final Logger logger = LoggerFactory.getLogger(CardDetailsResource.class);
    private final ChargeDao chargeDao;

    public CardDetailsResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @POST
    @Path(CARD_AUTH_FRONTEND_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response addCardDetailsForCharge(@PathParam("chargeId") String chargeId, Map<String, Object> cardDetails) {

        if (!isWellFormattedCardDetails(cardDetails)) {
            return responseWithError("Values do not match expected format/length.");
        }

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);

        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(logger, chargeId);
        } else if (!hasStatusCreated(maybeCharge.get())) {
            return responseWithCardAlreadyProcessed(chargeId);
        }

        String cardNumber = (String) cardDetails.get(CARD_NUMBER_FIELD);

        return responseForCorrespondingSandboxCard(chargeId, cardNumber);
    }

    private Response responseForCorrespondingSandboxCard(String chargeId, String cardNumber) {

        if (isInvalidCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            chargeDao.updateStatus(chargeId, errorInfo.getNewErrorStatus());
            return responseWithError(errorInfo.getErrorMessage());
        }

        if (isValidCard(cardNumber)) {
            chargeDao.updateStatus(chargeId, AUTHORIZATION_SUCCESS);
            return Response.noContent().build();
        }

        return responseWithError("Unsupported card details.");
    }

    private boolean hasStatusCreated(Map<String, Object> charge) {
        return ChargeStatus.CREATED.getValue().equals(charge.get(STATUS_KEY));
    }

    private Response responseWithError(String msg) {
        return badRequestResponse(logger, msg);
    }

    private Response responseWithCardAlreadyProcessed(String chargeId) {
        return responseWithError(String.format("Card already processed for charge with id %s.", chargeId));
    }
}
