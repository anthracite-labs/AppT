package dev.anthracite.appt

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.remote.ActiveRemoteSnapshot
import dev.anthracite.appt.samsung.SamsungModule
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.CoroutineScope

/** The distributable release variant is permanently wired to the production adapter. */
internal object VariantSamsungTvsFactory {
    fun create(context: Context) = SamsungModule.samsungTvs(context)
}

/** No engineering accessory is compiled into the distributable release variant. */
@Composable
internal fun VariantRemoteAccessory(
    @Suppress("UNUSED_PARAMETER") snapshot: ActiveRemoteSnapshot?,
    @Suppress("UNUSED_PARAMETER") host: ActiveRemoteHost,
) = Unit

/** No engineering verifier entry is compiled into the distributable release variant. */
@Composable
internal fun VariantEngineeringEntry(
    @Suppress("UNUSED_PARAMETER") application: AppTApplication,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
) = Unit

/** The non-debug variant always preserves the ordinary RemoteViewModel dispatch path. */
internal fun VariantRemoteCommand(
    command: TvCommand,
    @Suppress("UNUSED_PARAMETER") host: ActiveRemoteHost,
    @Suppress("UNUSED_PARAMETER") tvId: TvId,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    fallback: (TvCommand) -> Unit,
) {
    fallback(command)
}
