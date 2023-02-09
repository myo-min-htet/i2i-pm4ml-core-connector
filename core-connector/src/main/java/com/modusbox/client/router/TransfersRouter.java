package com.modusbox.client.router;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.utility.util;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONException;

public class TransfersRouter extends RouteBuilder {

    private final RouteExceptionHandlingConfigurer exception = new RouteExceptionHandlingConfigurer();

    private static final String ROUTE_ID_POST = "com.modusbox.postTransfers";
    private static final String ROUTE_ID_PUT = "com.modusbox.putTransfersByTransferId";
    private static final String ROUTE_ID_GET = "com.modusbox.getTransfersByTransferId";
    private static final String COUNTER_NAME_POST = "counter_post_transfers_requests";
    private static final String COUNTER_NAME_PUT = "counter_put_transfers_requests";
    private static final String COUNTER_NAME_GET = "counter_get_transfers_requests";
    private static final String TIMER_NAME_POST = "histogram_post_transfers_timer";
    private static final String TIMER_NAME_PUT = "histogram_put_transfers_timer";
    private static final String TIMER_NAME_GET = "histogram_get_transfers_timer";
    private static final String HISTOGRAM_NAME_POST = "histogram_post_transfers_requests_latency";
    private static final String HISTOGRAM_NAME_PUT = "histogram_put_transfers_requests_latency";
    private static final String HISTOGRAM_NAME_GET = "histogram_get_transfers_requests_latency";

    public static final Counter requestCounter = Counter.build()
            .name(COUNTER_NAME_POST)
            .help("Total requests for POST /transfers.")
            .register();

    private static final Histogram requestLatency = Histogram.build()
            .name(HISTOGRAM_NAME_POST)
            .help("Request latency in seconds for POST /transfers.")
            .register();

    public static final Counter requestCounterPut = Counter.build()
            .name(COUNTER_NAME_PUT)
            .help("Total requests for PUT /transfers/{transferId}.")
            .register();

    private static final Histogram requestLatencyPut = Histogram.build()
            .name(HISTOGRAM_NAME_PUT)
            .help("Request latency in seconds for PUT /transfers/{transferId}.")
            .register();

    public static final Counter requestCounterGet = Counter.build()
            .name(COUNTER_NAME_GET)
            .help("Total requests for GET /transfers/{transferId}.")
            .register();

    private static final Histogram requestLatencyGet = Histogram.build()
            .name(HISTOGRAM_NAME_GET)
            .help("Request latency in seconds for GET /transfers/{transferId}.")
            .register();

    public void configure() {

        // Add custom global exception handling strategy
        exception.configureExceptionHandling(this);

        from("direct:getTransfersByTransferId").routeId(ROUTE_ID_GET).doTry()
                .process(exchange -> {
                    requestCounterGet.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_GET, requestLatencyGet.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, GET /transfers/${header.transferId}', " +
                        "null, null, null)")
                /*
                 * BEGIN processing
                 */

                .removeHeaders("CamelHttp*")
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Accept", constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Calling Hub API, get transfers, GET {{ml-outbound.endpoint}}', " +
                        "'Tracking the request', 'Track the response', 'Input Payload: ${body}')")
                .toD("{{ml-outbound.endpoint}}/transfers/${header.transferId}?bridgeEndpoint=true&throwExceptionOnFailure=false")
                .unmarshal().json(JsonLibrary.Gson)
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Response from Hub API, get transfers: ${body}', " +
                        "'Tracking the response', 'Verify the response', null)")
//                .process(exchange -> System.out.println())

                .choice()
                .when(simple("${body['statusCode']} != null"))
//                .process(exchange -> System.out.println())
                .to("direct:catchMojaloopError")
                .endDoTry()

//                .process(exchange -> System.out.println())

                .choice()
                .when(simple("${body['fulfil']} != null"))
//                .process(exchange -> System.out.println())
                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/getTransfersResponse.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()
                .endDoTry()

                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Final Response: ${body}', " +
                        "null, null, 'Response of GET /transfers/${header.transferId} API')")

                .doCatch(CCCustomException.class, HttpOperationFailedException.class, JSONException.class)
                .to("direct:extractCustomErrors")
                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME_GET)).observeDuration(); // stop Prometheus Histogram metric
                }).end()
        ;

        from("direct:postTransfers").routeId(ROUTE_ID_POST).doTry()
                .process(exchange -> {
                    requestCounter.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_POST, requestLatency.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, " + ROUTE_ID_POST + "', null, null, 'Input Payload: ${body}')") // default logging

                /*
                 * BEGIN processing
                 */

                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(simple("{}"))

                /*
                 * END processing
                 */
                
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Send response, " + ROUTE_ID_POST + "', null, null, 'Output Payload: ${body}')") // default logging
                .doFinally().process(exchange -> {
            ((Histogram.Timer) exchange.getProperty(TIMER_NAME_POST)).observeDuration(); // stop Prometheus Histogram metric
        }).end()
        ;

        from("direct:putTransfersByTransferId").routeId(ROUTE_ID_PUT).doTry()
                .process(exchange -> {
                    requestCounterPut.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_PUT, requestLatencyPut.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, PUT /transfers/${header.transferId}', " +
                        "null, null, 'Input Payload: ${body}')")

                .marshal().json(JsonLibrary.Gson)
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, PUT /transfers/${header.transferId}', " +
                        "null, null, 'Input Payload in JSON: ${body}')")
                .unmarshal().json(JsonLibrary.Gson)

                /*
                 * BEGIN processing
                 */

                .setProperty("origPayload", simple("${body}"))
                .choice()
                    .when(simple("${body['currentState']} == 'COMPLETED'"))
                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                "'Transfer current state COMPLETED, PUT /transfers/${header.transferId}', " +
                                "null, null, null)")

                        .toD("direct:getAuthHeader")
                        .setProperty("data",simple("{{dfsp.dataMap}}"))
                        .setProperty("phone",simple("${body['quote']['request']['payee']['partyIdInfo']['partyIdentifier']}"))
                        .setProperty("AccountNumber",method(util.class, "getMapData(${exchangeProperty.phone}, accountNo, ${exchangeProperty.data})"))
                        .setProperty("name",method(util.class, "getMapData(${exchangeProperty.phone}, name, ${exchangeProperty.data})"))
                        .setProperty("email",method(util.class, "getMapData(${exchangeProperty.phone}, email, ${exchangeProperty.data})"))
                        .setProperty("payeremail",method(util.class, "getMapData(${exchangeProperty.phone}, payeremail, ${exchangeProperty.data})"))
                        .setProperty("bankName",method(util.class, "getMapData(${exchangeProperty.phone}, bankName, ${exchangeProperty.data})"))
                        .setProperty("bankCode",method(util.class, "getMapData(${exchangeProperty.phone}, bankCode, ${exchangeProperty.data})"))
                        .removeHeaders("CamelHttp*")
                        .setHeader("Content-Type", constant("application/json"))
                        .marshal().json()
                        .transform(datasonnet("resource:classpath:mappings/putTransactionRequest.ds"))
                        .setBody(simple("${body.content}"))
                        .marshal().json()
                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                "'Calling i2i API, PUT transfers, at: {{dfsp.host}}/api-apic/instapay/process', " +
                                "'Tracking the request', 'Track the response', 'Input Payload: ${body}')")
                        .toD("{{dfsp.host}}/api-apic/instapay/process/direct?bridgeEndpoint=true&throwExceptionOnFailure=false")
                        .unmarshal().json(JsonLibrary.Gson)
                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                            "'Response from backend API, putTransfers: ${body}', " +
                            "'Tracking the response', 'Verify the response', null)")
                        .toD("direct:checkStatus")
                    .otherwise()
                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                            "'Transfer current state is NOT COMPLETED, PUT /transfers/${header.transferId}', " +
                            "null, null, 'Input payload: ${body}')")
                .endDoTry()

                .choice()
                    .when(simple("${body['status']} != 'SUCCESS'"))
                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                "'CBS did not return 201 for, PUT /transfers/${header.transferId}', " +
                                "null, null, 'Output payload: ${body}')")
                        .to("direct:catchCBSError")
                    .otherwise()
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                            "'Response from backend API: ${body}', " +
                            "null, null, null)")
                        .marshal().json()
                        .transform(datasonnet("resource:classpath:mappings/putTransfersResponse.ds"))
                        .setBody(simple("${body.content}"))
                        .marshal().json()
                .endDoTry()

                /*
                 * END processing
                 */

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Final Response: ${body}', " +
                        "null, null, 'Response of PUT /transfers/${header.transferId} API')")
                .doCatch(CCCustomException.class, HttpOperationFailedException.class, JSONException.class)
                    .to("direct:extractCustomErrors")
                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME_PUT)).observeDuration(); // stop Prometheus Histogram metric
                })
                .endDoTry()
        ;
        from("direct:checkStatus")
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Starting checking status API call', " +
                        "'Tracking the request', " +
                        "'Call the check instaPay status method, Track the response', " +
                        "'Input Payload: ${body}')")
                /*
                 * BEGIN processing
                 */
                .setProperty("orgResponse", simple("${body}"))
                .log("Original body : ${body}")
                .setProperty("senderReferenceID",simple("${body['senderReference']}"))
                .//loopDoWhile(simple("${body['status']} == 'SENT FOR PROCESSING' || ${body['status']} == 'SENT FOR CONFIRMATION' || ${body['status']} == 'FOR CHECKING' || ${body['status']} == 'PROCESSING'"))
                loopDoWhile(simple("${body['status']} == 'SENT FOR PROCESSING' || ${body['status']} == 'SENT FOR CONFIRMATION' || ${body['status']} == 'FOR CHECKING' || ${body['status']} == 'PROCESSING' || ${body['status']} == 'CREATED'"))
                    .toD("{{dfsp.host}}/api-apic/instapay?senderReference=${exchangeProperty.senderReferenceID}")
                    .unmarshal().json(JsonLibrary.Gson)
                    .log("Checking body : ${body}")
                .end()

                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Response for check status', " +
                        "'Tracking the response', " +
                        "null, " +
                        "'Output Payload: ${body}')")
                // .removeHeaders("*", "X-*")
                .end()
        ;
    }
}
