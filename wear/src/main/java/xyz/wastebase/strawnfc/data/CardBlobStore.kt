package xyz.wastebase.strawnfc.data

/**
 * Persistence for a single encrypted blob (card inventory).
 */
interface CardBlobStore {
    fun read(): ByteArray?

    fun write(data: ByteArray)

    fun clear()
}

class InMemoryCardBlobStore : CardBlobStore {
    private var blob: ByteArray? = null

    override fun read(): ByteArray? = blob?.copyOf()

    override fun write(data: ByteArray) {
        blob = data.copyOf()
    }

    override fun clear() {
        blob = null
    }
}
