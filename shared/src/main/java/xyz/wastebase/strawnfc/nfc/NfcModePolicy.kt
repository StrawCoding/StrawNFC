package xyz.wastebase.strawnfc.nfc

/**
 * Pure policy for Wear NFC mode transitions (unit-testable, no Android services).
 * Reader and HCE must never share the foreground radio path.
 */
enum class NfcWorkMode {
    IDLE,
    READER,
    HCE,
}

sealed interface NfcModeAction {
    data object StartReader : NfcModeAction
    data object StopReader : NfcModeAction
    data object StartHce : NfcModeAction
    data object StopHce : NfcModeAction
}

object NfcModePolicy {
    data class Transition(
        val next: NfcWorkMode,
        val stopReader: Boolean,
        val stopHce: Boolean,
    )

    fun transition(current: NfcWorkMode, action: NfcModeAction): Transition =
        when (action) {
            NfcModeAction.StartReader -> Transition(
                next = NfcWorkMode.READER,
                stopReader = current == NfcWorkMode.READER,
                stopHce = current == NfcWorkMode.HCE,
            )
            NfcModeAction.StopReader -> Transition(
                next = if (current == NfcWorkMode.READER) NfcWorkMode.IDLE else current,
                stopReader = current == NfcWorkMode.READER,
                stopHce = false,
            )
            NfcModeAction.StartHce -> Transition(
                next = NfcWorkMode.HCE,
                stopReader = current == NfcWorkMode.READER,
                stopHce = current == NfcWorkMode.HCE,
            )
            NfcModeAction.StopHce -> Transition(
                next = if (current == NfcWorkMode.HCE) NfcWorkMode.IDLE else current,
                stopReader = false,
                stopHce = current == NfcWorkMode.HCE,
            )
        }
}
