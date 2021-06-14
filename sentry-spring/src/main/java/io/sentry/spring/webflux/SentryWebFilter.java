package io.sentry.spring.webflux;

import io.sentry.Breadcrumb;
import io.sentry.IHub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.sentry.util.Objects;
import reactor.core.publisher.Mono;

/** Manages {@link io.sentry.Scope} in Webflux request processing. */
@ApiStatus.Experimental
public final class SentryWebFilter implements WebFilter {
  private final @NotNull IHub hub;
  private final @NotNull SentryRequestResolver sentryRequestResolver;

  public SentryWebFilter(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.sentryRequestResolver = new SentryRequestResolver(hub);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(
            __ -> {
              hub.popScope();
            })
        .doFirst(
            () -> {
              hub.pushScope();
              final ServerHttpRequest request = serverWebExchange.getRequest();
              hub.addBreadcrumb(
                  Breadcrumb.http(request.getURI().toString(), request.getMethodValue()));
              hub.configureScope(
                  scope -> scope.setRequest(sentryRequestResolver.resolveSentryRequest(request)));
            });
  }
}
