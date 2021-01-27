package io.sentry.transport;

import io.sentry.ISerializer;
import io.sentry.SentryEnvelope;
import io.sentry.util.Objects;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public final class StdoutTransport implements ITransport {

  private final @NotNull ISerializer serializer;

  public StdoutTransport(final @NotNull ISerializer serializer) {
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required");
  }

  @Override
  public void send(final @NotNull SentryEnvelope envelope, Object hint) throws IOException {
    Objects.requireNonNull(envelope, "SentryEnvelope is required");

    try {
      serializer.serialize(envelope, System.out);
    } catch (Exception e) {
      // do nothing
    }
  }

  @Override
  public void close() {}
}
