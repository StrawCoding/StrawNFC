package xyz.wastebase.strawnfc.model

import kotlinx.serialization.Serializable

/**
 * Honest emulate status — Stock HCE must never claim arbitrary UID door access.
 */
@Serializable
enum class EmulationCapability {
    UNKNOWN,
    SUPPORTED,
    DEVICE_UNSUPPORTED,
    PROTOCOL_UNSUPPORTED,
}
