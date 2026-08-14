package xyz.wastebase.strawnfc.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import xyz.wastebase.strawnfc.backup.BackupCodec
import xyz.wastebase.strawnfc.backup.BackupEnvelope
import xyz.wastebase.strawnfc.mobile.BuildConfig
import xyz.wastebase.strawnfc.mobile.R
import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.model.toJson

/**
 * Companion SAF export/import for encrypted `.strawnfc` backups.
 * Cards are held in-memory for this thin Companion session (phone is scan+sync+backup file I/O).
 */
class BackupActivity : ComponentActivity() {
    private lateinit var statusView: TextView
    private lateinit var passwordInput: EditText
    private lateinit var cardsJsonInput: EditText

    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            statusView.text = "已取消匯出"
            return@registerForActivityResult
        }
        exportToUri(uri)
    }

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            statusView.text = "已取消匯入"
            return@registerForActivityResult
        }
        importFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passwordInput = EditText(this).apply {
            hint = "備份密碼"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        cardsJsonInput = EditText(this).apply {
            hint = "卡片 JSON 陣列（匯出用；可由掃描結果貼上）"
            minLines = 4
            setText(intent.getStringExtra(EXTRA_CARDS_JSON).orEmpty())
        }
        statusView = TextView(this).apply {
            textSize = 14f
            text = "AES-GCM .${BackupCodec.FILE_EXTENSION} · own_only"
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply {
                text = getString(R.string.backup_title)
                textSize = 22f
            })
            addView(TextView(context).apply {
                text = "v${BuildConfig.VERSION_NAME} · 不做破解／不宣稱可開門禁"
                textSize = 12f
            })
            addView(passwordInput)
            addView(cardsJsonInput)
            addView(Button(context).apply {
                text = getString(R.string.backup_export)
                setOnClickListener {
                    createDoc.launch("strawnfc-backup.${BackupCodec.FILE_EXTENSION}")
                }
            })
            addView(Button(context).apply {
                text = getString(R.string.backup_import)
                setOnClickListener {
                    openDoc.launch(arrayOf("*/*", "application/octet-stream"))
                }
            })
            addView(statusView)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun exportToUri(uri: Uri) {
        val password = passwordInput.text?.toString()?.toCharArray() ?: CharArray(0)
        if (password.isEmpty()) {
            statusView.text = "請輸入密碼"
            return
        }
        val cards = parseCardsOrNull() ?: return
        try {
            val blob = BackupCodec.export(cards, password)
            contentResolver.openOutputStream(uri)?.use { it.write(blob) }
                ?: error("無法寫入")
            statusView.text = "已匯出 ${cards.size} 張 · ${blob.size} bytes"
            Toast.makeText(this, R.string.backup_export_ok, Toast.LENGTH_SHORT).show()
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_LAST_BACKUP_B64, Base64.encodeToString(blob, Base64.NO_WRAP)),
            )
        } catch (e: Exception) {
            statusView.text = "匯出失敗：${e.message}"
        } finally {
            password.fill('\u0000')
        }
    }

    private fun importFromUri(uri: Uri) {
        val password = passwordInput.text?.toString()?.toCharArray() ?: CharArray(0)
        if (password.isEmpty()) {
            statusView.text = "請輸入密碼"
            return
        }
        try {
            val blob = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("無法讀取")
            val envelope: BackupEnvelope = BackupCodec.import(blob, password)
            cardsJsonInput.setText(envelope.cards.toJson())
            statusView.text = "已匯入 ${envelope.cards.size} 張（${envelope.format}）"
            Toast.makeText(this, R.string.backup_import_ok, Toast.LENGTH_SHORT).show()
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_IMPORTED_CARDS_JSON, envelope.cards.toJson()),
            )
        } catch (e: Exception) {
            statusView.text = "匯入失敗：${e.message}"
        } finally {
            password.fill('\u0000')
        }
    }

    private fun parseCardsOrNull(): List<StoredCard>? {
        val text = cardsJsonInput.text?.toString().orEmpty().trim()
        if (text.isEmpty()) {
            statusView.text = "請提供卡片 JSON"
            return null
        }
        return try {
            StoredCard.listFromJson(text)
        } catch (e: Exception) {
            // Also accept single card or newline-wrapped envelope-less list via raw reader
            try {
                val raw = contentLike(text)
                StoredCard.listFromJson(raw)
            } catch (e2: Exception) {
                statusView.text = "JSON 無法解析：${e.message}"
                null
            }
        }
    }

    private fun contentLike(text: String): String = text

    companion object {
        const val EXTRA_CARDS_JSON = "cards_json"
        const val EXTRA_IMPORTED_CARDS_JSON = "imported_cards_json"
        const val EXTRA_LAST_BACKUP_B64 = "last_backup_b64"
    }
}
