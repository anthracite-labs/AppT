package dev.anthracite.appt

import android.content.Context
import androidx.compose.runtime.Composable
import dev.anthracite.appt.benchmark.BenchmarkReadySamsungTvs
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.remote.ActiveRemoteSnapshot
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.CoroutineScope

/** The Baseline Profile and macrobenchmark-only variants use the deterministic in-process fake. */
internal object VariantSamsungTvsFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context) = BenchmarkReadySamsungTvs()
}

/** Hosted benchmark variants contain no physical verifier UI. */
@Composable
internal fun VariantRemoteAccessory(
    @Suppress("UNUSED_PARAMETER") snapshot: ActiveRemoteSnapshot?,
    @Suppress("UNUSED_PARAMETER") host: ActiveRemoteHost,
) = Unit

/** Hosted benchmark interactions retain the ordinary ViewModel command path. */
internal fun VariantRemoteCommand(
    command: TvCommand,
    @Suppress("UNUSED_PARAMETER") host: ActiveRemoteHost,
    @Suppress("UNUSED_PARAMETER") tvId: TvId,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    fallback: (TvCommand) -> Unit,
) {
    fallback(command)
}
