package xyz.wastebase.strawnfc.sync

/**
 * Wear Data Layer paths for phone → watch card sync.
 *
 * Path shape: `/strawnfc/cards/{id}`
 */
object SyncPaths {
    const val CARDS_PREFIX = "/strawnfc/cards"
    const val KEY_CARD_JSON = "card_json"

    fun cardPath(id: String): String {
        require(id.isNotBlank()) { "card id must not be blank" }
        return "$CARDS_PREFIX/$id"
    }

    fun isCardPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        if (!path.startsWith("$CARDS_PREFIX/")) return false
        val id = path.removePrefix("$CARDS_PREFIX/")
        return id.isNotBlank() && !id.contains('/')
    }

    fun cardIdFromPath(path: String): String? {
        if (!isCardPath(path)) return null
        return path.removePrefix("$CARDS_PREFIX/")
    }
}
