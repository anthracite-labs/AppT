package dev.anthracite.appt.testing

import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveredTv
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The scripted [SamsungTvs] adapter for `app` tests (samsung-interface.md: `app` tests cross the
 * seam with a fake). Each collection of [discover] is one [Scan] whose events the test sends.
 */
class FakeSamsungTvs : SamsungTvs {
    class Scan {
        internal val events = Channel<DiscoveryEvent>(Channel.UNLIMITED)
        var cancelled = false
            internal set

        var completed = false
            internal set

        fun send(event: DiscoveryEvent) {
            events.trySend(event)
        }
    }

    val scans = mutableListOf<Scan>()

    /** Collections started, i.e. scans started. */
    val discoverCalls: Int
        get() = scans.size

    val latest: Scan
        get() = scans.last()

    override fun discover(): Flow<DiscoveryEvent> = flow {
        val scan = Scan()
        scans += scan
        try {
            for (event in scan.events) {
                emit(event)
                if (event == DiscoveryEvent.Finished || event is DiscoveryEvent.Failed) break
            }
            scan.completed = true
        } finally {
            if (!scan.completed) scan.cancelled = true
        }
    }

    companion object {
        const val LIVING_ROOM_ID = "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"
        const val OLDER_ID = "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"

        fun found(
            id: String = LIVING_ROOM_ID,
            name: String = "Living Room TV",
            availability: ControlAvailability = ControlAvailability.NeedsPairing,
            remembered: Boolean = false,
        ): DiscoveryEvent.Found =
            DiscoveryEvent.Found(
                DiscoveredTv(
                    id = TvId(id),
                    name = name,
                    remembered = remembered,
                    stableIdentity = true,
                    availability = availability,
                )
            )
    }
}
