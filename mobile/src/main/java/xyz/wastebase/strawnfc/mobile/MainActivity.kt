package xyz.wastebase.strawnfc.mobile

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import xyz.wastebase.strawnfc.StrawNfcShared

/**
 * Thin Companion shell: NFC scan + Wear Data Layer sync land in later stages.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply { text = StrawNfcShared.PRODUCT; textSize = 22f })
            addView(TextView(context).apply { text = "Companion（掃描＋同步）"; textSize = 16f })
            addView(TextView(context).apply { text = "version ${BuildConfig.VERSION_NAME}"; textSize = 14f })
        }
        setContentView(root)
    }
}
