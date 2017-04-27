package uk.gov.pay.connector.service.epdq;

import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.*;
import uk.gov.pay.connector.service.smartpay.SmartpayStatusMapper;

import javax.ws.rs.client.Invocation;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.domain.GatewayAccount.*;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.*;

public class EpdqPaymentProvider extends BasePaymentProvider<BaseResponse> {

    final static public String ROUTE_FOR_NEW_ORDER = "orderdirect.asp";
    final static public String ROUTE_FOR_MAINTENANCE_ORDER = "maintenancedirect.asp";

    public EpdqPaymentProvider(EnumMap<GatewayOperation, GatewayClient> clients) {
        super(clients);
    }

    @Override
    public String getPaymentGatewayName() {
        return PaymentGatewayName.EPDQ.getName();
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        return sendReceive(ROUTE_FOR_NEW_ORDER, request, buildAuthoriseOrderFor(), EpdqAuthorisationResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse<BaseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return GatewayResponse.with(new GatewayError("3D Secure not implemented for Epdq", GENERIC_GATEWAY_ERROR));
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        throw new UnsupportedOperationException("Capture operation not supported.");
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        throw new UnsupportedOperationException("Refund operation not supported.");
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        throw new UnsupportedOperationException("Cancel operation not supported.");
    }

    @Override
    public Boolean isNotificationEndpointSecured() {
        return false;
    }

    @Override
    public String getNotificationDomain() {
        return null;
    }

    @Override
    public Either<String, Notifications<Pair<String, Boolean>>> parseNotification(String payload) {
        return null;
    }

    @Override
    public StatusMapper getStatusMapper() {
        throw new UnsupportedOperationException();
    }

    private Function<AuthorisationGatewayRequest, GatewayOrder> buildAuthoriseOrderFor() {
        return request -> anEpdqAuthoriseOrderRequestBuilder()
                .withOrderId(request.getChargeExternalId())
                .withPspId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaPassphrase(request.getGatewayAccount().getCredentials().get(CREDENTIALS_SHA_PASSPHRASE))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private Function<GatewayClient.Response, Optional<String>> extractResponseIdentifier() {
        return response -> {
            Optional<String> emptyResponseIdentifierForEpdq = Optional.empty();
            return emptyResponseIdentifierForEpdq;
        };
    }

    public static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }
}