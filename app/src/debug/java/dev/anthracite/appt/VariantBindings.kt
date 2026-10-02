package dev.anthracite.appt

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anthracite.appt.engineering.EngineeringVerifierEntry
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.remote.ActiveRemoteSnapshot
import dev.anthracite.appt.remote.DebugS05VerifierAction
import dev.anthracite.appt.remote.DebugS05VerifierCommand
import dev.anthracite.appt.samsung.SamsungModule
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.CoroutineScope

/** Stock debug keeps the real production Samsung adapter for physical testing. */
internal object VariantSamsungTvsFactory {
    fun create(context: Context) = SamsungModule.samsungTvs(context)
}

/** The concrete verifier entry point exists only in the stock debug source set. */
@Composable
internal fun VariantRemoteAccessory(snapshot: ActiveRemoteSnapshot?, host: ActiveRemoteHost) {
    DebugS05VerifierAction(snapshot, host)
}

/** The single stock-debug Engineering Verifier entry, composed beside the graph on every route. */
@Composable
internal fun VariantEngineeringEntry(application: AppTApplication, modifier: Modifier = Modifier) {
    EngineeringVerifierEntry(application = application, modifier = modifier)
}

/**
 * Routes the stock-debug normal control through the bounded verifier only while explicitly armed.
 */
internal fun VariantRemoteCommand(
    command: TvCommand,
    host: ActiveRemoteHost,
    tvId: TvId,
    scope: CoroutineScope,
    fallback: (TvCommand) -> Unit,
) {
    DebugS05VerifierCommand(command, host, tvId, scope, fallback)
}
