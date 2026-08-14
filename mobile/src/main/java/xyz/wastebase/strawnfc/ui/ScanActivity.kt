package xyz.wastebase.strawnfc.ui

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import xyz.wastebase.strawnfc.mobile.BuildConfig
import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.nfc.NfcCardReader
import xyz.wastebase.strawnfc.sync.CardSender
import java.util.concurrent.Executors

/**
 * Thin Companion scan UI: read Tag → StoredCard → optional Wear Data Layer sync.
 * Classic: no default-key probing. DESFire → PROTOCOL_UNSUPPORTED.
 */
class ScanActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val syncExecutor = Executors.newSingleThreadExecutor()

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var lastCard: StoredCard? = null

    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var syncButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply {
            textSize = 16f
            text = getString(xyz.wastebase.strawnfc.mobile.R.string.scan_status_ready)
        }
        detailView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
        }
        syncButton = Button(this).apply {
            text = getString(xyz.wastebase.strawnfc.mobile.R.string.scan_sync_to_wear)
            isEnabled = false
            setOnClickListener { syncLastCard() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(
                TextView(context).apply {
                    text = getString(xyz.wastebase.strawnfc.mobile.R.string.scan_title)
                    textSize = 22f
                },
            )
            addView(
                TextView(context).apply {
                    text = "v${BuildConfig.VERSION_NAME} · own_only · 不破解／不複製交通支付卡"
                    textSize = 12f
                },
            )
            addView(statusView)
            addView(syncButton)
            addView(detailView)
        }
        setContentView(
            ScrollView(this).apply { addView(content) },
        )

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            statusView.text = getString(xyz.wastebase.strawnfc.mobile.R.string.scan_nfc_unavailable)
        }

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            piFlags,
        )

        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try {
                    addDataType("*/*")
                } catch (_: IntentFilter.MalformedMimeTypeException) {
                    // ignore
                }
            },
        )
        val techLists = arrayOf(
            arrayOf(android.nfc.tech.NfcA::class.java.name),
            arrayOf(android.nfc.tech.IsoDep::class.java.name),
            arrayOf(android.nfc.tech.MifareClassic::class.java.name),
            arrayOf(android.nfc.tech.Ndef::class.java.name),
        )
        pendingIntent?.let { adapter.enableForegroundDispatch(this, it, filters, techLists) }
    }

    override fun onPause() {
        nfcAdapter?.disableForegroundDispatch(this)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onDestroy() {
        syncExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        @Suppress("DEPRECATION")
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        val card = NfcCardReader.fromTag(tag)
        lastCard = card
        syncButton.isEnabled = true
        statusView.text = getString(
            xyz.wastebase.strawnfc.mobile.R.string.scan_status_scanned,
            card.type.name,
            card.uidHex ?: "—",
        )
        detailView.text = buildString {
            appendLine("id=${card.id}")
            appendLine("type=${card.type}")
            appendLine("uid=${card.uidHex}")
            appendLine("atqa=${card.atqaHex} sak=${card.sakHex}")
            appendLine("emulate=${card.emulateStatus}")
            appendLine("classicKeysPresent=${card.classicKeysPresent}")
            appendLine("notes=${card.notes}")
            if (card.ndefPayloadBase64 != null) {
                appendLine("ndefBase64Len=${card.ndefPayloadBase64!!.length}")
            }
            appendLine()
            appendLine(card.toJson())
        }
    }

    private fun syncLastCard() {
        val card = lastCard ?: return
        statusView.text = getString(xyz.wastebase.strawnfc.mobile.R.string.scan_status_syncing)
        syncButton.isEnabled = false
        syncExecutor.execute {
            val result = runCatching {
                CardSender(this@ScanActivity).sendBlocking(card)
            }
            mainHandler.post {
                syncButton.isEnabled = true
                if (result.isSuccess) {
                    statusView.text = getString(
                        xyz.wastebase.strawnfc.mobile.R.string.scan_status_synced,
                        CardSender.pathFor(card),
                    )
                    Toast.makeText(
                        this,
                        getString(xyz.wastebase.strawnfc.mobile.R.string.scan_toast_synced),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    statusView.text = getString(
                        xyz.wastebase.strawnfc.mobile.R.string.scan_status_sync_failed,
                        result.exceptionOrNull()?.message ?: "unknown",
                    )
                }
            }
        }
    }
}
