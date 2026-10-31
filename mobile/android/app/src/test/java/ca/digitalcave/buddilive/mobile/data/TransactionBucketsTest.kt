package ca.digitalcave.buddilive.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class TransactionBucketsTest {

    @Test
    fun sharedGoldenManifestAndPresentationFieldsAreExcluded() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/transaction-buckets.json")!!.reader()).asJsonObject
        val records = Gson().fromJson(fixture.get("items"), Array<TransactionRecordDto>::class.java).toList()
        val page = page(records)
        val expected = fixture.get("manifest").asString
        assertEquals(expected, TransactionBuckets.manifest(1, page))
        assertEquals(684, expected.length)
        assertEquals(512, Base64.getDecoder().decode(expected).size)
        assertEquals(expected, TransactionBuckets.manifest(1, page.copy(data = page.data.map { transaction -> transaction.copy(
            date = "different date format", splits = transaction.splits.map { it.copy(amount = "formatted differently", from = "Renamed", to = "Category") }
        ) })))
        records.forEachIndexed { index, record -> assertEquals(fixture.getAsJsonArray("bucketIds")[index].asInt, TransactionBuckets.bucketId(record.id)) }
    }

    @Test
    fun invalidResponsesAreRejectedAndEmptyBucketsClearRecords() {
        val item = TransactionRecordDto(1, "uuid", "2026-10-31", "Coffee", null, listOf(TransactionRecordSplitDto(1, "1.00".toBigDecimal(), 1, 2, null)))
        val id = TransactionBuckets.bucketId(item.id)
        val previous = page(listOf(item))
        val revision = Base64.getEncoder().encodeToString(ByteArray(32))
        val empty = TransactionBucketDto(id, Base64.getEncoder().encodeToString(TransactionBuckets.hash(1, id, emptyList())), emptyList())
        val response = TransactionAccountSyncResponseDto(1, 0, revision, "buckets", buckets = listOf(empty))
        assertTrue(TransactionBuckets.replace(previous, response).data.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { TransactionBuckets.replace(previous, response.copy(buckets = listOf(empty, empty))) }
        assertThrows(IllegalArgumentException::class.java) { TransactionBuckets.replace(previous, response.copy(buckets = listOf(empty.copy(hash = "bad")))) }
        assertThrows(IllegalArgumentException::class.java) { TransactionBuckets.replace(null, response) }
        assertThrows(IllegalArgumentException::class.java) { TransactionBuckets.replace(previous, response.copy(total = 1)) }
        assertEquals(1, previous.data.size)
    }

    @Test
    fun deletingAtWindowBoundaryDownloadsOnlyTheMissingBucket() {
        fun record(id: Long) = TransactionRecordDto(id, "uuid-$id", "2026-10-31", "Transaction $id", null,
            listOf(TransactionRecordSplitDto(id, "1.00".toBigDecimal(), 1, 2, null)))
        val previous = page((101L downTo 2L).map { record(it) }).copy(total = 101)
        val patched = TransactionBuckets.patch(1, previous, 101, null, listOf(1, 2))
        assertTrue(patched.partial)
        assertEquals(99, patched.data.size)
        assertEquals(684, TransactionBuckets.manifest(1, patched).length)
        val id = TransactionBuckets.bucketId(1)
        val replacement = (100L downTo 1L).map { record(it) }.filter { TransactionBuckets.bucketId(it.id) == id }
        val bucket = TransactionBucketDto(id, Base64.getEncoder().encodeToString(TransactionBuckets.hash(1, id, replacement)), replacement)
        val response = TransactionAccountSyncResponseDto(1, 100, Base64.getEncoder().encodeToString(ByteArray(32)), "buckets", buckets = listOf(bucket))
        val completed = TransactionBuckets.replace(patched, response)
        assertFalse(completed.partial)
        assertEquals((100L downTo 1L).toList(), completed.data.map { it.id })
    }

    private fun page(records: List<TransactionRecordDto>): TransactionsResponseDto = TransactionsResponseDto(true, records.map { item ->
        TransactionDto(item.id, item.uuid, item.dateIso, item.dateIso, item.description, item.number,
            item.splits.map { SplitDto(it.id, null, it.amountNumber, it.fromId, null, it.toId, null, it.memo) })
    }, records.size)
}
