package io.sentry;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

public interface ISerializer {
  <T> T deserialize(Reader reader, Class<T> clazz);

  SentryEnvelope deserializeEnvelope(InputStream inputStream);

  <T> void serialize(T entity, Writer writer) throws IOException;

  void serialize(SentryEnvelope envelope, Writer writer) throws Exception;

  String serialize(Map<String, Object> data) throws Exception;
}
