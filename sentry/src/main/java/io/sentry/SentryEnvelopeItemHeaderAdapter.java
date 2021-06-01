package io.sentry;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.sentry.util.StringUtils;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeItemHeaderAdapter extends TypeAdapter<SentryEnvelopeItemHeader> {
  @Override
  public void write(JsonWriter writer, SentryEnvelopeItemHeader value) throws IOException {
    if (value == null) {
      writer.nullValue();
      return;
    }
    writer.beginObject();

    if (value.getContentType() != null) {
      writer.name("content_type");
      writer.value(value.getContentType());
    }

    if (value.getFileName() != null) {
      writer.name("filename");
      writer.value(value.getFileName());
    }

    if (!SentryItemType.Unknown.equals(value.getType())) {
      writer.name("type");
      writer.value(value.getType().getItemType().toLowerCase(Locale.ROOT));
    }

    if (value.getAttachmentType() != null) {
      writer.name("attachment_type");
      writer.value(value.getAttachmentType());
    }

    writer.name("length");
    writer.value(value.getLength());

    writer.endObject();
  }

  @Override
  public @Nullable SentryEnvelopeItemHeader read(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }

    String contentType = null;
    String fileName = null;
    SentryItemType type = SentryItemType.Unknown;
    int length = 0;
    String attachmentType = null;

    reader.beginObject();
    while (reader.hasNext()) {
      switch (reader.nextName()) {
        case "content_type":
          contentType = reader.nextString();
          break;
        case "filename":
          fileName = reader.nextString();
          break;
        case "type":
          try {
            final String nextString = StringUtils.capitalize(reader.nextString());
            if (nextString != null) {
              type = SentryItemType.valueOf(nextString);
            }
          } catch (IllegalArgumentException ignored) {
            // invalid type
          }
          break;
        case "length":
          length = reader.nextInt();
          break;
        case "attachment_type":
          attachmentType = reader.nextString();
          break;
        default:
          reader.skipValue();
          break;
      }
    }
    reader.endObject();

    return new SentryEnvelopeItemHeader(type, length, contentType, fileName, attachmentType);
  }
}
