package io.sentry.config.location;

import static java.util.Arrays.asList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CompoundResourceLocatorTest {

    @Test
    void testReturnsFirstNonNullValue() {
        // given
        ConfigurationResourceLocator first = mock(ConfigurationResourceLocator.class);
        ConfigurationResourceLocator second = mock(ConfigurationResourceLocator.class);
        ConfigurationResourceLocator third = mock(ConfigurationResourceLocator.class);

        when(second.getConfigurationResourcePath()).thenReturn("non-null");

        CompoundResourceLocator compoundLocator = new CompoundResourceLocator(asList(first, second, third));

        // when
        String val = compoundLocator.getConfigurationResourcePath();

        // then
        Assertions.assertEquals("non-null", val);

        verify(first, times(1)).getConfigurationResourcePath();
        verify(second, times(1)).getConfigurationResourcePath();
        verify(third, never()).getConfigurationResourcePath();
    }
}
