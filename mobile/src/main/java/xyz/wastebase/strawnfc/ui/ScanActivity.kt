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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import xyz.wastebase.strawnfc.mobile.BuildConfig
import xyz.wastebase.strawnfc.mobile.R
import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.nfc.NfcCardReader
import xyz.wastebase.strawnfc.sync.CardSender
import xyz.wastebase.strawnfc.sync.NoWearNodeException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Companion: tap card → name → auto-write to paired Wear (Data Layer).
 * own_only; no cracking / transit / payment cloning.
 */
class ScanActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val syncExecutor = Executors.newSingleThreadExecutor()
    private val syncInFlight = AtomicBoolean(false)

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var lastCard: StoredCard? = null

    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var nameInput: EditText
    private lateinit var syncButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply {
            textSize = 16f
            text = getString(R.string.scan_status_ready)
        }
        nameInput = EditText(this).apply {
            hint = getString(R.string.scan_name_hint)
            setSingleLine()
            isEnabled = false
        }
        detailView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
        }
        syncButton = Button(this).apply {
            text = getString(R.string.scan_write_to_wear)
            isEnabled = false
            setOnClickListener { writeLastCardToWear(manual = true) }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(
                TextView(context).apply {
                    text = getString(R.string.scan_title)
                    textSize = 22f
                },
            )
            addView(
                TextView(context).apply {
                    text = getString(R.string.scan_subtitle, BuildConfig.VERSION_NAME)
                    textSize = 12f
                },
            )
            addView(statusView)
            addView(
                TextView(context).apply {
                    text = getString(R.string.scan_name_label)
                    textSize = 13f
                },
            )
            addView(nameInput)
            addView(syncButton)
            addView(detailView)
        }
        setContentView(ScrollView(this).apply { addView(content) })

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            statusView.text = getString(R.string.scan_nfc_unavailable)
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
        val snapshot = NfcCardReader.snapshotFromTag(tag)
        val defaultName = CardSender.defaultCardName(
            NfcCardReader.bytesToHex(snapshot.uidBytes).ifEmpty { null },
        )
        val card = NfcCardReader.fromSnapshot(snapshot, name = defaultName)
        lastCard = card
        nameInput.isEnabled = true
        nameInput.setText(defaultName)
        nameInput.setSelection(nameInput.text.length)
        syncButton.isEnabled = true
        statusView.text = getString(
            R.string.scan_status_scanned,
            card.type.name,
            card.uidHex ?: "—",
        )
        detailView.text = buildString {
            appendLine(getString(R.string.scan_detail_writing))
            appendLine("type=${card.type}")
            appendLine("uid=${card.uidHex}")
            appendLine("emulate=${card.emulateStatus}")
            appendLine("notes=${card.notes}")
        }
        // Scan = write to watch (user asked for direct add → wear).
        writeLastCardToWear(manual = false)
    }

    private fun writeLastCardToWear(manual: Boolean) {
        val base = lastCard ?: return
        if (!syncInFlight.compareAndSet(false, true)) return
        val named = base.copy(
            name = nameInput.text?.toString()?.trim().orEmpty().ifBlank {
                CardSender.defaultCardName(base.uidHex)
            },
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        lastCard = named
        statusView.text = getString(R.string.scan_status_syncing)
        syncButton.isEnabled = false
        syncExecutor.execute {
            val result = runCatching {
                CardSender(this@ScanActivity).sendBlocking(named)
            }
            mainHandler.post {
                syncInFlight.set(false)
                syncButton.isEnabled = true
                if (result.isSuccess) {
                    statusView.text = getString(R.string.scan_status_written, named.name)
                    detailView.text = buildString {
                        appendLine(getString(R.string.scan_detail_written_ok))
                        appendLine("name=${named.name}")
                        appendLine("type=${named.type}")
                        appendLine("uid=${named.uidHex}")
                        appendLine("path=${CardSender.pathFor(named)}")
                    }
                    Toast.makeText(
                        this,
                        getString(R.string.scan_toast_written, named.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    val err = result.exceptionOrNull()
                    val message = when (err) {
                        is NoWearNodeException -> err.message
                        else -> err?.message ?: "unknown"
                    }
                    statusView.text = getString(R.string.scan_status_sync_failed, message)
                    if (manual || err is NoWearNodeException) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
