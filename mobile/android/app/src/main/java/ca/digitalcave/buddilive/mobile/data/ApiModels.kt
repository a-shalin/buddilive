package ca.digitalcave.buddilive.mobile.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

data class AuthenticationFlowResponseDto(
	val success: Boolean,
	val next: String?
)

data class AccountsResponseDto(
	val success: Boolean,
	val children: List<JsonObject>
)

data class SourcesResponseDto(
	val success: Boolean,
	val data: List<SourceItemDto>
)

data class SourceItemDto(
	val value: JsonElement?,
	val text: String,
	val style: String?,
	val type: String?
)

data class TransactionsResponseDto(
	val success: Boolean,
	val data: List<TransactionDto>,
	val total: Int
)

data class TransactionDto(
	val id: Long,
	val date: String,
	val dateIso: String,
	val description: String,
	val number: String?,
	val splits: List<SplitDto>
)

data class SplitDto(
	val id: Long?,
	val amount: String?,
	val amountNumber: BigDecimal?,
	val fromId: Int,
	val from: String?,
	val toId: Int,
	val to: String?,
	val memo: String?
)

data class SuccessResponseDto(
	val success: Boolean
)

data class UserPreferencesResponseDto(
	val success: Boolean,
	val locale: String?,
	val dateFormat: String?
)

data class TransactionMutationRequestDto(
	val action: String,
	val id: Long? = null,
	val description: String? = null,
	val number: String? = null,
	val date: String? = null,
	val splits: List<SplitMutationRequestDto>? = null
)

data class SplitMutationRequestDto(
	val amount: String,
	val fromId: Int,
	val toId: Int,
	val memo: String?
)

data class AccountSummary(
	val id: Long,
	val name: String,
	val balance: String,
	val deleted: Boolean
)

data class SourceOption(
	val id: Int,
	val label: String,
	val type: String?
)

data class TransactionSplitSummary(
	val amountLabel: String,
	val amountNumber: BigDecimal,
	val fromId: Int,
	val fromName: String,
	val toId: Int,
	val toName: String,
	val memo: String
)

data class TransactionSummary(
	val id: Long,
	val dateIso: String,
	val description: String,
	val number: String,
	val split: TransactionSplitSummary?
)

data class TransactionsPage(
	val items: List<TransactionSummary>,
	val total: Int
)

data class TransactionEditInput(
	val dateIso: String,
	val description: String,
	val number: String,
	val amount: String,
	val fromId: Int,
	val toId: Int,
	val memo: String
)

data class UserDatePreferences(
	val dateFormat: String,
	val localeTag: String
)

fun AccountsResponseDto.toAccountSummaries(): List<AccountSummary> {
	val accounts = mutableListOf<AccountSummary>()
	for (child in children) {
		collectAccountNodes(child, accounts)
	}
	return accounts.sortedBy { it.name.lowercase() }
}

private fun collectAccountNodes(node: JsonObject, output: MutableList<AccountSummary>) {
	val nodeType = node.getAsJsonPrimitive("nodeType")?.asString
	if (nodeType == "account") {
		val id = node.getAsJsonPrimitive("id")?.asLong ?: return
		val name = node.getAsJsonPrimitive("name")?.asString ?: return
		val balance = node.getAsJsonPrimitive("balance")?.asString ?: ""
		val deleted = node.getAsJsonPrimitive("deleted")?.asBoolean ?: false
		output.add(AccountSummary(id, name, balance, deleted))
	}

	val children = node.getAsJsonArray("children") ?: return
	for (child in children) {
		if (child.isJsonObject) {
			collectAccountNodes(child.asJsonObject, output)
		}
	}
}

fun SourcesResponseDto.toSourceOptions(): List<SourceOption> {
	return data.mapNotNull { item ->
		val value = item.value ?: return@mapNotNull null
		if (!value.isJsonPrimitive) {
			return@mapNotNull null
		}
		val primitive = value.asJsonPrimitive
		if (!primitive.isNumber) {
			return@mapNotNull null
		}
		SourceOption(
			id = primitive.asInt,
			label = item.text.replace('\u00a0', ' ').trim(),
			type = item.type
		)
	}
}

fun TransactionsResponseDto.toTransactionsPage(): TransactionsPage {
	return TransactionsPage(
		items = data.map { transaction ->
			val split = transaction.splits.firstOrNull()
			TransactionSummary(
				id = transaction.id,
				dateIso = transaction.dateIso,
				description = transaction.description,
				number = transaction.number.orEmpty(),
				split = split?.let {
					TransactionSplitSummary(
						amountLabel = it.amount.orEmpty(),
						amountNumber = it.amountNumber ?: BigDecimal.ZERO,
						fromId = it.fromId,
						fromName = it.from.orEmpty(),
						toId = it.toId,
						toName = it.to.orEmpty(),
						memo = it.memo.orEmpty()
					)
				}
			)
		},
		total = total
	)
}

fun UserPreferencesResponseDto.toUserDatePreferences(): UserDatePreferences {
	val locale = toLocale(locale)
	val requestedDateFormat = dateFormat?.trim().orEmpty()
	val resolvedDateFormat = when {
		requestedDateFormat.isEmpty() -> resolveDateFormatFromLocale(locale)
		isValidDateFormat(requestedDateFormat) -> requestedDateFormat
		else -> resolveDateFormatFromLocale(locale)
	}
	return UserDatePreferences(
		dateFormat = resolvedDateFormat,
		localeTag = locale.toLanguageTag()
	)
}

private fun toLocale(localeTag: String?): Locale {
	if (localeTag.isNullOrBlank()) {
		return Locale.getDefault()
	}
	val parsed = Locale.forLanguageTag(localeTag.replace('_', '-'))
	if (parsed == Locale.ROOT) {
		return Locale.getDefault()
	}
	return parsed
}

private fun resolveDateFormatFromLocale(locale: Locale): String {
	val format = DateFormat.getDateInstance(DateFormat.SHORT, locale)
	if (format is SimpleDateFormat) {
		return format.toLocalizedPattern()
	}
	return "yyyy-MM-dd"
}

private fun isValidDateFormat(format: String): Boolean {
	return runCatching { SimpleDateFormat(format) }.isSuccess
}
