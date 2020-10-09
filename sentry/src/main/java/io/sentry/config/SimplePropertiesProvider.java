package io.sentry.config;

import java.util.Properties;

import io.sentry.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@link PropertiesProvider} implementation that delegates to {@link Properties}. */
final class SimplePropertiesProvider implements PropertiesProvider {
  private final @NotNull Properties properties;

  public SimplePropertiesProvider(final @NotNull Properties properties) {
    this.properties = properties;
  }

  @Override
  public @Nullable String getProperty(@NotNull String property) {
    return StringUtils.removeSurrounding(properties.getProperty(property), "\"");
  }
}
