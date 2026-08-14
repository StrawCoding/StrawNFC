package xyz.wastebase.strawnfc.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import xyz.wastebase.strawnfc.StrawNfcShared
import xyz.wastebase.strawnfc.ui.BackupActivity
import xyz.wastebase.strawnfc.ui.ScanActivity

/**
 * Thin Companion shell: NFC scan + Wear Data Layer sync + SAF backup.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply { text = StrawNfcShared.PRODUCT; textSize = 22f })
            addView(TextView(context).apply { text = "Companion（掃描即寫入手錶）"; textSize = 16f })
            addView(TextView(context).apply { text = "version ${BuildConfig.VERSION_NAME}"; textSize = 14f })
            addView(TextView(context).apply {
                text = "僅處理自己擁有／已授權卡片；不做金鑰破解或交通／支付卡複製。"
                textSize = 12f
            })
            addView(
                Button(context).apply {
                    text = getString(R.string.open_scan)
                    setOnClickListener {
                        startActivity(Intent(this@MainActivity, ScanActivity::class.java))
                    }
                },
            )
            addView(
                Button(context).apply {
                    text = getString(R.string.open_backup)
                    setOnClickListener {
                        startActivity(Intent(this@MainActivity, BackupActivity::class.java))
                    }
                },
            )
        }
        setContentView(root)
    }
}
