package io.sentry.transport;

import io.sentry.SentryEnvelope;
import java.io.Closeable;
import java.io.IOException;

/** A transport is in charge of sending the event to the Sentry server. */
public interface ITransport extends Closeable {
  void send(SentryEnvelope envelope, Object hint) throws IOException;

  default void send(SentryEnvelope envelope) throws IOException {
    send(envelope, null);
  }

  /**
   * Flushes events queued up, but keeps the client enabled. Not implemented yet.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);
}
