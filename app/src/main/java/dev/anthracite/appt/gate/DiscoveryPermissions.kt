package dev.anthracite.appt.gate

/**
 * Which runtime permissions the gate must request before the V1 discovery probes
 * (docs/architecture/discovery.md#permission-gate).
 *
 * At targetSdk 36 the V1 probes (SSDP over raw sockets, `NsdManager`, a multicast lock) need only
 * install-time permissions, so there is nothing to request: Continue on the explanation grants the
 * gate without a system prompt. `NEARBY_WIFI_DEVICES` and location are never requested for these
 * probes, and `ACCESS_LOCAL_NETWORK` is neither declared nor requested while the target is 36.
 *
 * A higher target is not V1. It must adopt `ACCESS_LOCAL_NETWORK` through this same gate first, so
 * rather than silently returning "nothing to request" for a target it was not designed for, this
 * fails loudly and forces that decision to be made here.
 */
object DiscoveryPermissions {
    const val V1_TARGET_SDK: Int = 36

    fun runtimeRequestFor(targetSdk: Int): List<String> {
        check(targetSdk <= V1_TARGET_SDK) {
            "targetSdk $targetSdk is not V1: the permission gate must adopt ACCESS_LOCAL_NETWORK " +
                "before the target moves past $V1_TARGET_SDK (docs/architecture/discovery.md)"
        }
        return emptyList()
    }
}
