package io.sentry

import kotlin.test.Test
import kotlin.test.assertNotNull

class NoOpTransactionTest {

    private val transaction = NoOpTransaction()

    @Test
    fun `startChild does not return null`() {
        assertNotNull(transaction.startChild())
        assertNotNull(transaction.startChild(SpanId.EMPTY_ID))
        assertNotNull(transaction.startChild(SpanId.EMPTY_ID, "op", "desc"))
        assertNotNull(transaction.startChild("op", "desc"))
    }

    @Test
    fun `getSpanContext does not return null`() {
        assertNotNull(transaction.spanContext)
    }
}
