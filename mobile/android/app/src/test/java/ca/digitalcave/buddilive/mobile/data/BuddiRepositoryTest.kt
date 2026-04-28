package ca.digitalcave.buddilive.mobile.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal

class BuddiRepositoryTest {

	@Test
	fun createTransactionUpdatesExistingDescriptionTemplateAmount() = runBlocking {
		val description = "Coffee"
		val api = FakeBuddiApi(descriptionsResponse = descriptionsResponse(description, "10.00"))
		val repository = BuddiRepository(api = api, descriptionsApi = api)

		val initial = repository.fetchTransactionDescriptionTemplates().getOrThrow()
		assertEquals(BigDecimal("10.00"), initial.single().splits.single().amountNumber)

		val result = repository.createTransaction(
			TransactionEditInput(
				dateIso = "2026-04-23",
				description = description,
				number = "",
				amount = "25.75",
				fromId = 1,
				toId = 2,
				memo = ""
			)
		)

		assertTrue(result.isSuccess)
		assertEquals(
			BigDecimal("25.75"),
			repository.transactionDescriptionTemplates.value.single().splits.single().amountNumber
		)
	}

	@Test
	fun fetchTransactionDescriptionTemplatesKeepsNewerLocalTemplateWhenRemoteLoadFinishesLater() = runBlocking {
		val description = "Coffee"
		val repository = BuddiRepository(
			api = FakeBuddiApi(),
			descriptionsApi = FakeBuddiApi(descriptionsResponse = descriptionsResponse(description, "10.00"))
		)

		val mutationResult = repository.createTransaction(
			TransactionEditInput(
				dateIso = "2026-04-23",
				description = description,
				number = "",
				amount = "25.75",
				fromId = 1,
				toId = 2,
				memo = ""
			)
		)

		assertTrue(mutationResult.isSuccess)

		val merged = repository.fetchTransactionDescriptionTemplates().getOrThrow()
		assertEquals(BigDecimal("25.75"), merged.single().splits.single().amountNumber)
	}
}

private fun descriptionsResponse(description: String, amount: String): TransactionDescriptionsResponseDto {
	return TransactionDescriptionsResponseDto(
		success = true,
		data = listOf(
			TransactionDescriptionItemDto(
				value = description,
				transaction = TransactionDescriptionTransactionDto(
					description = description,
					splits = listOf(
						TransactionDescriptionSplitDto(
							amountNumber = BigDecimal(amount),
							fromId = 1,
							toId = 2,
							fromType = "D",
							toType = "E"
						)
					)
				)
			)
		)
	)
}

private class FakeBuddiApi(
	private val descriptionsResponse: TransactionDescriptionsResponseDto = TransactionDescriptionsResponseDto(
		success = true,
		data = emptyList()
	),
	private val mutateResponse: SuccessResponseDto = SuccessResponseDto(success = true)
) : BuddiApi {

	override suspend fun login(identifier: String, password: String, remember: String?): AuthenticationFlowResponseDto {
		error("Unused in test.")
	}

	override suspend fun logout(): Response<Void> {
		error("Unused in test.")
	}

	override suspend fun getAccounts(): AccountsResponseDto {
		error("Unused in test.")
	}

	override suspend fun getTransactions(
		sourceId: Long,
		start: Int,
		limit: Int,
		search: String?
	): TransactionsResponseDto {
		error("Unused in test.")
	}

	override suspend fun getTransactionDescriptions(): TransactionDescriptionsResponseDto {
		return descriptionsResponse
	}

	override suspend fun mutateTransaction(request: TransactionMutationRequestDto): SuccessResponseDto {
		return mutateResponse
	}

	override suspend fun getUserPreferences(): UserPreferencesResponseDto {
		error("Unused in test.")
	}

	override suspend fun getSources(direction: String): SourcesResponseDto {
		error("Unused in test.")
	}
}
