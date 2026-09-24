package dev.anthracite.appt.samsung.internal

import android.content.Context
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.SamsungTvs
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.job

/**
 * The production [SamsungTvs] adapter.
 *
 * It is the single scan owner: at most one scan runs per instance. Starting a new collection of
 * [discover] cancels the previous scan and waits for it to release its probes and multicast lock
 * before the new one begins. The previous collector is cancelled (it sees a
 * `CancellationException`), never failed, and receives neither `Finished` nor `Failed`.
 */
internal class SamsungTvsImpl(private val newScan: () -> DiscoveryScan) : SamsungTvs {
    private val activeScan = AtomicReference<Job?>(null)

    override fun discover(): Flow<DiscoveryEvent> = channelFlow {
        val scan = coroutineContext.job
        activeScan.getAndSet(scan)?.cancelAndJoin()
        try {
            newScan().run { event -> send(event) }
        } finally {
            activeScan.compareAndSet(scan, null)
        }
    }

    companion object {
        fun create(context: Context): SamsungTvs {
            val transport = AndroidDiscoveryTransport(context)
            return SamsungTvsImpl { DiscoveryScan(transport) }
        }
    }
}
