package io.sentry.protocol

import io.sentry.SpanContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import org.junit.Test

class ContextsTest {
    @Test
    fun `cloning contexts wont have the same references`() {
        val contexts = Contexts()
        contexts.setApp(App())
        contexts.setBrowser(Browser())
        contexts.setDevice(Device())
        contexts.setOperatingSystem(OperatingSystem())
        contexts.setRuntime(SentryRuntime())
        contexts.setGpu(Gpu())
        contexts.trace = SpanContext()

        val clone = contexts.clone()

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertNotSame(contexts.app, clone.app)
        assertNotSame(contexts.browser, clone.browser)
        assertNotSame(contexts.device, clone.device)
        assertNotSame(contexts.operatingSystem, clone.operatingSystem)
        assertNotSame(contexts.runtime, clone.runtime)
        assertNotSame(contexts.gpu, clone.gpu)
        assertNotSame(contexts.trace, clone.trace)
    }

    @Test
    fun `cloning contexts will have the same values`() {
        val contexts = Contexts()
        contexts["some-property"] = "some-value"
        contexts.trace = SpanContext()
        contexts.trace!!.description = "desc"

        val clone = contexts.clone()

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertEquals(contexts["some-property"], clone["some-property"])
        assertEquals(contexts.trace!!.description, clone.trace!!.description)
    }
}
