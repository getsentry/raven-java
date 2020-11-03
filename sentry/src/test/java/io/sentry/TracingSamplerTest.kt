package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracingSamplerTest {
    class Fixture {
        internal fun getSut(randomResult: Double? = null, tracesSampleRate: Double? = null, tracesSamplerResult: Double? = null): TracingSampler {
            val random = mock<Random>()
            if (randomResult != null) {
                whenever(random.nextDouble()).thenReturn(randomResult)
            }
            val options = SentryOptions()
            if (tracesSampleRate != null) {
                options.tracesSampleRate = tracesSampleRate
            }
            if (tracesSamplerResult != null) {
                options.tracesSampler = SentryOptions.TracesSamplerCallback { tracesSamplerResult }
            }
            return TracingSampler(options, random)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when tracesSampleRate is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, tracesSampleRate = 0.2)
        assertFalse(sampler.sample(null))
    }

    @Test
    fun `when tracesSampleRate is set and random returns lower number returns true`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 0.2)
        assertTrue(sampler.sample(null))
    }

    @Test
    fun `when tracesSampleRate is not set, tracesSampler is set and random returns lower number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSamplerResult = 0.2)
        assertTrue(sampler.sample(SamplingContext()))
    }

    @Test
    fun `when tracesSampleRate is not set, tracesSampler is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, tracesSamplerResult = 0.2)
        assertFalse(sampler.sample(SamplingContext()))
    }

    @Test
    fun `when tracesSampleRate is not set, and tracesSampler is not set returns false`() {
        val sampler = fixture.getSut(randomResult = 0.1)
        assertFalse(sampler.sample(SamplingContext()))
    }
}
