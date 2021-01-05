package io.sentry

import com.nhaarman.mockitokotlin2.mock
import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Cached
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.User
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainEventProcessorTest {
    class Fixture {
        private val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            release = "release"
            dist = "dist"
            serverName = "server"
            sdkVersion = SdkVersion().apply {
                name = "test"
                version = "1.2.3"
            }
        }
        fun getSut(attachThreads: Boolean = true, attachStackTrace: Boolean = true, environment: String? = "environment", tags: Map<String, String> = emptyMap(), sendDefaultPii: Boolean? = null): MainEventProcessor {
            sentryOptions.isAttachThreads = attachThreads
            sentryOptions.isAttachStacktrace = attachStackTrace
            sentryOptions.environment = environment
            if (sendDefaultPii != null) {
                sentryOptions.isSendDefaultPii = sendDefaultPii
            }
            tags.forEach { sentryOptions.setTag(it.key, it.value) }
            return MainEventProcessor(sentryOptions)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flagged, mechanism added`() {
        val sut = fixture.getSut()

        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertSame(crashedThread.id, event.exceptions.first().threadId)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
        assertFalse(event.exceptions.first().mechanism.isHandled)
    }

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flagged, even if its not the current thread`() {
        val sut = fixture.getSut()

        val crashedThread = Thread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertTrue(event.threads.any { it.isCrashed })
    }

    @Test
    fun `When hint is not Cached, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
    }

    @Test
    fun `When hint is ApplyScopeData, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, mock<ApplyScopeData>())

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
    }

    @Test
    fun `data should be applied only if event doesn't have them`() {
        val sut = fixture.getSut()
        var event = generateCrashedEvent()
        event.dist = "eventDist"
        event.environment = "eventEnvironment"
        event.release = "eventRelease"
        event.serverName = "eventServerName"

        event = sut.process(event, null)

        assertEquals("eventRelease", event.release)
        assertEquals("eventEnvironment", event.environment)
        assertEquals("eventDist", event.dist)
        assertEquals("eventServerName", event.serverName)
    }

    @Test
    fun `When hint is Cached, data should not be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, CachedEvent())

        assertNull(event.release)
        assertNull(event.environment)
        assertNull(event.dist)
        assertNull(event.serverName)
        assertNull(event.threads)
    }

    @Test
    fun `When hint is Cached but also ApplyScopeData, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, mock<CustomCachedApplyScopeDataHint>())

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
    }

    @Test
    fun `when attach threads is disabled, threads should not be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = false)

        var event = SentryEvent()
        event = sut.process(event, null)

        assertNull(event.threads)
    }

    @Test
    fun `when attach threads is enabled, threads should be set`() {
        val sut = fixture.getSut()

        var event = SentryEvent()
        event = sut.process(event, null)

        assertNotNull(event.threads)
    }

    @Test
    fun `when attach threads is disabled, but attach stacktrace is enabled, current thread should be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = true)

        var event = SentryEvent()
        event = sut.process(event, null)

        assertEquals(1, event.threads.count())
    }

    @Test
    fun `when attach threads is disabled, but attach stacktrace is enabled and has exceptions, threads should not be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = true)

        var event = SentryEvent(RuntimeException("error"))
        event = sut.process(event, null)

        assertNull(event.threads)
    }

    @Test
    fun `sets sdkVersion in the event`() {
        val sut = fixture.getSut()
        val event = SentryEvent()
        sut.process(event, null)
        assertNotNull(event.sdk)
        assertEquals(event.sdk.name, "test")
        assertEquals(event.sdk.version, "1.2.3")
    }

    @Test
    fun `when event and SentryOptions do not have environment set, sets "production" as environment`() {
        val sut = fixture.getSut(environment = null)
        val event = SentryEvent()
        sut.process(event, null)
        assertEquals("production", event.environment)
    }

    @Test
    fun `when event does not have ip address set and sendDefaultPii is set to true, sets "{{auto}}" as the ip address`() {
        val sut = fixture.getSut(sendDefaultPii = true)
        val event = SentryEvent()
        sut.process(event, null)
        assertNotNull(event.user)
        assertEquals("{{auto}}", event.user.ipAddress)
    }

    @Test
    fun `when event has ip address set and sendDefaultPii is set to true, keeps original ip address`() {
        val sut = fixture.getSut(sendDefaultPii = true)
        val event = SentryEvent()
        event.user = User()
        event.user.ipAddress = "192.168.0.1"
        sut.process(event, null)
        assertNotNull(event.user)
        assertEquals("192.168.0.1", event.user.ipAddress)
    }

    @Test
    fun `when event does not have ip address set and sendDefaultPii is set to false, does not set ip address`() {
        val sut = fixture.getSut(sendDefaultPii = false)
        val event = SentryEvent()
        event.user = User()
        sut.process(event, null)
        assertNotNull(event.user)
        assertNull(event.user.ipAddress)
    }

    @Test
    fun `when event has environment set, does not overwrite environment`() {
        val sut = fixture.getSut(environment = null)
        val event = SentryEvent()
        event.environment = "staging"
        sut.process(event, null)
        assertEquals("staging", event.environment)
    }

    @Test
    fun `when event does not have environment set and SentryOptions have environment set, uses environment from SentryOptions`() {
        val sut = fixture.getSut(environment = "custom")
        val event = SentryEvent()
        sut.process(event, null)
        assertEquals("custom", event.environment)
    }

    @Test
    fun `sets tags from SentryOptions`() {
        val sut = fixture.getSut(tags = mapOf("tag1" to "value1", "tag2" to "value2"))
        val event = SentryEvent()
        sut.process(event, null)
        assertEquals("value1", event.tags["tag1"])
        assertEquals("value2", event.tags["tag2"])
    }

    @Test
    fun `when event has a tag set with the same name as SentryOptions tags, the tag value from the event is retained`() {
        val sut = fixture.getSut(tags = mapOf("tag1" to "value1", "tag2" to "value2"))
        val event = SentryEvent()
        event.setTag("tag2", "event-tag-value")
        sut.process(event, null)
        assertEquals("value1", event.tags["tag1"])
        assertEquals("event-tag-value", event.tags["tag2"])
    }

    private fun generateCrashedEvent(crashedThread: Thread = Thread.currentThread()) = SentryEvent().apply {
        val mockThrowable = mock<Throwable>()
        val actualThrowable = UncaughtExceptionHandlerIntegration.getUnhandledThrowable(crashedThread, mockThrowable)
        throwable = actualThrowable
    }

    internal class CustomCachedApplyScopeDataHint : Cached, ApplyScopeData
}
