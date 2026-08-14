package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Dense Wear list metrics: clear TimeText without the default ScalingLazyColumn
 * auto-centering that leaves a large empty band under the clock.
 */
object WearListDefaults {
    /** Top clears TimeText; bottom stays tight on round bezels. */
    val ContentPadding = PaddingValues(
        start = 8.dp,
        end = 8.dp,
        top = 20.dp,
        bottom = 10.dp,
    )
}
