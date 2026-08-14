package xyz.wastebase.strawnfc.wear

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import xyz.wastebase.strawnfc.StrawNfcShared

/**
 * Wear OS primary UI shell. Compose card list / HCE / Tile land in later stages.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "${StrawNfcShared.PRODUCT}\nWear 主 UI\n${BuildConfig.VERSION_NAME}"
                textSize = 16f
                setPadding(32, 32, 32, 32)
            },
        )
    }
}
