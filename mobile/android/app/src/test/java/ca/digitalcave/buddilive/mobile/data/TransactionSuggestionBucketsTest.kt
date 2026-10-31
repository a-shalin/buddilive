package ca.digitalcave.buddilive.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class TransactionSuggestionBucketsTest {

    @Test
    fun sharedGoldenManifest() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/transaction-suggestion-buckets.json")!!.reader()).asJsonObject
        val items = Gson().fromJson(fixture.get("items"), Array<TransactionDescriptionTemplate>::class.java).toList()
        assertEquals(fixture.get("manifest").asString, TransactionSuggestionBuckets.manifest(items))
        items.forEachIndexed { index, item -> assertEquals(fixture.getAsJsonArray("bucketIds")[index].asInt, TransactionSuggestionBuckets.bucketId(item.description)) }
    }

    @Test
    fun emptyReplacementClearsOnlyItsBucketAndRejectsInvalidResponses() {
        val item = TransactionDescriptionTemplate("Coffee", listOf(TransactionDescriptionTemplateSplit("2.00".toBigDecimal(), 1, 2, "D", "E")))
        val cache = SuggestionsCache(complete = true, items = listOf(item))
        val id = TransactionSuggestionBuckets.bucketId(item.description)
        val empty = SuggestionBucketDto(id, Base64.getEncoder().encodeToString(TransactionSuggestionBuckets.hash(id, emptyList())), emptyList())
        val response = DescriptionsSyncResponseDto(true, 1, "buckets", buckets = listOf(empty))
        assertTrue(TransactionSuggestionBuckets.replace(cache, response).items.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { TransactionSuggestionBuckets.replace(cache, response.copy(buckets = listOf(empty.copy(hash = "bad")))) }
        assertThrows(IllegalArgumentException::class.java) { TransactionSuggestionBuckets.replace(cache, response.copy(buckets = listOf(empty, empty))) }
        assertThrows(IllegalArgumentException::class.java) { TransactionSuggestionBuckets.replace(cache.copy(complete = false), response) }
        assertEquals(listOf(item), cache.items)
    }
}
