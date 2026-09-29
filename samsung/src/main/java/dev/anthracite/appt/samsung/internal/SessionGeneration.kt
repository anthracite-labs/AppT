package dev.anthracite.appt.samsung.internal

/**
 * One session's standing in the connection-generation order
 * (docs/architecture/connection.md#evidence-and-race-handling-harvest, APPT DECISION).
 *
 * Every asynchronous open, repair and destructive transition belongs to a generation. A newer
 * `open`, `close`, confirmed repair, or local `forget` invalidates older work: a late callback from
 * an invalidated generation may clean up its own resources, but it may not publish `Ready`, may not
 * persist a token, pin or device record, and may not touch a replacement session. The regression
 * proof is `supersededConnectCannotResurrectSession` (docs/architecture/testing.md).
 *
 * The method pattern (not a property) keeps this a `fun interface`, so the owner can pass a plain
 * `{ ... }` check.
 */
internal fun interface SessionGeneration {
    /** True while this generation is the newest work for its television. */
    fun isActive(): Boolean

    companion object {
        /** For callers that run no superseded work: one session, always current. */
        val ALWAYS_CURRENT = SessionGeneration { true }
    }
}
