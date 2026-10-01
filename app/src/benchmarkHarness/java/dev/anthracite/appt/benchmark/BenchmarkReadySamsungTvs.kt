package dev.anthracite.appt.benchmark

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveredTv
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.ForgetResult
import dev.anthracite.appt.samsung.RedactedDiagnosticReport
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCapabilities
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic S05-only Samsung seam for the AndroidX profile and macrobenchmark variants.
 *
 * This source is attached only to `benchmarkRelease` and `nonMinifiedRelease`; the stock `release`
 * and stock `debug` variants never contain it. The fake follows the accepted `FakeSamsungTvs`
 * Ready-capability semantics and has no socket, protocol frame, TV identity, or wire response.
 */
internal class BenchmarkReadySamsungTvs : SamsungTvs {
    private val television =
        DiscoveredTv(
            id = TvId(BENCHMARK_TV_ID),
            name = BENCHMARK_TV_NAME,
            remembered = false,
            stableIdentity = true,
            availability = ControlAvailability.ReadyToOpen,
        )

    override fun discover(): Flow<DiscoveryEvent> = flow {
        emit(DiscoveryEvent.Found(television))
        emit(DiscoveryEvent.Finished)
    }

    override fun open(id: TvId, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope): RemoteSession {
        check(id == television.id) { "benchmark fake only opens its fixture television" }
        return BenchmarkReadySession()
    }

    override suspend fun forget(@Suppress("UNUSED_PARAMETER") id: TvId): ForgetResult =
        ForgetResult.Forgotten

    override fun rememberedIds(): Set<TvId> = emptySet()

    override fun redactedDiagnostics(): RedactedDiagnosticReport = RedactedDiagnosticReport(emptyList())

    private class BenchmarkReadySession : RemoteSession {
        private val mutableSnapshot =
            MutableStateFlow(SessionSnapshot(SessionState.Ready, TvCapabilities(RemoteKey.entries.toSet())))

        override val snapshot: StateFlow<SessionSnapshot> = mutableSnapshot.asStateFlow()

        override suspend fun command(command: TvCommand): CommandResult =
            if (
                mutableSnapshot.value.state == SessionState.Ready &&
                    command is TvCommand.Tap &&
                    command.key in mutableSnapshot.value.capabilities.keys
            ) {
                CommandResult.Accepted
            } else {
                CommandResult.Rejected(TvFailure.Unavailable)
            }

        override suspend fun retryApproval() = Unit

        override suspend fun confirmRepair() = Unit

        override fun close() {
            mutableSnapshot.value = mutableSnapshot.value.copy(state = SessionState.Closed)
        }
    }

    private companion object {
        const val BENCHMARK_TV_ID = "00000000-0000-4000-8000-000000000005"
        const val BENCHMARK_TV_NAME = "Benchmark TV"
    }
}
