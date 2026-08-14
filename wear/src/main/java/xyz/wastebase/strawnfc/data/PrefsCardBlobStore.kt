package xyz.wastebase.strawnfc.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

class PrefsCardBlobStore(
    private val prefs: SharedPreferences,
    private val key: String = KEY_BLOB,
) : CardBlobStore {
    override fun read(): ByteArray? {
        val encoded = prefs.getString(key, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    override fun write(data: ByteArray) {
        prefs.edit()
            .putString(key, Base64.encodeToString(data, Base64.NO_WRAP))
            .apply()
    }

    override fun clear() {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFS_NAME = "strawnfc_cards"
        private const val KEY_BLOB = "cards_blob_v1"

        fun create(context: Context): PrefsCardBlobStore =
            PrefsCardBlobStore(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
    }
}
