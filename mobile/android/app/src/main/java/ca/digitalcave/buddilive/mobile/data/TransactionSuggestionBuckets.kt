package ca.digitalcave.buddilive.mobile.data

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64

internal data class SuggestionsCache(
    val version: Int = TransactionSuggestionBuckets.VERSION,
    val complete: Boolean = false,
    val items: List<TransactionDescriptionTemplate> = emptyList()
)

data class DescriptionsSyncRequestDto(val version: Int = 1, val bucketHashes: String? = null)

data class DescriptionsSyncResponseDto(
    val success: Boolean,
    val version: Int,
    val mode: String,
    val items: List<TransactionDescriptionTemplate>? = null,
    val buckets: List<SuggestionBucketDto>? = null
)

data class SuggestionBucketDto(val id: Int, val hash: String, val items: List<TransactionDescriptionTemplate>)

data class SuggestionsPatchDto(val upserts: List<TransactionDescriptionTemplate>, val deletes: List<String>)

data class TransactionResponseDto(
    val success: Boolean,
    val id: Long? = null,
    val uuid: String? = null,
    val suggestions: SuggestionsPatchDto? = null,
    val transaction: TransactionRecordDto? = null,
    val previousSourceIds: List<Int>? = null
)

internal object TransactionSuggestionBuckets {
    const val VERSION = 1
    const val COUNT = 256

    fun bucketId(description: String): Int = digest(description.toByteArray(Charsets.UTF_8))[0].toInt() and 0xff

    fun hash(id: Int, items: List<TransactionDescriptionTemplate>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeInt(id)
            output.writeInt(items.size)
            for (item in items.sortedBy { it.description }) {
                output.writeString(item.description)
                output.writeInt(item.splits.size)
                for (split in item.splits) {
                    output.writeString(split.amountNumber.stripTrailingZeros().toPlainString())
                    output.writeInt(split.fromId)
                    output.writeInt(split.toId)
                    output.writeString(split.fromType)
                    output.writeString(split.toType)
                }
            }
        }
        return digest(bytes.toByteArray())
    }

    fun manifest(items: List<TransactionDescriptionTemplate>): String {
        val buckets = items.groupBy { bucketId(it.description) }
        val bytes = ByteArrayOutputStream()
        repeat(COUNT) { bytes.write(hash(it, buckets[it].orEmpty())) }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun replace(cache: SuggestionsCache, response: DescriptionsSyncResponseDto): SuggestionsCache {
        require(response.success && response.version == VERSION) { "Unsupported suggestions response" }
        return when (response.mode) {
            "unchanged" -> {
                require(cache.complete && response.items == null && response.buckets == null)
                cache
            }
            "full" -> {
                require(response.buckets == null)
                val items = requireNotNull(response.items)
                validate(items)
                SuggestionsCache(complete = true, items = items)
            }
            "buckets" -> {
                require(cache.complete && response.items == null)
                val buckets = requireNotNull(response.buckets)
                require(buckets.isNotEmpty() && buckets.map { it.id }.distinct().size == buckets.size)
                for (bucket in buckets) {
                    require(bucket.id in 0 until COUNT)
                    validate(bucket.items)
                    require(bucket.items.all { bucketId(it.description) == bucket.id })
                    require(hash(bucket.id, bucket.items).contentEquals(Base64.getDecoder().decode(bucket.hash)))
                }
                val ids = buckets.map { it.id }.toSet()
                cache.copy(items = cache.items.filter { bucketId(it.description) !in ids } + buckets.flatMap { it.items })
            }
            else -> error("Unknown suggestions response mode")
        }
    }

    fun patch(cache: SuggestionsCache, patch: SuggestionsPatchDto): SuggestionsCache {
        validate(patch.upserts)
        require(patch.deletes.distinct().size == patch.deletes.size)
        val keys = patch.deletes.toSet() + patch.upserts.map { it.description }
        require(patch.upserts.none { it.description in patch.deletes })
        return cache.copy(items = cache.items.filter { it.description !in keys } + patch.upserts)
    }

    fun validate(items: List<TransactionDescriptionTemplate>) {
        require(items.map { it.description }.distinct().size == items.size)
        for (item in items) {
            item.description.toByteArray(Charsets.UTF_8)
            require(item.splits.isNotEmpty())
            item.splits.forEach { it.amountNumber.toPlainString() }
        }
    }

    private fun DataOutputStream.writeString(value: String?) {
        if (value == null) {
            writeInt(-1)
        } else {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }
    }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
