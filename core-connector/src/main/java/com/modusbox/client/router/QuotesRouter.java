package com.modusbox.client.router;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.CorsFilter;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONException;

public class QuotesRouter extends RouteBuilder {

	private final RouteExceptionHandlingConfigurer exception = new RouteExceptionHandlingConfigurer();
	private final CorsFilter corsFilter = new CorsFilter();

	private static final String ROUTE_ID = "com.modusbox.postQuoterequests";
	private static final String COUNTER_NAME = "counter_post_quoterequests_requests";
	private static final String TIMER_NAME = "histogram_post_quoterequests_timer";
	private static final String HISTOGRAM_NAME = "histogram_post_quoterequests_requests_latency";

	public static final Counter requestCounter = Counter.build()
			.name(COUNTER_NAME)
			.help("Total requests for POST /quoterequests.")
			.register();

	private static final Histogram requestLatency = Histogram.build()
			.name(HISTOGRAM_NAME)
			.help("Request latency in seconds for POST /quoterequests.")
			.register();

	public void configure() {

		// Add custom global exception handling strategy
		exception.configureExceptionHandling(this);

		from("direct:postQuoteRequests").routeId(ROUTE_ID).doTry()
				.process(exchange -> {
					requestCounter.inc(1); // increment Prometheus Counter metric
					exchange.setProperty(TIMER_NAME, requestLatency.startTimer()); // initiate Prometheus Histogram metric
				})
				.marshal().json(JsonLibrary.Gson)
				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Request received, " + ROUTE_ID + "', null, null, 'Input Payload: ${body}')") // default logging
				.unmarshal().json(JsonLibrary.Gson)
				/*
				 * BEGIN processing
				 */
				.setProperty("origPayload", simple("${body}"))

				.removeHeaders("CamelHttp*")
				.removeHeader(Exchange.HTTP_URI)
				.setHeader("Content-Type", constant("application/json"))
				.setHeader("Accept", constant("application/json"))
				.setHeader(Exchange.HTTP_METHOD, constant("GET"))
				.setProperty("quoteId",simple("${body['quoteId']}"))
				.setProperty("amount",simple("${body['amount']}"))
				.setProperty("currency",simple("${body['currency']}"))
				.toD("{{dfsp.mockhost}}/payment/quotes/${exchangeProperty.quoteId}")
//				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/postQuoterequestsResponseMock.ds"))
				.setBody(simple("${body.content}"))
//				.marshal().json()
				.log("${body}")
				// Add CORS headers
				.process(corsFilter)

				/*
				 * END processing
				 */
				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Send response, " + ROUTE_ID + "', null, null, 'Output Payload: ${body}')") // default logging
				.doCatch(CCCustomException.class, HttpOperationFailedException.class, JSONException.class)
					.to("direct:extractCustomErrors")
				.doFinally().process(exchange -> {
			((Histogram.Timer) exchange.getProperty(TIMER_NAME)).observeDuration(); // stop Prometheus Histogram metric
		}).end()
		;
	}
}
