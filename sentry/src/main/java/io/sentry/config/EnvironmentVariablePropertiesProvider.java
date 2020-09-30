package io.sentry.config;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesProvider} implementation that resolves properties from the environment
 * variables.
 */
final class EnvironmentVariablePropertiesProvider implements PropertiesProvider {
  private static final String PREFIX = "SENTRY";

  @Override
  public @Nullable String getProperty(@NotNull String property) {
    return System.getenv(
        PREFIX + "_" + property.replace(".", "_").toUpperCase(Locale.getDefault()));
  }
}