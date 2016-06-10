package com.getsentry.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.getsentry.raven.event.interfaces.MessageInterface;

import java.io.IOException;

/**
 * Binding allowing to transform a {@link MessageInterface} into a JSON stream.
 */
public class MessageInterfaceBinding implements InterfaceBinding<MessageInterface> {
    /**
     * Maximum length for a message.
     */
    public static final int MAX_MESSAGE_LENGTH = 1000;
    private static final String MESSAGE_PARAMETER = "message";
    private static final String PARAMS_PARAMETER = "params";
    private static final String FORMATTED_PARAMETER = "formatted";

    /**
     * Formats a message, ensuring that the maximum length {@link #MAX_MESSAGE_LENGTH} isn't reached.
     *
     * @param message message to format.
     * @return formatted message (shortened if necessary).
     */
    private String formatMessage(String message) {
        if (message == null)
            return null;
        else if (message.length() > MAX_MESSAGE_LENGTH)
            return message.substring(0, MAX_MESSAGE_LENGTH);
        else return message;
    }

    @Override
    public void writeInterface(JsonGenerator generator, MessageInterface messageInterface) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(MESSAGE_PARAMETER, formatMessage(messageInterface.getMessage()));
        generator.writeArrayFieldStart(PARAMS_PARAMETER);
        for (String parameter : messageInterface.getParameters()) {
            generator.writeString(parameter);
        }
        generator.writeEndArray();
        if (messageInterface.getFormatted() != null) {
            generator.writeStringField(FORMATTED_PARAMETER, formatMessage(messageInterface.getFormatted()));
        }
        generator.writeEndObject();
    }
}
