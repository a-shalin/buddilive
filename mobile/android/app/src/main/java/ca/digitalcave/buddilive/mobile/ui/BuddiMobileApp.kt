package ca.digitalcave.buddilive.mobile.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.digitalcave.buddilive.mobile.BuildConfig
import ca.digitalcave.buddilive.mobile.data.AccountSummary
import ca.digitalcave.buddilive.mobile.data.BuddiRepository
import ca.digitalcave.buddilive.mobile.data.LoginResult
import ca.digitalcave.buddilive.mobile.data.SourceOption
import ca.digitalcave.buddilive.mobile.data.TransactionEditInput
import ca.digitalcave.buddilive.mobile.data.TransactionSummary
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private data class AmountFormat(
	val localeTag: String,
	val decimalSeparator: Char,
	val thousandSeparator: Char,
	val currencyToken: String,
	val currencyAfter: Boolean,
	val negativeFormat: String,
	val currencySpacing: Boolean,
	val fractionDigits: Int
)

private fun createInputDateFormatter(pattern: String, localeTag: String): DateTimeFormatter {
	val locale = parseLocaleTag(localeTag)
	val normalizedPattern = pattern.trim().ifEmpty { "yyyy-MM-dd" }
	return runCatching {
		DateTimeFormatter.ofPattern(normalizedPattern, locale)
	}.getOrElse {
		DateTimeFormatter.ofPattern("yyyy-MM-dd", locale)
	}
}

private fun parseLocaleTag(localeTag: String): Locale {
	val parsedLocale = Locale.forLanguageTag(localeTag)
	if (parsedLocale == Locale.ROOT) {
		return Locale.getDefault()
	}
	return parsedLocale
}

private fun parseInputDate(value: String, formatter: DateTimeFormatter): LocalDate? {
	val normalized = value.trim()
	if (normalized.isEmpty()) {
		return null
	}
	val parsed = runCatching { LocalDate.parse(normalized, formatter) }.getOrNull() ?: return null
	if (parsed.format(formatter) != normalized) {
		return null
	}
	return parsed
}

private fun formatIsoDateForInput(dateIso: String?, inputFormatter: DateTimeFormatter): String {
	val parsedDate = dateIso
		?.let { runCatching { LocalDate.parse(it, isoDateFormatter) }.getOrNull() }
		?: LocalDate.now()
	return parsedDate.format(inputFormatter)
}

private fun formatAmountForInput(value: BigDecimal, amountFormat: AmountFormat): String {
	val locale = parseLocaleTag(amountFormat.localeTag)
	val decimalFormat = (NumberFormat.getNumberInstance(locale) as? DecimalFormat) ?: DecimalFormat()
	val symbols = decimalFormat.decimalFormatSymbols
	symbols.decimalSeparator = amountFormat.decimalSeparator
	symbols.groupingSeparator = amountFormat.thousandSeparator
	decimalFormat.decimalFormatSymbols = symbols
	decimalFormat.maximumFractionDigits = amountFormat.fractionDigits
	decimalFormat.minimumFractionDigits = amountFormat.fractionDigits
	decimalFormat.isGroupingUsed = true
	return decimalFormat.format(value)
}

private fun parseAmountInput(value: String, amountFormat: AmountFormat): BigDecimal? {
	var normalized = value.replace('\u00a0', ' ').trim()
	if (normalized.isEmpty()) {
		return null
	}

	val token = amountFormat.currencyToken
	if (token.isNotBlank()) {
		if (normalized.startsWith(token)) {
			normalized = normalized.removePrefix(token).trim()
		}
		if (normalized.endsWith(token)) {
			normalized = normalized.removeSuffix(token).trim()
		}
	}

	var isNegative = false
	if (normalized.startsWith("(") && normalized.endsWith(")")) {
		isNegative = true
		normalized = normalized.substring(1, normalized.length - 1).trim()
	}
	if (normalized.startsWith("-")) {
		isNegative = true
		normalized = normalized.removePrefix("-").trim()
	}
	if (normalized.contains('-')) {
		return null
	}
	if (token.isNotBlank() && normalized.contains(token)) {
		return null
	}
	if (!isValidGroupedAmount(normalized, amountFormat.decimalSeparator, amountFormat.thousandSeparator)) {
		return null
	}

	val decimalIndex = normalized.indexOf(amountFormat.decimalSeparator)
	val integerPart = if (decimalIndex >= 0) normalized.substring(0, decimalIndex) else normalized
	val fractionPart = if (decimalIndex >= 0) normalized.substring(decimalIndex + 1) else ""
	val integerDigits = integerPart.replace(amountFormat.thousandSeparator.toString(), "")
	val normalizedInteger = if (integerDigits.isEmpty()) "0" else integerDigits
	val normalizedDecimal = if (fractionPart.isEmpty()) normalizedInteger else "$normalizedInteger.$fractionPart"

	val parsed = normalizedDecimal.toBigDecimalOrNull() ?: return null
	return if (isNegative) parsed.negate() else parsed
}

private fun isValidGroupedAmount(value: String, decimalSeparator: Char, thousandSeparator: Char): Boolean {
	if (value.isBlank()) {
		return false
	}
	if (decimalSeparator == thousandSeparator) {
		return false
	}
	if (value.count { it == decimalSeparator } > 1) {
		return false
	}

	val decimalIndex = value.indexOf(decimalSeparator)
	val integerPart = if (decimalIndex >= 0) value.substring(0, decimalIndex) else value
	val fractionPart = if (decimalIndex >= 0) value.substring(decimalIndex + 1) else ""
	if (decimalIndex >= 0 && fractionPart.isEmpty()) {
		return false
	}
	if (fractionPart.any { !it.isDigit() }) {
		return false
	}

	if (integerPart.isEmpty()) {
		return true
	}
	if (!integerPart.contains(thousandSeparator)) {
		return integerPart.all { it.isDigit() }
	}

	val groups = integerPart.split(thousandSeparator)
	if (groups.isEmpty() || groups.any { it.isEmpty() }) {
		return false
	}
	val first = groups.first()
	if (first.length !in 1..3 || !first.all { it.isDigit() }) {
		return false
	}
	for (index in 1 until groups.size) {
		val group = groups[index]
		if (group.length != 3 || !group.all { it.isDigit() }) {
			return false
		}
	}
	return true
}

@Composable
fun BuddiMobileApp(repository: BuddiRepository) {
	val context = LocalContext.current
	val snackbarHostState = remember { SnackbarHostState() }
	val coroutineScope = rememberCoroutineScope()

	var isAuthenticated by rememberSaveable { mutableStateOf(false) }
	var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
	var selectedAccountName by rememberSaveable { mutableStateOf("") }

	Scaffold(
		snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
	) { paddingValues ->
		when {
			!isAuthenticated -> LoginScreen(
				modifier = Modifier.padding(paddingValues),
				repository = repository,
				onLoginSuccess = {
					isAuthenticated = true
					selectedAccountId = null
					selectedAccountName = ""
				},
				onNextStep = { nextStep ->
					coroutineScope.launch {
						snackbarHostState.showSnackbar("Additional authentication is required: $nextStep")
					}
					val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.BASE_URL))
					context.startActivity(intent)
				}
			)

			selectedAccountId == null -> AccountsScreen(
				modifier = Modifier.padding(paddingValues),
				repository = repository,
				onAccountSelected = { account ->
					selectedAccountId = account.id
					selectedAccountName = account.name
				},
				onNeedsLogin = {
					isAuthenticated = false
				}
			)

			else -> TransactionsScreen(
				modifier = Modifier.padding(paddingValues),
				repository = repository,
				accountId = selectedAccountId ?: return@Scaffold,
				accountName = selectedAccountName,
				onBack = {
					selectedAccountId = null
					selectedAccountName = ""
				},
				onNeedsLogin = {
					isAuthenticated = false
					selectedAccountId = null
					selectedAccountName = ""
				}
			)
		}
	}
}

@Composable
private fun LoginScreen(
	modifier: Modifier,
	repository: BuddiRepository,
	onLoginSuccess: () -> Unit,
	onNextStep: (String) -> Unit
) {
	val viewModel: LoginViewModel = viewModel(factory = LoginViewModelFactory(repository))
	val state by viewModel.state.collectAsStateWithLifecycle()

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(24.dp),
		verticalArrangement = Arrangement.Center
	) {
		Text(text = "BuddiLive Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
		Spacer(modifier = Modifier.height(16.dp))

		OutlinedTextField(
			modifier = Modifier.fillMaxWidth(),
			value = state.identifier,
			onValueChange = viewModel::onIdentifierChanged,
			label = { Text("Email / Username") },
			singleLine = true
		)
		Spacer(modifier = Modifier.height(8.dp))
		OutlinedTextField(
			modifier = Modifier.fillMaxWidth(),
			value = state.password,
			onValueChange = viewModel::onPasswordChanged,
			label = { Text("Password") },
			visualTransformation = PasswordVisualTransformation(),
			singleLine = true
		)
		Spacer(modifier = Modifier.height(16.dp))

		Button(
			onClick = {
				viewModel.login { result ->
					when (result) {
						is LoginResult.Success -> onLoginSuccess()
						is LoginResult.NextStep -> onNextStep(result.nextStep)
						is LoginResult.Error -> Unit
					}
				}
			},
			enabled = !state.isLoading,
			modifier = Modifier.fillMaxWidth()
		) {
			if (state.isLoading) {
				CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
			}
			else {
				Text("Sign In")
			}
		}

		state.error?.let {
			Spacer(modifier = Modifier.height(12.dp))
			Text(text = it, color = MaterialTheme.colorScheme.error)
		}
	}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AccountsScreen(
	modifier: Modifier,
	repository: BuddiRepository,
	onAccountSelected: (AccountSummary) -> Unit,
	onNeedsLogin: () -> Unit
) {
	val viewModel: AccountsViewModel = viewModel(factory = AccountsViewModelFactory(repository))
	val state by viewModel.state.collectAsStateWithLifecycle()

	LaunchedEffect(Unit) {
		viewModel.refresh()
	}

	LaunchedEffect(state.needsLogin) {
		if (state.needsLogin) {
			onNeedsLogin()
		}
	}

	Scaffold(
		modifier = modifier.fillMaxSize(),
		topBar = {
				TopAppBar(
					title = { Text("Accounts") },
					actions = {
						IconButton(onClick = viewModel::refresh) {
							Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
						}
					}
				)
			}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.padding(12.dp)
		) {
			if (state.isLoading && state.accounts.isEmpty()) {
				CircularProgressIndicator()
			}

			state.error?.let {
				Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
			}

				LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					items(state.accounts, key = { it.id }) { account ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clickable { onAccountSelected(account) }
								.padding(12.dp),
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Text(
								text = account.name,
								style = MaterialTheme.typography.titleMedium,
								modifier = Modifier.weight(1f)
							)
							Text(
								text = account.balance,
								style = MaterialTheme.typography.titleMedium,
								textAlign = TextAlign.End
							)
						}
					}
				}
		}
	}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TransactionsScreen(
	modifier: Modifier,
	repository: BuddiRepository,
	accountId: Long,
	accountName: String,
	onBack: () -> Unit,
	onNeedsLogin: () -> Unit
) {
	val viewModel: TransactionsViewModel = viewModel(
		key = "transactions-$accountId",
		factory = TransactionsViewModelFactory(repository, accountId)
	)
	val state by viewModel.state.collectAsStateWithLifecycle()
	val amountFormat = remember(
		state.localeTag,
		state.decimalSeparator,
		state.thousandSeparator,
		state.currencyToken,
		state.currencyAfter,
		state.negativeFormat,
		state.currencySpacing,
		state.fractionDigits
	) {
		AmountFormat(
			localeTag = state.localeTag,
			decimalSeparator = state.decimalSeparator,
			thousandSeparator = state.thousandSeparator,
			currencyToken = state.currencyToken,
			currencyAfter = state.currencyAfter,
			negativeFormat = state.negativeFormat,
			currencySpacing = state.currencySpacing,
			fractionDigits = state.fractionDigits
		)
	}

	var editingTransaction by remember { mutableStateOf<TransactionSummary?>(null) }
	var showEditor by remember { mutableStateOf(false) }

	LaunchedEffect(accountId) {
		viewModel.loadInitial()
	}

	LaunchedEffect(state.needsLogin) {
		if (state.needsLogin) {
			onNeedsLogin()
		}
	}

	if (showEditor) {
		TransactionEditorScreen(
			modifier = modifier,
			existing = editingTransaction,
			fromSources = state.fromSources,
			toSources = state.toSources,
			dateFormat = state.dateFormat,
			amountFormat = amountFormat,
			onBack = { showEditor = false },
			onSave = { input ->
				val existingId = editingTransaction?.id
				if (existingId == null) {
					viewModel.createTransaction(input) { success ->
						if (success) {
							showEditor = false
						}
					}
				}
				else {
					viewModel.updateTransaction(existingId, input) { success ->
						if (success) {
							showEditor = false
						}
					}
				}
			},
			onDelete = { transactionId ->
				viewModel.deleteTransaction(transactionId) { success ->
					if (success) {
						editingTransaction = null
						showEditor = false
					}
				}
			}
		)
	}
	else {
		Scaffold(
			modifier = modifier.fillMaxSize(),
			topBar = {
				TopAppBar(
					title = { Text("Transactions: $accountName") },
					navigationIcon = {
						IconButton(onClick = onBack) {
							Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
						}
					},
					actions = {
						IconButton(onClick = {
							editingTransaction = null
							showEditor = true
						}) {
							Icon(Icons.Filled.Add, contentDescription = "New")
						}
						IconButton(onClick = viewModel::refresh) {
							Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
						}
					}
				)
			}
		) { paddingValues ->
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(paddingValues)
					.padding(12.dp)
			) {
				if (state.isLoading && state.transactions.isEmpty()) {
					CircularProgressIndicator()
				}

				state.error?.let {
					Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
				}

				LazyColumn(
					modifier = Modifier.weight(1f, fill = true),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					items(state.transactions, key = { it.id }) { transaction ->
						Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
							Text(text = transaction.description, style = MaterialTheme.typography.titleMedium)
							Text(text = transaction.dateIso, style = MaterialTheme.typography.bodySmall)
							transaction.split?.let { split ->
								Text(text = split.amountLabel, style = MaterialTheme.typography.bodyLarge)
								Text(text = "${split.fromName} -> ${split.toName}", style = MaterialTheme.typography.bodyMedium)
							}
							Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
								IconButton(onClick = {
									editingTransaction = transaction
									showEditor = true
								}) {
									Icon(Icons.Filled.Edit, contentDescription = "Edit")
								}
							}
						}
					}
				}

				if (state.transactions.size < state.total) {
					Button(
						onClick = viewModel::loadMore,
						modifier = Modifier.fillMaxWidth(),
						enabled = !state.isLoading
					) {
						Text("Load More")
					}
				}
			}
		}
	}
}

data class TransactionFormState(
	val dateIso: String,
	val description: String,
	val number: String,
	val amount: String,
	val fromId: Int?,
	val toId: Int?,
	val memo: String
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TransactionEditorScreen(
	modifier: Modifier,
	existing: TransactionSummary?,
	fromSources: List<SourceOption>,
	toSources: List<SourceOption>,
	dateFormat: String,
	amountFormat: AmountFormat,
	onBack: () -> Unit,
	onSave: (TransactionEditInput) -> Unit,
	onDelete: (Long) -> Unit
) {
	val context = LocalContext.current
	val inputDateFormatter = remember(dateFormat, amountFormat.localeTag) {
		createInputDateFormatter(dateFormat, amountFormat.localeTag)
	}
	val defaultDate = remember(inputDateFormatter) { LocalDate.now().format(inputDateFormatter) }
	val dateFormatExample = remember(inputDateFormatter) { LocalDate.now().format(inputDateFormatter) }
	val amountFormatExample = remember(amountFormat) { formatAmountForInput(BigDecimal("1234.56"), amountFormat) }
	val amountFieldLabel = remember(amountFormat.currencyToken) {
		if (amountFormat.currencyToken.isBlank()) "Amount" else "Amount (${amountFormat.currencyToken})"
	}
	var state by remember(existing, fromSources, toSources, dateFormat, amountFormat) {
		mutableStateOf(
			TransactionFormState(
				dateIso = existing?.dateIso?.let { formatIsoDateForInput(it, inputDateFormatter) } ?: defaultDate,
				description = existing?.description.orEmpty(),
				number = existing?.number.orEmpty(),
				amount = existing?.split?.amountNumber?.let { formatAmountForInput(it, amountFormat) }.orEmpty(),
				fromId = existing?.split?.fromId ?: fromSources.firstOrNull()?.id,
				toId = existing?.split?.toId ?: toSources.firstOrNull()?.id,
				memo = existing?.split?.memo.orEmpty()
			)
		)
	}
	var showDeleteConfirmation by remember(existing) { mutableStateOf(false) }

	val selectedDate = parseInputDate(state.dateIso, inputDateFormatter)
	val parsedAmount = parseAmountInput(state.amount, amountFormat)
	val isDateValid = selectedDate != null
	val isDateInvalid = state.dateIso.isNotBlank() && !isDateValid
	val isAmountInvalid = state.amount.isNotBlank() && parsedAmount == null
	val isValid = isDateValid
		&& state.description.isNotBlank()
		&& state.amount.isNotBlank()
		&& state.fromId != null
		&& state.toId != null
		&& state.fromId != state.toId
		&& parsedAmount != null
	val scrollState = rememberScrollState()

	Scaffold(
		modifier = modifier.fillMaxSize(),
		topBar = {
			TopAppBar(
				title = { Text(if (existing == null) "New Transaction" else "Edit Transaction") },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
					}
				},
				actions = {
					if (existing != null) {
						IconButton(
							modifier = Modifier.testTag("transactionEditorDelete"),
							onClick = { showDeleteConfirmation = true }
						) {
							Icon(
								Icons.Filled.Delete,
								contentDescription = "Delete",
								tint = MaterialTheme.colorScheme.error
							)
						}
					}
					IconButton(
						modifier = Modifier.testTag("transactionEditorSave"),
						onClick = {
							val input = TransactionEditInput(
								dateIso = selectedDate?.format(isoDateFormatter) ?: return@IconButton,
								description = state.description.trim(),
								number = state.number.trim(),
								amount = parsedAmount?.toPlainString() ?: return@IconButton,
								fromId = state.fromId ?: return@IconButton,
								toId = state.toId ?: return@IconButton,
								memo = state.memo.trim()
							)
							onSave(input)
						},
						enabled = isValid
					) {
						Icon(Icons.Filled.Check, contentDescription = "Save")
					}
				}
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.padding(12.dp)
				.verticalScroll(scrollState),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			OutlinedTextField(
				modifier = Modifier
					.fillMaxWidth()
					.testTag("transactionEditorDate"),
				value = state.dateIso,
				onValueChange = { state = state.copy(dateIso = it) },
				label = { Text("Date ($dateFormat)") },
				singleLine = true,
				isError = isDateInvalid,
				supportingText = {
					if (isDateInvalid) {
						Text("Use $dateFormat (for example $dateFormatExample).")
					}
				},
				trailingIcon = {
					TextButton(
						onClick = {
							val pickerDate = selectedDate ?: LocalDate.now()
							DatePickerDialog(
								context,
								{ _, year, month, dayOfMonth ->
									state = state.copy(
										dateIso = LocalDate.of(year, month + 1, dayOfMonth).format(inputDateFormatter)
									)
								},
								pickerDate.year,
								pickerDate.monthValue - 1,
								pickerDate.dayOfMonth
							).show()
						}
					) {
						Text("Pick")
					}
				}
			)
			OutlinedTextField(
				modifier = Modifier
					.fillMaxWidth()
					.testTag("transactionEditorDescription"),
				value = state.description,
				onValueChange = { state = state.copy(description = it) },
				label = { Text("Description") },
				singleLine = true
			)
			OutlinedTextField(
				modifier = Modifier
					.fillMaxWidth()
					.testTag("transactionEditorNumber"),
				value = state.number,
				onValueChange = { state = state.copy(number = it) },
				label = { Text("Number") },
				singleLine = true
			)
			OutlinedTextField(
				modifier = Modifier
					.fillMaxWidth()
					.testTag("transactionEditorAmount"),
				value = state.amount,
				onValueChange = { state = state.copy(amount = it) },
				label = { Text(amountFieldLabel) },
				isError = isAmountInvalid,
				supportingText = {
					if (isAmountInvalid) {
						Text("Use format $amountFormatExample.")
					}
				},
				singleLine = true
			)
			SourceSelector(
				label = "From",
				options = fromSources,
				selectedId = state.fromId,
				onSelect = { state = state.copy(fromId = it) }
			)
			SourceSelector(
				label = "To",
				options = toSources,
				selectedId = state.toId,
				onSelect = { state = state.copy(toId = it) }
			)
			OutlinedTextField(
				value = state.memo,
				onValueChange = { state = state.copy(memo = it) },
				label = { Text("Memo") },
				singleLine = false
			)
		}
	}

	if (showDeleteConfirmation && existing != null) {
		AlertDialog(
			onDismissRequest = { showDeleteConfirmation = false },
			title = { Text("Delete Transaction?") },
			text = { Text("This action cannot be undone.") },
			confirmButton = {
				TextButton(
					modifier = Modifier.testTag("transactionEditorDeleteConfirm"),
					onClick = {
						showDeleteConfirmation = false
						onDelete(existing.id)
					}
				) {
					Text("Delete", color = MaterialTheme.colorScheme.error)
				}
			},
			dismissButton = {
				TextButton(onClick = { showDeleteConfirmation = false }) {
					Text("Cancel")
				}
			}
		)
	}
}

@Composable
private fun SourceSelector(
	label: String,
	options: List<SourceOption>,
	selectedId: Int?,
	onSelect: (Int) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }
	val selected = options.firstOrNull { it.id == selectedId }

	Box {
		OutlinedTextField(
			modifier = Modifier
				.fillMaxWidth()
				.clickable { expanded = true },
			value = selected?.label.orEmpty(),
			onValueChange = {},
			readOnly = true,
			label = { Text(label) }
		)
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			for (option in options) {
				DropdownMenuItem(
					text = { Text(option.label) },
					onClick = {
						onSelect(option.id)
						expanded = false
					}
				)
			}
		}
	}
}
