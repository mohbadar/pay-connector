package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import fj.F;
import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fj.data.Either.left;
import static fj.data.Either.reduce;
import static fj.data.Either.right;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.resources.CardResource.AUTHORIZATION_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.resources.CardResource.CAPTURE_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.util.LinksBuilder.linksBuilder;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargesFrontendResource {

    private static final String CHARGES_FRONTEND_PATH = "/v1/frontend/charges/";
    private static final String GET_CHARGE_FRONTEND_PATH = CHARGES_FRONTEND_PATH + "{chargeId}";

    private final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private final ChargeDao chargeDao;

    public ChargesFrontendResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(GET_CHARGE_FRONTEND_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        return maybeCharge
                .map(charge -> {
                    URI chargeLocation = locationUriFor(GET_CHARGE_FRONTEND_PATH, uriInfo, chargeId);

                    Map<String, Object> responseData = linksBuilder(chargeLocation)
                            .addLink("cardAuth", HttpMethod.POST, locationUriFor(AUTHORIZATION_FRONTEND_RESOURCE_PATH, uriInfo, chargeId))
                            .addLink("cardCapture", HttpMethod.POST, locationUriFor(CAPTURE_FRONTEND_RESOURCE_PATH, uriInfo, chargeId))
                            .appendLinksTo(removeGatewayAccount(charge));

                    return ok(responseData).build();
                })
                .orElseGet(() -> responseWithChargeNotFound(logger, chargeId));
    }

    @GET
    @Path(CHARGES_FRONTEND_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharges(@QueryParam("gatewayAccountId") String gatewayAccountId, @Context UriInfo uriInfo) {

        return reduce(validateGatewayAccountReference(gatewayAccountId)
                .bimap(handleError, listTransactions(gatewayAccountId)));

    }

    private F<Boolean, Response> listTransactions(final String gatewayAccountId) {
        return success -> {
            List<Map<String, Object>> charges = chargeDao.findAllBy(gatewayAccountId);
            return ok(ImmutableMap.of("results", charges)).build();

        };
    }

    private F<String, Response> handleError =
            errorMessage -> badRequestResponse(logger, errorMessage);


    private Either<String, Boolean> validateGatewayAccountReference(String gatewayAccountId) {
        if (StringUtils.isBlank(gatewayAccountId)) {
            return left("missing gateway account reference");
        } else if (!NumberUtils.isNumber(gatewayAccountId)) {
            return left(format("invalid gateway account reference %s", gatewayAccountId));
        }
        return right(true);
    }

    private Map<String, Object> removeGatewayAccount(Map<String, Object> charge) {
        charge.remove("gateway_account_id");
        return charge;
    }

    private URI locationUriFor(String path, UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(path).build(chargeId);
    }
}
