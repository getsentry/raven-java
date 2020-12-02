package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.spring.SentryRequestResolver;
import io.sentry.util.Objects;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/**
 * Creates {@link io.sentry.SentryTransaction} around HTTP request executions.
 *
 * <p>Only requests that have {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} request
 * attribute set are turned into transactions. This attribute is set in {@link
 * RequestMappingInfoHandlerMapping} on request that have not been dropped with any {@link
 * javax.servlet.Filter}.
 */
@Open
public class SentryTracingFilter extends OncePerRequestFilter {
  /** Operation used by {@link SentryTransaction} created in {@link SentryTracingFilter}. */
  private static final String TRANSACTION_OP = "http.server";

  private final @NotNull TransactionNameProvider transactionNameProvider =
      new TransactionNameProvider();
  private final @NotNull IHub hub;
  private final @NotNull SentryOptions options;
  private final @NotNull SentryRequestResolver requestResolver;

  public SentryTracingFilter(
      final @NotNull IHub hub,
      final @NotNull SentryOptions options,
      final @NotNull SentryRequestResolver requestResolver) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.options = Objects.requireNonNull(options, "options is required");
    this.requestResolver = Objects.requireNonNull(requestResolver, "requestResolver is required");
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest httpRequest,
      final @NotNull HttpServletResponse httpResponse,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {

    final String sentryTraceHeader = httpRequest.getHeader(SentryTraceHeader.SENTRY_TRACE_HEADER);

    // at this stage we are not able to get real transaction name
    final io.sentry.SentryTransaction transaction =
        startTransaction(
            httpRequest.getMethod() + " " + httpRequest.getRequestURI(), sentryTraceHeader);
    try {
      filterChain.doFilter(httpRequest, httpResponse);
    } finally {
      // after all filters run, templated path pattern is available in request attribute
      final String transactionName = transactionNameProvider.provideTransactionName(httpRequest);
      // if transaction name is not resolved, the request has not been processed by a controller and
      // we should not report it to Sentry
      if (transactionName != null) {
        transaction.setName(transactionName);
        transaction.setOperation(TRANSACTION_OP);
        transaction.setRequest(requestResolver.resolveSentryRequest(httpRequest));
        transaction.setStatus(SpanStatus.fromHttpStatusCode(httpResponse.getStatus()));
        transaction.finish();
      }
    }
  }

  private io.sentry.SentryTransaction startTransaction(
      final @NotNull String name, final @Nullable String sentryTraceHeader) {
    if (sentryTraceHeader != null) {
      try {
        final TransactionContext contexts =
            TransactionContext.fromSentryTrace(name, new SentryTraceHeader(sentryTraceHeader));
        return hub.startTransaction(contexts);
      } catch (InvalidSentryTraceHeaderException e) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Failed to parse Sentry trace header: %s", e.getMessage());
      }
    }
    return hub.startTransaction(name);
  }
}
