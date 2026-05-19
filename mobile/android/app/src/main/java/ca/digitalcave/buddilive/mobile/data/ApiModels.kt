package ca.digitalcave.buddilive.mobile.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
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

data class TransactionDescriptionsResponseDto(
	val success: Boolean,
	val data: List<TransactionDescriptionItemDto>
)

data class TransactionDescriptionItemDto(
	val value: String?,
	val transaction: TransactionDescriptionTransactionDto?
)

data class TransactionDescriptionTransactionDto(
	val description: String?,
	val splits: List<TransactionDescriptionSplitDto>?
)

data class TransactionDescriptionSplitDto(
	val amountNumber: BigDecimal?,
	val fromId: Int,
	val toId: Int,
	val fromType: String?,
	val toType: String?
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
	val uuid: String?,
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
	val success: Boolean,
	val id: Long? = null,
	val uuid: String? = null
)

data class UserPreferencesResponseDto(
	val success: Boolean,
	val locale: String?,
	val currency: String?,
	val dateFormat: String?,
	val currencyAfter: Boolean?,
	val decimalSeparator: String?,
	val thousandSeparator: String?,
	val negativeFormat: String?,
	val showCurrencySymbol: Boolean?,
	val currencySpacing: Boolean?
)

data class TransactionMutationRequestDto(
	val action: String,
	val id: Long? = null,
	val uuid: String? = null,
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
	val deleted: Boolean,
	val balanceNumber: BigDecimal? = null,
	val debit: Boolean = true,
	val hasPending: Boolean = false
)

data class AccountTypeSummary(
	val name: String,
	val balance: String,
	val accounts: List<AccountSummary>,
	val balanceNumber: BigDecimal? = null,
	val debit: Boolean = true,
	val hasPending: Boolean = false
)

data class NetWorthSummary(
	val label: String,
	val balance: String,
	val balanceNumber: BigDecimal? = null,
	val hasPending: Boolean = false
)

data class AccountsOverview(
	val accountTypes: List<AccountTypeSummary>,
	val netWorth: NetWorthSummary?
)

data class SourceOption(
	val id: Int?,
	val label: String,
	val type: String?
) {
	val selectable: Boolean
		get() = id != null
}

data class TransactionDescriptionTemplateSplit(
	val amountNumber: BigDecimal,
	val fromId: Int,
	val toId: Int,
	val fromType: String?,
	val toType: String?
)

data class TransactionDescriptionTemplate(
	val description: String,
	val splits: List<TransactionDescriptionTemplateSplit>
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

enum class PendingTransactionStatus {
	PENDING,
	SYNCING,
	FAILED
}

data class PendingTransaction(
	val localUuid: String,
	val status: PendingTransactionStatus,
	val dateIso: String,
	val description: String,
	val number: String,
	val amount: String,
	val fromId: Int,
	val toId: Int,
	val memo: String,
	val createdAt: Long,
	val modifiedAt: Long,
	val lastError: String?,
	val attemptCount: Int
)

data class TransactionSummary(
	val id: Long,
	val uuid: String?,
	val dateIso: String,
	val description: String,
	val number: String,
	val split: TransactionSplitSummary?,
	val pendingStatus: PendingTransactionStatus? = null,
	val pendingError: String? = null
) {
	val isPending: Boolean
		get() = pendingStatus != null
}

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
	val localeTag: String,
	val decimalSeparator: Char,
	val thousandSeparator: Char,
	val currencyToken: String,
	val currencyAfter: Boolean,
	val negativeFormat: String,
	val currencySpacing: Boolean,
	val fractionDigits: Int
)

fun AccountsResponseDto.toAccountSummaries(): List<AccountSummary> {
	val accounts = mutableListOf<AccountSummary>()
	for (child in children) {
		collectAccountNodes(child, accounts)
	}
	return accounts.sortedBy { it.name.lowercase() }
}

fun AccountsResponseDto.toAccountsOverview(): AccountsOverview {
	val accountTypes = mutableListOf<AccountTypeSummary>()
	var netWorth: NetWorthSummary? = null

	for (child in children) {
		val nodeType = child.getAsJsonPrimitive("nodeType")?.asString
		if (nodeType == "type") {
			val typeName = child.getAsJsonPrimitive("name")?.asString?.trim().orEmpty()
			if (typeName.isBlank()) {
				continue
			}
			val typeBalance = child.getAsJsonPrimitive("balance")?.asString ?: ""
			val typeBalanceNumber = child.getBigDecimalOrNull("balanceNumber")
			val typeDebit = child.getAsJsonPrimitive("debit")?.asBoolean ?: true
			val accounts = mutableListOf<AccountSummary>()
			val typeChildren = child.getAsJsonArray("children")
			if (typeChildren != null) {
				for (typeChild in typeChildren) {
					if (typeChild.isJsonObject) {
						collectAccountNodes(typeChild.asJsonObject, accounts)
					}
				}
			}
			val sortedAccounts = accounts.sortedBy { it.name.lowercase() }
			if (sortedAccounts.isNotEmpty()) {
				accountTypes.add(
					AccountTypeSummary(
						name = typeName,
						balance = typeBalance,
						accounts = sortedAccounts,
						balanceNumber = typeBalanceNumber,
						debit = typeDebit
					)
				)
			}
			continue
		}

		val netWorthLabel = child.getAsJsonPrimitive("name")?.asString?.trim().orEmpty()
		val netWorthBalance = child.getAsJsonPrimitive("balance")?.asString ?: ""
		val netWorthBalanceNumber = child.getBigDecimalOrNull("balanceNumber")
		if (netWorthLabel.isNotBlank() && netWorthBalance.isNotBlank()) {
			netWorth = NetWorthSummary(
				label = netWorthLabel,
				balance = netWorthBalance,
				balanceNumber = netWorthBalanceNumber
			)
		}
	}

	return AccountsOverview(
		accountTypes = accountTypes,
		netWorth = netWorth
	)
}

private fun collectAccountNodes(node: JsonObject, output: MutableList<AccountSummary>) {
	val nodeType = node.getAsJsonPrimitive("nodeType")?.asString
	if (nodeType == "account") {
		val id = node.getAsJsonPrimitive("id")?.asLong ?: return
		val name = node.getAsJsonPrimitive("name")?.asString ?: return
		val balance = node.getAsJsonPrimitive("balance")?.asString ?: ""
		val balanceNumber = node.getBigDecimalOrNull("balanceNumber")
		val debit = node.getAsJsonPrimitive("debit")?.asBoolean ?: true
		val deleted = node.getAsJsonPrimitive("deleted")?.asBoolean ?: false
		output.add(AccountSummary(id, name, balance, deleted, balanceNumber, debit))
	}

	val children = node.getAsJsonArray("children") ?: return
	for (child in children) {
		if (child.isJsonObject) {
			collectAccountNodes(child.asJsonObject, output)
		}
	}
}

private fun JsonObject.getBigDecimalOrNull(memberName: String): BigDecimal? {
	val element = get(memberName) ?: return null
	if (!element.isJsonPrimitive) {
		return null
	}
	return runCatching { element.asBigDecimal }.getOrNull()
}

fun SourcesResponseDto.toSourceOptions(): List<SourceOption> {
	return data.mapNotNull { item ->
		val value = item.value ?: return@mapNotNull null
		if (!value.isJsonPrimitive) {
			return@mapNotNull null
		}
		val primitive = value.asJsonPrimitive
		SourceOption(
			id = if (primitive.isNumber) primitive.asInt else null,
			label = item.text.replace('\u00a0', ' '),
			type = item.type
		)
	}
}

fun TransactionDescriptionsResponseDto.toTransactionDescriptionTemplates(): List<TransactionDescriptionTemplate> {
	return data.mapNotNull { item ->
		val transaction = item.transaction ?: return@mapNotNull null
		val description = item.value?.trim()
			?.takeIf { it.isNotEmpty() }
			?: transaction.description?.trim()?.takeIf { it.isNotEmpty() }
			?: return@mapNotNull null

		val splits = transaction.splits
			?.mapNotNull { split ->
				val amountNumber = split.amountNumber
				if (amountNumber == null) {
					null
				}
				else {
					TransactionDescriptionTemplateSplit(
						amountNumber = amountNumber,
						fromId = split.fromId,
						toId = split.toId,
						fromType = split.fromType,
						toType = split.toType
					)
				}
			}
			.orEmpty()
		if (splits.isEmpty()) {
			return@mapNotNull null
		}
		TransactionDescriptionTemplate(
			description = description,
			splits = splits
		)
	}
}

fun TransactionsResponseDto.toTransactionsPage(): TransactionsPage {
	return TransactionsPage(
		items = data.map { transaction ->
			val split = transaction.splits.firstOrNull()
			TransactionSummary(
				id = transaction.id,
				uuid = transaction.uuid,
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
	val currencyCode = currency?.trim().orEmpty()
	val showCurrencySymbol = showCurrencySymbol ?: false
	val resolvedDecimalSeparator = resolveDecimalSeparator(locale, decimalSeparator)
	val resolvedThousandSeparator = resolveThousandSeparator(locale, thousandSeparator, resolvedDecimalSeparator)
	val requestedDateFormat = dateFormat?.trim().orEmpty()
	val resolvedDateFormat = when {
		requestedDateFormat.isEmpty() -> resolveDateFormatFromLocale(locale)
		isValidDateFormat(requestedDateFormat) -> requestedDateFormat
		else -> resolveDateFormatFromLocale(locale)
	}
	return UserDatePreferences(
		dateFormat = resolvedDateFormat,
		localeTag = locale.toLanguageTag(),
		decimalSeparator = resolvedDecimalSeparator,
		thousandSeparator = resolvedThousandSeparator,
		currencyToken = resolveCurrencyToken(locale, currencyCode, showCurrencySymbol),
		currencyAfter = currencyAfter ?: resolveCurrencyAfter(locale, currencyCode),
		negativeFormat = if (negativeFormat == "B") "B" else "N",
		currencySpacing = currencySpacing ?: !showCurrencySymbol,
		fractionDigits = resolveCurrencyFractionDigits(currencyCode)
	)
}

fun defaultUserDatePreferences(): UserDatePreferences {
	return UserPreferencesResponseDto(
		success = true,
		locale = null,
		currency = null,
		dateFormat = null,
		currencyAfter = null,
		decimalSeparator = null,
		thousandSeparator = null,
		negativeFormat = null,
		showCurrencySymbol = null,
		currencySpacing = null
	).toUserDatePreferences()
}

fun formatCurrencyAmount(amount: BigDecimal, preferences: UserDatePreferences): String {
	val locale = toLocale(preferences.localeTag)
	val decimalFormat = (NumberFormat.getNumberInstance(locale) as? DecimalFormat) ?: DecimalFormat()
	val symbols = decimalFormat.decimalFormatSymbols
	symbols.decimalSeparator = preferences.decimalSeparator
	symbols.groupingSeparator = preferences.thousandSeparator
	decimalFormat.decimalFormatSymbols = symbols
	decimalFormat.maximumFractionDigits = preferences.fractionDigits
	decimalFormat.minimumFractionDigits = preferences.fractionDigits
	decimalFormat.isGroupingUsed = true

	val isNegative = amount.signum() < 0
	val number = decimalFormat.format(amount.abs())
	val signedNumber = when {
		!isNegative -> number
		preferences.negativeFormat == "B" -> "($number)"
		else -> "-$number"
	}
	val currencyToken = preferences.currencyToken.trim()
	if (currencyToken.isBlank()) {
		return signedNumber
	}
	val separator = if (preferences.currencySpacing) " " else ""
	return if (preferences.currencyAfter) {
		"$signedNumber$separator$currencyToken"
	}
	else {
		"$currencyToken$separator$signedNumber"
	}
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

private fun resolveDecimalSeparator(locale: Locale, requestedSeparator: String?): Char {
	val overrideSeparator = requestedSeparator?.firstOrNull()
	if (overrideSeparator != null) {
		return overrideSeparator
	}
	return DecimalFormatSymbols.getInstance(locale).decimalSeparator
}

private fun resolveThousandSeparator(locale: Locale, requestedSeparator: String?, decimalSeparator: Char): Char {
	val overrideSeparator = requestedSeparator?.firstOrNull()
	if (overrideSeparator != null && overrideSeparator != decimalSeparator) {
		return overrideSeparator
	}
	val localeSeparator = DecimalFormatSymbols.getInstance(locale).groupingSeparator
	return if (localeSeparator == decimalSeparator) ',' else localeSeparator
}

private fun resolveCurrencyToken(locale: Locale, currencyCode: String, showCurrencySymbol: Boolean): String {
	if (currencyCode.isBlank()) {
		return ""
	}
	if (!showCurrencySymbol) {
		return currencyCode
	}
	val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull() ?: return currencyCode
	var bestSymbol: String? = null
	for (candidateLocale in Locale.getAvailableLocales()) {
		val candidateCurrency = runCatching { Currency.getInstance(candidateLocale) }.getOrNull()
		if (candidateCurrency != currency) {
			continue
		}
		val symbol = currency.getSymbol(candidateLocale)
		if (symbol.isNotBlank() && symbol != currencyCode) {
			if (bestSymbol == null || symbol.length < bestSymbol.length) {
				bestSymbol = symbol
				if (bestSymbol.length == 1) {
					break
				}
			}
		}
	}
	if (bestSymbol != null) {
		return bestSymbol
	}
	val localeSymbol = currency.getSymbol(locale)
	if (localeSymbol.isNotBlank() && localeSymbol != currencyCode) {
		return localeSymbol
	}
	val fallbackSymbol = currency.symbol
	if (fallbackSymbol.isNotBlank() && fallbackSymbol != currencyCode) {
		return fallbackSymbol
	}
	return currencyCode
}

private fun resolveCurrencyAfter(locale: Locale, currencyCode: String): Boolean {
	if (currencyCode.isBlank()) {
		return false
	}
	val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull() ?: return false
	val format = NumberFormat.getCurrencyInstance(locale) as? DecimalFormat ?: return false
	format.currency = currency
	val pattern = format.toPattern()
	val currencyPosition = pattern.indexOf('\u00a4')
	val numberPosition = pattern.indexOf('#')
	if (currencyPosition < 0 || numberPosition < 0) {
		return false
	}
	return currencyPosition > numberPosition
}

private fun resolveCurrencyFractionDigits(currencyCode: String): Int {
	if (currencyCode.isBlank()) {
		return 2
	}
	val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull() ?: return 2
	return maxOf(0, currency.defaultFractionDigits)
}
