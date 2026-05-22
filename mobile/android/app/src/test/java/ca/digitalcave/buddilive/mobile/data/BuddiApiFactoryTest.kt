package ca.digitalcave.buddilive.mobile.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class BuddiApiFactoryTest {

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
