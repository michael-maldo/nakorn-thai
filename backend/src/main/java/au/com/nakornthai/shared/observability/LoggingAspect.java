package au.com.nakornthai.shared.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

/** Central request-completion logging in the existing observability scaffold.
 * Uses observation lifecycle callbacks, not controller AOP, to also cover async requests.
 */
@Slf4j
@Component
public class LoggingAspect implements ObservationHandler<ServerRequestObservationContext> {
    private static final Object START = new Object();

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onStart(ServerRequestObservationContext context) {
        context.put(START, System.nanoTime());
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        Long start = context.get(START);
        var response = context.getResponse();
        int status = response == null ? 500 : response.getStatus();
        if (context.getError() != null && status < 400) status = 500;
        var event = status >= 500 ? log.atError() : status >= 400 ? log.atWarn() : log.atInfo();
        TracingContext tracing = context.get(TracingContext.class);
        var span = tracing == null ? null : tracing.getSpan();
        event.addKeyValue("event", "http_request_completed")
                .addKeyValue("http_method", context.getCarrier().getMethod())
                .addKeyValue("http_route", context.getPathPattern() == null ? "UNMATCHED" : context.getPathPattern())
                .addKeyValue("http_status", status)
                .addKeyValue("duration_ms", start == null ? 0 : (System.nanoTime() - start) / 1_000_000.0)
                .addKeyValue("request_id", context.getCarrier().getAttribute(CorrelationIdFilter.ATTRIBUTE))
                .addKeyValue("trace_id", span == null ? null : span.context().traceId())
                .addKeyValue("span_id", span == null ? null : span.context().spanId())
                .addKeyValue("error_type", context.getError() == null ? null : context.getError().getClass().getSimpleName())
                .log("HTTP request completed");
        // Do not log raw URLs, query strings, headers, bodies, or exception messages here.
    }
}
