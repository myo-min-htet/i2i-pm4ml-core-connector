package com.modusbox.client.router;

import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.TokenStore;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

public class AuthRouter extends RouteBuilder {

    private final String PATH_NAME = "i2i Fetch Access Token API";
    private final String PATH = "/api-apic/auth/token";

    private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

    public void configure() {

        exceptionHandlingConfigurer.configureExceptionHandling(this);

        from("direct:getAuthHeader")
                .setProperty("downstreamRequestBody", simple("${body}"))
                .setProperty("AccessToken", method(TokenStore.class, "getAccessToken()"))
                .choice()
                    .when(method(TokenStore.class, "getAccessToken()").isEqualTo(""))
                    .removeHeaders("Camel*")
                    .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                    .marshal().json()
                    .transform(datasonnet("resource:classpath:mappings/getToken.ds"))
                    .setBody(simple("${body.content}"))
                    .marshal().json()

                    .to("bean:customJsonMessage?method=logJsonMessage(" +
                            "'info', " +
                            "${header.X-CorrelationId}, " +
                            "'Calling the access token " + PATH_NAME + "', " +
                            "null, " +
                            "null, " +
                            "'Request to POST {{dfsp.host}}" + PATH +", IN Payload: ${body}')")
                    .toD("{{dfsp.host}}" + PATH)
                    .unmarshal().json()
                    .log("${body}")
                    .setProperty("AccessToken", simple("${body['accessToken']}"))
                    .setProperty("AccessTokenExpiration", simple("${body['expiresIn']}"))
                    .bean(TokenStore.class, "setAccessToken(${exchangeProperty.AccessToken}, 3599)")

                    .to("bean:customJsonMessage?method=logJsonMessage(" +
                            "'info', " +
                            "${header.X-CorrelationId}, " +
                            "'Called Access token " + PATH_NAME + "', " +
                            "null, " +
                            "null, " +
                            "'Response from POST {{dfsp.host}}" + PATH + ", OUT Payload: ${body}')")

                .end()

                .setHeader("Authorization", simple("Bearer ${exchangeProperty.AccessToken}"))
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Auth Token caught from " + PATH_NAME + "', " +
                        "null, " +
                        "null, " +
                        "'Authorization: ${header.Authorization}')")
                .removeHeaders("CamelHttp*")
                .setBody(simple("${exchangeProperty.downstreamRequestBody}"))
        ;
    }
}