package ca.digitalcave.buddilive.mobile.data

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Base64

data class TransactionRecordDto(
    val id: Long,
    val uuid: String?,
    val dateIso: String,
    val description: String,
    val number: String?,
    val splits: List<TransactionRecordSplitDto>
)

data class TransactionRecordSplitDto(val id: Long?, val amountNumber: BigDecimal, val fromId: Int, val toId: Int, val memo: String?)

data class TransactionsSyncRequestDto(val version: Int = 1, val accounts: List<TransactionAccountSyncRequestDto>)

data class TransactionAccountSyncRequestDto(val sourceId: Long, val bucketHashes: String?)

data class TransactionsSyncResponseDto(val success: Boolean, val version: Int, val accounts: List<TransactionAccountSyncResponseDto>, val sourceNames: Map<Int, String> = emptyMap())

data class TransactionAccountSyncResponseDto(
    val sourceId: Long,
    val total: Int,
    val revision: String,
    val mode: String,
    val items: List<TransactionRecordDto>? = null,
    val buckets: List<TransactionBucketDto>? = null
)

data class TransactionBucketDto(val id: Int, val hash: String, val items: List<TransactionRecordDto>)

internal object TransactionBuckets {
    const val VERSION = 1
    const val COUNT = 16
    const val LIMIT = 100

    fun bucketId(id: Long): Int = digest(id.toString().toByteArray(Charsets.UTF_8))[0].toInt() and (COUNT - 1)

    fun hash(sourceId: Long, bucketId: Int, items: List<TransactionRecordDto>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeInt(sourceId.toInt())
            output.writeInt(bucketId)
            output.writeInt(items.size)
            for (item in items.sortedBy { it.id }) {
                output.writeLong(item.id)
                output.writeString(item.uuid)
                output.writeString(item.dateIso)
                output.writeString(item.description)
                output.writeString(item.number)
                output.writeInt(item.splits.size)
                for (split in item.splits) {
                    output.writeLong(split.id ?: -1)
                    output.writeString(split.amountNumber.stripTrailingZeros().toPlainString())
                    output.writeInt(split.fromId)
                    output.writeInt(split.toId)
                    output.writeString(split.memo)
                }
            }
        }
        return digest(bytes.toByteArray())
    }

    fun manifest(sourceId: Long, page: TransactionsResponseDto): String {
        val items = page.data.map { it.toRecord() }
        validatePage(sourceId, page.total, items, page.partial)
        val buckets = items.groupBy { bucketId(it.id) }
        val bytes = ByteArrayOutputStream()
        repeat(COUNT) { bytes.write(hash(sourceId, it, buckets[it].orEmpty())) }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun replace(cache: TransactionsResponseDto?, response: TransactionAccountSyncResponseDto): TransactionsResponseDto {
        require(Base64.getDecoder().decode(response.revision).size == 32)
        val items = when (response.mode) {
            "full" -> {
                require(response.buckets == null)
                requireNotNull(response.items)
            }
            "unchanged" -> {
                require(response.items == null && response.buckets == null)
                val previous = requireNotNull(cache)
                manifest(response.sourceId, previous)
                previous.data.map { it.toRecord() }
            }
            "buckets" -> {
                require(response.items == null)
                val previous = requireNotNull(cache)
                manifest(response.sourceId, previous)
                val buckets = requireNotNull(response.buckets)
                require(buckets.isNotEmpty() && buckets.map { it.id }.distinct().size == buckets.size)
                for (bucket in buckets) {
                    require(bucket.id in 0 until COUNT)
                    validate(response.sourceId, bucket.items)
                    require(bucket.items.all { bucketId(it.id) == bucket.id })
                    require(hash(response.sourceId, bucket.id, bucket.items).contentEquals(Base64.getDecoder().decode(bucket.hash)))
                }
                val replaced = buckets.map { it.id }.toSet()
                previous.data.map { it.toRecord() }.filter { bucketId(it.id) !in replaced } + buckets.flatMap { it.items }
            }
            else -> error("Unknown transaction response mode")
        }
        validatePage(response.sourceId, response.total, items)
        return TransactionsResponseDto(true, ordered(items).map { it.toTransaction() }, response.total)
    }

    fun patch(sourceId: Long, cache: TransactionsResponseDto, id: Long, current: TransactionRecordDto?, previousSourceIds: List<Int>): TransactionsResponseDto {
        val previous = cache.data.firstOrNull { it.id == id || current?.uuid != null && it.uuid == current.uuid }
        val matching = current?.splits.orEmpty().filter { it.fromId.toLong() == sourceId || it.toId.toLong() == sourceId }
        val after = current?.takeIf { matching.isNotEmpty() }?.copy(splits = matching)
        val before = previous != null || sourceId.toInt() in previousSourceIds
        if (!before && after == null) return cache
        after?.let { validate(sourceId, listOf(it)) }
        val items = cache.data.filterNot { it.id == id || current?.uuid != null && it.uuid == current.uuid }.map { it.toRecord() } + listOfNotNull(after)
        val recent = ordered(items).take(LIMIT)
        val total = maxOf(recent.size, cache.total + (if (after == null) 0 else 1) - (if (before) 1 else 0))
        // A removed recent row can expose an older record that is not cached yet.
        return TransactionsResponseDto(true, recent.map { it.toTransaction() }, total, partial = true)
    }

    private fun ordered(items: List<TransactionRecordDto>): List<TransactionRecordDto> =
        items.sortedWith(compareByDescending<TransactionRecordDto> { it.dateIso }
            .thenByDescending { item -> item.splits.mapNotNull { it.id }.maxOrNull() ?: 0L }.thenByDescending { it.id })

    private fun validatePage(sourceId: Long, total: Int, items: List<TransactionRecordDto>, partial: Boolean = false) {
        require(total >= 0 && if (partial) items.size <= minOf(total, LIMIT) else items.size == minOf(total, LIMIT))
        validate(sourceId, items)
    }

    private fun validate(sourceId: Long, items: List<TransactionRecordDto>) {
        require(sourceId in 1..Int.MAX_VALUE.toLong())
        require(items.map { it.id }.distinct().size == items.size)
        for (item in items) {
            require(item.id > 0)
            LocalDate.parse(item.dateIso)
            item.description.toByteArray(Charsets.UTF_8)
            for (split in item.splits) {
                require(split.fromId.toLong() == sourceId || split.toId.toLong() == sourceId)
                split.amountNumber.toPlainString()
            }
        }
    }

    private fun TransactionRecordDto.toTransaction(): TransactionDto = TransactionDto(id, uuid, dateIso, dateIso, description, number,
        splits.map { SplitDto(it.id, null, it.amountNumber, it.fromId, null, it.toId, null, it.memo) })

    private fun DataOutputStream.writeString(value: String?) {
        if (value == null) writeInt(-1)
        else {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }
    }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}

internal fun TransactionDto.toRecord(): TransactionRecordDto = TransactionRecordDto(id, uuid, dateIso, description, number,
    splits.map { TransactionRecordSplitDto(it.id, requireNotNull(it.amountNumber), it.fromId, it.toId, it.memo) })
