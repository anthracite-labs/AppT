package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.RemoteKey
import java.net.URLEncoder
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The adopted Tizen remote-control channel (docs/architecture/protocol.md#session-frames).
 *
 * Everything wire-shaped lives here and nowhere else: the remote URL, the client-name encoding, the
 * outbound frame shape, the internal key-name table, the inbound event names, and the parser
 * limits. `app` imports none of it, and none of it is logged (protocol.md#logging-from-this-layer).
 */
internal object RemoteChannel {
    /** protocol.md: the cleartext client name the television shows in its Allow/Deny prompt. */
    const val CLIENT_NAME = "AppT"

    /** protocol.md: standard Base64, no newlines, of the UTF-8 client name. */
    val CLIENT_NAME_ENCODED: String =
        Base64.getEncoder().encodeToString(CLIENT_NAME.toByteArray(Charsets.UTF_8))

    /** protocol.md#parser-limits: one frame. Over-limit input is a malformed frame, not a crash. */
    const val MAX_FRAME_CODE_POINTS: Int = 64 * 1024

    /** protocol.md#parser-limits: JSON depth. */
    const val MAX_JSON_DEPTH: Int = 8

    /** A trusted channel connect. Carries the approval token. */
    const val EVENT_CONNECT = "ms.channel.connect"

    /** The television is asking for approval, or refused the presented token. */
    const val EVENT_UNAUTHORIZED = "ms.channel.unauthorized"

    /** Approval timeout while waiting for approval; connection loss while ready. */
    const val EVENT_TIME_OUT = "ms.channel.timeOut"

    /** Connection loss. */
    const val EVENT_CLIENT_DISCONNECT = "ms.channel.clientDisconnect"

    private const val METHOD_REMOTE_CONTROL = "ms.remote.control"

    /**
     * The one internal key-name table (protocol.md: "The map from `RemoteKey` to that name stays in
     * one table inside `samsung`").
     */
    private val KEY_NAMES: Map<RemoteKey, String> =
        mapOf(
            RemoteKey.Up to "KEY_UP",
            RemoteKey.Down to "KEY_DOWN",
            RemoteKey.Left to "KEY_LEFT",
            RemoteKey.Right to "KEY_RIGHT",
            RemoteKey.Enter to "KEY_ENTER",
            RemoteKey.Back to "KEY_RETURN",
            RemoteKey.Home to "KEY_HOME",
            RemoteKey.VolumeUp to "KEY_VOLUP",
            RemoteKey.VolumeDown to "KEY_VOLDOWN",
            RemoteKey.Mute to "KEY_MUTE",
            RemoteKey.Power to "KEY_POWER",
        )

    /** protocol.md: the remote-channel path, origin-form, first contact, no token. */
    fun remoteRequestTarget(): String {
        val encodedName = URLEncoder.encode(CLIENT_NAME_ENCODED, Charsets.UTF_8.name())
        return "/api/v2/channels/samsung.remote.control?name=$encodedName"
    }

    /**
     * Host header / URL authority for [television]. IPv6 literals are bracketed; a link-local scope
     * marker is percent-encoded.
     */
    fun authority(television: ConfirmedTelevision): String {
        val host =
            if (':' in television.host && !television.host.startsWith("[")) {
                "[${television.host.replace("%", "%25")}]"
            } else {
                television.host
            }
        return "$host:${television.remotePort}"
    }

    /**
     * The remote-channel URL for [television].
     *
     * [token] is attached only when the caller has already established the television's security
     * identity on this connection (connection.md#ordering-relative-to-secrets): a saved TLS pin
     * that matched, on the TLS channel. First contact and every plaintext connection carry no
     * token, so a saved token is structurally absent from the attempted URL until the identity
     * check has passed. The value is URL-encoded and never logged
     * (protocol.md#logging-from-this-layer).
     */
    fun remoteUrl(television: ConfirmedTelevision, token: String? = null): String {
        val scheme = if (television.tls) "wss" else "ws"
        val target = remoteRequestTarget()
        if (token == null) return "$scheme://${authority(television)}$target"
        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        return "$scheme://${authority(television)}$target&token=$encodedToken"
    }

    /** The outbound frame for one key tap, in the documented `ms.remote.control` shape. */
    fun tapFrame(key: RemoteKey): String {
        val keyName = KEY_NAMES[key] ?: throw IllegalArgumentException("no internal name for $key")
        return buildJsonObject {
                put("method", JsonPrimitive(METHOD_REMOTE_CONTROL))
                put(
                    "params",
                    buildJsonObject {
                        put("Cmd", JsonPrimitive("Click"))
                        put("DataOfCmd", JsonPrimitive(keyName))
                        put("Option", JsonPrimitive("false"))
                        put("TypeOfRemote", JsonPrimitive("SendRemoteKey"))
                    },
                )
            }
            .toString()
    }

    /**
     * One parsed inbound channel event, or null when the frame is malformed, oversized, or carries
     * no event name. Malformed input is contained here: the caller drops the frame and the session
     * continues (connection.md: "Malformed frames do not throw across the seam").
     */
    fun parseEvent(frame: String): ChannelEvent? {
        if (!withinFrameLimits(frame)) return null
        val root = parseObject(frame) ?: return null
        return root.channelEvent()
    }

    /** The object as a channel event, or null when it carries no event name. */
    private fun JsonObject.channelEvent(): ChannelEvent? {
        val name = text("event") ?: return null
        return ChannelEvent(name = name, token = token())
    }

    /**
     * protocol.md#parser-limits: one frame, one depth. Over-limit input is malformed, not a crash.
     */
    private fun withinFrameLimits(frame: String): Boolean =
        frame.codePointCount(0, frame.length) <= MAX_FRAME_CODE_POINTS &&
            DeviceInfoParser.withinDepth(frame, MAX_JSON_DEPTH)

    /**
     * The frame as a JSON object, or null when it is not one.
     *
     * kotlinx.serialization raises SerializationException, an IllegalArgumentException, for
     * anything that is not valid JSON, including a non-object root. Contained here, never across
     * the seam.
     */
    private fun parseObject(frame: String): JsonObject? =
        try {
            Json.parseToJsonElement(frame).jsonObject
        } catch (ignored: IllegalArgumentException) {
            null
        }
}

/**
 * One inbound `ms.channel.*` event. [token] is transient live-session evidence, never persisted.
 */
internal data class ChannelEvent(val name: String, val token: String?)

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/**
 * protocol.md: "Read token from `data.token`, else from the client attributes." The data member is
 * an object on most televisions and a bare string on some; both are accepted and both are
 * transient.
 */
private fun JsonObject.token(): String? {
    val data = this["data"] ?: return null
    return when (data) {
        is JsonPrimitive -> data.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> data.text("token")
        else -> null
    }
}
