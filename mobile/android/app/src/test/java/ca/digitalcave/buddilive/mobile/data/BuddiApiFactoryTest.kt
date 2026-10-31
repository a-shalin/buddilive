package ca.digitalcave.buddilive.mobile.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import com.google.gson.JsonParser
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class BuddiApiFactoryTest {

	@Test
	fun transactionSyncUsesOnePostAndPreservesRawNumbersAndNames() = runBlocking {
		val manifest = TransactionBuckets.manifest(1, TransactionsResponseDto(true, emptyList(), 0))
		val revision = java.util.Base64.getEncoder().encodeToString(ByteArray(32))
		val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		server.createContext("/data/transactions/sync") { exchange ->
			assertEquals("POST", exchange.requestMethod)
			val request = JsonParser.parseReader(exchange.requestBody.reader()).asJsonObject
			assertEquals(1, request.get("version").asInt)
			assertEquals(manifest, request.getAsJsonArray("accounts")[0].asJsonObject.get("bucketHashes").asString)
			val body = """{"success":true,"version":1,"accounts":[{"sourceId":1,"total":1,"revision":"$revision","mode":"full","items":[{"id":9007199254740993,"uuid":"uuid","dateIso":"2026-10-31","description":"Coffee","number":null,"splits":[{"id":1,"amountNumber":0.1234567890123456789,"fromId":1,"toId":2,"memo":null}]}]}],"sourceNames":{"1":"Checking","2":"Savings"}}""".toByteArray()
			exchange.responseHeaders.add("Content-Type", "application/json")
			exchange.sendResponseHeaders(200, body.size.toLong())
			exchange.responseBody.use { it.write(body) }
		}
		server.start()
		try {
			val api = BuddiApiFactory.createForTest("http://127.0.0.1:${server.address.port}/")
			val response = api.syncTransactions(TransactionsSyncRequestDto(accounts = listOf(TransactionAccountSyncRequestDto(1, manifest))))
			assertEquals(9007199254740993L, response.accounts.single().items!!.single().id)
			assertEquals("0.1234567890123456789".toBigDecimal(), response.accounts.single().items!!.single().splits.single().amountNumber)
			assertEquals("Checking", response.sourceNames[1])
		} finally {
			server.stop(0)
		}
	}

	@Test
	fun suggestionsSyncAndOptedInMutationUseCompactJson() = runBlocking {
		val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		server.createContext("/data/transactions/descriptions/sync") { exchange ->
			assertEquals("POST", exchange.requestMethod)
			val request = JsonParser.parseReader(exchange.requestBody.reader()).asJsonObject
			assertEquals(1, request.get("version").asInt)
			assertEquals("manifest", request.get("bucketHashes").asString)
			val body = """{"success":true,"version":1,"mode":"unchanged"}""".toByteArray()
			exchange.responseHeaders.add("Content-Type", "application/json")
			exchange.sendResponseHeaders(200, body.size.toLong())
			exchange.responseBody.use { it.write(body) }
		}
		server.createContext("/data/transactions") { exchange ->
			assertEquals("includeSuggestions=true&includeTransaction=true", exchange.requestURI.rawQuery)
			val request = JsonParser.parseReader(exchange.requestBody.reader()).asJsonObject
			assertEquals("Coffee", request.getAsJsonArray("suggestionKeys")[0].asString)
			val body = """{"success":true,"id":1,"transaction":{"id":1,"uuid":"uuid","dateIso":"2026-10-31","description":"Coffee","number":null,"splits":[{"id":42,"amountNumber":0.1234567890123456789,"fromId":1,"toId":2,"memo":null}]},"previousSourceIds":[],"suggestions":{"upserts":[{"description":"Coffee","splits":[{"amountNumber":0.1234567890123456789,"fromId":1,"toId":2,"fromType":"D","toType":"E"}]}],"deletes":[]}}""".toByteArray()
			exchange.responseHeaders.add("Content-Type", "application/json")
			exchange.sendResponseHeaders(200, body.size.toLong())
			exchange.responseBody.use { it.write(body) }
		}
		server.start()
		try {
			val api = BuddiApiFactory.createForTest("http://127.0.0.1:${server.address.port}/")
			assertEquals("unchanged", api.syncTransactionDescriptions(DescriptionsSyncRequestDto(bucketHashes = "manifest")).mode)
			val response = api.mutateTransaction(TransactionMutationRequestDto("insert", uuid = "uuid", suggestionKeys = listOf("Coffee")))
			assertEquals(42L, response.transaction!!.splits.single().id)
			assertEquals("uuid", response.transaction.uuid)
			assertEquals("0.1234567890123456789".toBigDecimal(), response.transaction.splits.single().amountNumber)
			assertEquals(emptyList<Int>(), response.previousSourceIds)
			assertEquals("0.1234567890123456789".toBigDecimal(), response.suggestions!!.upserts.single().splits.single().amountNumber)
		} finally {
			server.stop(0)
		}
	}

	@Test
	fun apiClientAllowsSlowTransactionRequests() = runBlocking {
		SlowHttpServer(
			delayMillis = SLOW_RESPONSE_MILLIS,
			path = "/data/transactions",
			body = """{"success":true,"data":[],"total":0}"""
		).use { server ->
			val api = BuddiApiFactory.createForTest(server.baseUrl)

			val response = api.getTransactions(sourceId = 1L, start = 0, limit = 100)

			assertTrue(response.success)
		}
	}
}

private class SlowHttpServer(
	delayMillis: Long,
	path: String,
	body: String
) : AutoCloseable {

	private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
	val baseUrl: String

	init {
		server.createContext(path) { exchange ->
			TimeUnit.MILLISECONDS.sleep(delayMillis)
			val responseBytes = body.toByteArray(StandardCharsets.UTF_8)
			exchange.responseHeaders.add("Content-Type", "application/json")
			exchange.sendResponseHeaders(200, responseBytes.size.toLong())
			exchange.responseBody.use { responseBody ->
				responseBody.write(responseBytes)
			}
		}
		server.start()
		baseUrl = "http://127.0.0.1:${server.address.port}/"
	}

	override fun close() {
		server.stop(0)
	}
}

private const val SLOW_RESPONSE_MILLIS = 10_500L
