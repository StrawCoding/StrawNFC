package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Root surface for every Wear screen — solid background so round watches never show
 * a transparent / empty frame (symptom: 「畫面消失」).
 */
@Composable
fun WearShell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
    ) {
        content()
    }
}

@Composable
fun WearLoadingPlaceholder(label: String = "載入中…") {
    WearShell {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
            )
        }
    }
}
