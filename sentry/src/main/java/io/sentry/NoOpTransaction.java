package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpTransaction implements ITransaction {

  private static final NoOpTransaction instance = new NoOpTransaction();

  private NoOpTransaction() {}

  public static NoOpTransaction getInstance() {
    return instance;
  }

  @Override
  public void setName(@NotNull String name) {}

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    return NoOpSpan.getInstance();
  }

  @Override
  public void setRequest(@Nullable Request request) {}

  @Override
  public @Nullable Request getRequest() {
    return null;
  }

  @Override
  public @NotNull Contexts getContexts() {
    return new Contexts();
  }

  @Override
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public @NotNull List<Span> getSpans() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return null;
  }

  @Override
  public @Nullable Span getLatestActiveSpan() {
    return null;
  }

  @Override
  public @Nullable SentryId getEventId() {
    return null;
  }

  @Override
  public @Nullable String getTransaction() {
    return null;
  }

  @Override
  public @NotNull SentryTraceHeader toSentryTrace() {
    return new SentryTraceHeader(SentryId.EMPTY_ID, SpanId.EMPTY_ID, false);
  }

  @Override
  public void finish() {}

  @Override
  public void finish(@Nullable SpanStatus status) {}

  @Override
  public void setOperation(@Nullable String operation) {}

  @Override
  public void setDescription(@Nullable String description) {}

  @Override
  public void setStatus(@Nullable SpanStatus status) {}

  @Override
  public void setThrowable(@Nullable Throwable throwable) {}

  @Override
  public @Nullable Throwable getThrowable() {
    return null;
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return new SpanContext(SentryId.EMPTY_ID, SpanId.EMPTY_ID, null, null);
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {}
}
