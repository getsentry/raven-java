package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryTransaction;
import io.sentry.SpanContext;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * {@link HandlerExceptionResolver} implementation that will record any exception that a Spring
 * {@link org.springframework.web.servlet.mvc.Controller} throws to Sentry. It then returns null,
 * which will let the other (default or custom) exception resolvers handle the actual error.
 */
@Open
public class SentryExceptionResolver implements HandlerExceptionResolver, Ordered {
  private final @NotNull IHub hub;
  private final int order;

  public SentryExceptionResolver(final @NotNull IHub hub, final int order) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.order = order;
  }

  @Override
  public @Nullable ModelAndView resolveException(
      final @NotNull HttpServletRequest request,
      final @NotNull HttpServletResponse response,
      final @Nullable Object handler,
      final @NotNull Exception ex) {

    final Mechanism mechanism = new Mechanism();
    mechanism.setHandled(false);
    final Throwable throwable =
        new ExceptionMechanismException(mechanism, ex, Thread.currentThread());
    final SentryEvent event = new SentryEvent(throwable);
    event.setLevel(SentryLevel.FATAL);

    final SentryTransaction sentryTransaction = resolveActiveTransaction();
    if (sentryTransaction != null) {
      final SpanContext spanContext = sentryTransaction.getSpanContext(ex);
      if (spanContext != null) {
        // connects the event with a span
        event.getContexts().setTrace(spanContext);
      }
    }
    hub.captureEvent(event);

    // null = run other HandlerExceptionResolvers to actually handle the exception
    return null;
  }

  @Override
  public int getOrder() {
    return order;
  }

  private @Nullable SentryTransaction resolveActiveTransaction() {
    final AtomicReference<SentryTransaction> spanRef = new AtomicReference<>();

    hub.configureScope(
        scope -> {
          final SentryTransaction transaction = scope.getTransaction();

          if (transaction != null) {
            spanRef.set(transaction);
          }
        });

    return spanRef.get();
  }
}
