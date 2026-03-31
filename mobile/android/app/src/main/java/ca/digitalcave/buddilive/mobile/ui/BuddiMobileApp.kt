package ca.digitalcave.buddilive.mobile.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.digitalcave.buddilive.mobile.BuildConfig
import ca.digitalcave.buddilive.mobile.data.AccountSummary
import ca.digitalcave.buddilive.mobile.data.BuddiRepository
import ca.digitalcave.buddilive.mobile.data.LoginResult
import ca.digitalcave.buddilive.mobile.data.SourceOption
import ca.digitalcave.buddilive.mobile.data.TransactionDescriptionTemplate
import ca.digitalcave.buddilive.mobile.data.TransactionDescriptionTemplateSplit
import ca.digitalcave.buddilive.mobile.data.TransactionEditInput
import ca.digitalcave.buddilive.mobile.data.TransactionSummary
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlinx.coroutines.delay
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
	var selectedAccountBalance by rememberSaveable { mutableStateOf("") }

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
						selectedAccountBalance = ""
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
						selectedAccountBalance = account.balance
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
					accountBalance = selectedAccountBalance,
					onBack = {
						selectedAccountId = null
						selectedAccountName = ""
						selectedAccountBalance = ""
					},
					onNeedsLogin = {
						isAuthenticated = false
						selectedAccountId = null
						selectedAccountName = ""
						selectedAccountBalance = ""
					}
				)
			}
		}
	}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun LoginScreen(
	modifier: Modifier,
	repository: BuddiRepository,
	onLoginSuccess: () -> Unit,
	onNextStep: (String) -> Unit
) {
	val viewModel: LoginViewModel = viewModel(factory = LoginViewModelFactory(repository))
	val state by viewModel.state.collectAsStateWithLifecycle()
	val autofill = LocalAutofill.current
	val autofillTree = LocalAutofillTree.current
	val identifierAutofillNode = remember {
		AutofillNode(
			autofillTypes = listOf(AutofillType.Username, AutofillType.EmailAddress),
			onFill = viewModel::onIdentifierChanged
		)
	}
	val passwordAutofillNode = remember {
		AutofillNode(
			autofillTypes = listOf(AutofillType.Password),
			onFill = viewModel::onPasswordChanged
		)
	}

	DisposableEffect(identifierAutofillNode, autofillTree) {
		autofillTree += identifierAutofillNode
		onDispose {}
	}
	DisposableEffect(passwordAutofillNode, autofillTree) {
		autofillTree += passwordAutofillNode
		onDispose {}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(24.dp),
		verticalArrangement = Arrangement.Center
	) {
		Text(text = "BuddiLive Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
		Spacer(modifier = Modifier.height(16.dp))

		OutlinedTextField(
			modifier = Modifier
				.fillMaxWidth()
				.onGloballyPositioned { coordinates ->
					identifierAutofillNode.boundingBox = coordinates.boundsInWindow()
				}
				.onFocusChanged { focusState ->
					if (focusState.isFocused) {
						autofill?.requestAutofillForNode(identifierAutofillNode)
					}
					else {
						autofill?.cancelAutofillForNode(identifierAutofillNode)
					}
				},
			value = state.identifier,
			onValueChange = viewModel::onIdentifierChanged,
			label = { Text("Email / Username") },
			keyboardOptions = KeyboardOptions(
				keyboardType = KeyboardType.Email,
				autoCorrectEnabled = false,
				imeAction = ImeAction.Next
			),
			singleLine = true
		)
		Spacer(modifier = Modifier.height(8.dp))
		OutlinedTextField(
			modifier = Modifier
				.fillMaxWidth()
				.onGloballyPositioned { coordinates ->
					passwordAutofillNode.boundingBox = coordinates.boundsInWindow()
				}
				.onFocusChanged { focusState ->
					if (focusState.isFocused) {
						autofill?.requestAutofillForNode(passwordAutofillNode)
					}
					else {
						autofill?.cancelAutofillForNode(passwordAutofillNode)
					}
				},
			value = state.password,
			onValueChange = viewModel::onPasswordChanged,
			label = { Text("Password") },
			visualTransformation = PasswordVisualTransformation(),
			keyboardOptions = KeyboardOptions(
				keyboardType = KeyboardType.Password,
				autoCorrectEnabled = false,
				imeAction = ImeAction.Done
			),
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
	var expandedTypes by rememberSaveable { mutableStateOf(setOf<String>()) }

	LaunchedEffect(Unit) {
		viewModel.refresh()
	}

	LaunchedEffect(state.overview.accountTypes) {
		expandedTypes = state.overview.accountTypes.map { it.name }.toSet()
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
			if (state.isLoading && state.overview.accountTypes.isEmpty()) {
				CircularProgressIndicator()
			}

			state.error?.let {
				Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
			}

			LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				for (accountType in state.overview.accountTypes) {
					val typeName = accountType.name
					val isExpanded = expandedTypes.contains(typeName)
					item(key = "type-$typeName") {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clickable {
									expandedTypes = if (isExpanded) {
										expandedTypes - typeName
									}
									else {
										expandedTypes + typeName
									}
								}
								.padding(12.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							Row(
								modifier = Modifier.weight(1f),
								verticalAlignment = Alignment.CenterVertically
							) {
								Icon(
									imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
									contentDescription = null
								)
								Text(
									text = accountType.name,
									style = MaterialTheme.typography.titleMedium,
									fontWeight = FontWeight.Bold
								)
							}
							Text(
								text = accountType.balance,
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								textAlign = TextAlign.End
							)
						}
					}
					if (isExpanded) {
						items(accountType.accounts, key = { it.id }) { account ->
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.clickable { onAccountSelected(account) }
									.padding(start = 36.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
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
				state.overview.netWorth?.let { netWorth ->
					item(key = "net-worth") {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(12.dp),
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Text(
								text = netWorth.label,
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.weight(1f)
							)
							Text(
								text = netWorth.balance,
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								textAlign = TextAlign.End
							)
						}
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
	accountBalance: String,
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
			selectedAccountId = accountId,
			descriptionTemplates = state.descriptionTemplates,
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
						title = { Text("Transactions") },
						navigationIcon = {
							IconButton(onClick = onBack) {
								Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
				},
				bottomBar = {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(start = 24.dp, top = 8.dp, end = 72.dp, bottom = 8.dp)
					) {
						Text(
							text = "$accountName:",
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							modifier = Modifier.weight(1f)
						)
						Text(
							text = state.accountBalance.ifBlank { accountBalance },
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							textAlign = TextAlign.End
						)
					}
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
						modifier = Modifier
							.weight(1f, fill = true)
							.testTag("transactionsList"),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						items(state.transactions, key = { it.id }) { transaction ->
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(start = 12.dp, top = 12.dp, end = 0.dp, bottom = 12.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(2.dp)
								) {
									Row(modifier = Modifier.fillMaxWidth()) {
										Text(
											text = transaction.description,
											style = MaterialTheme.typography.titleMedium,
											modifier = Modifier.weight(1f)
										)
										Text(
											text = transaction.split?.amountLabel.orEmpty(),
											style = MaterialTheme.typography.titleMedium,
											textAlign = TextAlign.End
										)
									}
									Row(modifier = Modifier.fillMaxWidth()) {
										Text(
											text = transaction.dateIso,
											style = MaterialTheme.typography.bodySmall,
											modifier = Modifier.weight(1f)
										)
										Text(
											text = transaction.split?.let { "${it.fromName} -> ${it.toName}" }.orEmpty(),
											style = MaterialTheme.typography.bodySmall,
											textAlign = TextAlign.End
										)
									}
								}
								Box(
									modifier = Modifier.padding(start = 12.dp),
									contentAlignment = Alignment.Center
								) {
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
	val dateIso: TextFieldValue,
	val description: TextFieldValue,
	val number: TextFieldValue,
	val amount: TextFieldValue,
	val fromId: Int?,
	val toId: Int?,
	val memo: TextFieldValue
)

private fun toTextFieldValue(text: String): TextFieldValue {
	return TextFieldValue(text = text, selection = TextRange(text.length))
}

@Composable
private fun DoubleTapSelectAllOutlinedTextField(
	modifier: Modifier = Modifier,
	value: TextFieldValue,
	onValueChange: (TextFieldValue) -> Unit,
	label: @Composable (() -> Unit)? = null,
	singleLine: Boolean = false,
	isError: Boolean = false,
	supportingText: @Composable (() -> Unit)? = null,
	trailingIcon: @Composable (() -> Unit)? = null
) {
	val interactionSource = remember { MutableInteractionSource() }
	val latestValue by rememberUpdatedState(value)
	val latestOnValueChange by rememberUpdatedState(onValueChange)
	val doubleTapTimeoutMillis = remember { ViewConfiguration.getDoubleTapTimeout().toLong() }
	var lastTapTimestamp by remember { mutableStateOf(0L) }

	LaunchedEffect(interactionSource) {
		interactionSource.interactions.collect { interaction ->
			if (interaction !is PressInteraction.Release) {
				return@collect
			}
			val now = SystemClock.uptimeMillis()
			if (now - lastTapTimestamp <= doubleTapTimeoutMillis && latestValue.text.isNotEmpty()) {
				latestOnValueChange(latestValue.copy(selection = TextRange(0, latestValue.text.length)))
			}
			lastTapTimestamp = now
		}
	}

	OutlinedTextField(
		modifier = modifier,
		value = value,
		onValueChange = onValueChange,
		label = label,
		singleLine = singleLine,
		isError = isError,
		supportingText = supportingText,
		trailingIcon = trailingIcon,
		interactionSource = interactionSource
	)
}

private fun selectTemplateSplit(
	template: TransactionDescriptionTemplate,
	selectedAccountId: Int
): TransactionDescriptionTemplateSplit? {
	return template.splits.firstOrNull { it.fromId == selectedAccountId || it.toId == selectedAccountId }
		?: template.splits.firstOrNull()
}

private fun resolveTemplateSourceIds(
	currentState: TransactionFormState,
	templateSplit: TransactionDescriptionTemplateSplit,
	selectedAccountId: Int
): Pair<Int, Int> {
	val currentFromMatches = currentState.fromId == selectedAccountId
	val currentToMatches = currentState.toId == selectedAccountId

	if (currentFromMatches) {
		val toId = when {
			templateSplit.fromId == selectedAccountId -> templateSplit.toId
			templateSplit.toId == selectedAccountId -> templateSplit.fromId
			else -> templateSplit.toId
		}
		return selectedAccountId to toId
	}

	if (currentToMatches) {
		val fromId = when {
			templateSplit.toId == selectedAccountId -> templateSplit.fromId
			templateSplit.fromId == selectedAccountId -> templateSplit.toId
			else -> templateSplit.fromId
		}
		return fromId to selectedAccountId
	}

	if (templateSplit.fromId == selectedAccountId || templateSplit.toId == selectedAccountId) {
		return templateSplit.fromId to templateSplit.toId
	}

	return when {
		templateSplit.fromType == "E" || templateSplit.fromType == "I" -> templateSplit.fromId to selectedAccountId
		templateSplit.toType == "E" || templateSplit.toType == "I" -> selectedAccountId to templateSplit.toId
		templateSplit.toType == "C" -> selectedAccountId to templateSplit.toId
		else -> templateSplit.fromId to selectedAccountId
	}
}

private fun applyDescriptionTemplate(
	currentState: TransactionFormState,
	template: TransactionDescriptionTemplate,
	selectedAccountId: Int,
	amountFormat: AmountFormat
): TransactionFormState {
	val templateSplit = selectTemplateSplit(template, selectedAccountId)
		?: return currentState.copy(description = toTextFieldValue(template.description))
	val (fromId, toId) = resolveTemplateSourceIds(currentState, templateSplit, selectedAccountId)
	return currentState.copy(
		description = toTextFieldValue(template.description),
		amount = toTextFieldValue(formatAmountForInput(templateSplit.amountNumber, amountFormat)),
		fromId = fromId,
		toId = toId
	)
}

private fun firstSelectableSourceId(options: List<SourceOption>): Int? {
	return options.firstNotNullOfOrNull { option -> option.id }
}

private fun firstSelectableSourceId(options: List<SourceOption>, excludeId: Int?): Int? {
	return options.firstOrNull { option ->
		option.id != null && option.id != excludeId
	}?.id ?: firstSelectableSourceId(options)
}

private fun applySourceSelection(
	currentState: TransactionFormState,
	selectedId: Int,
	selectedAccountId: Int,
	isFromSelection: Boolean
): TransactionFormState {
	if (isFromSelection) {
		if (selectedId != selectedAccountId) {
			return currentState.copy(fromId = selectedId, toId = selectedAccountId)
		}
		val toId = if (currentState.toId == selectedAccountId) null else currentState.toId
		return currentState.copy(fromId = selectedId, toId = toId)
	}

	if (selectedId != selectedAccountId) {
		return currentState.copy(fromId = selectedAccountId, toId = selectedId)
	}
	val fromId = if (currentState.fromId == selectedAccountId) null else currentState.fromId
	return currentState.copy(fromId = fromId, toId = selectedId)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TransactionEditorScreen(
	modifier: Modifier,
	existing: TransactionSummary?,
	fromSources: List<SourceOption>,
	toSources: List<SourceOption>,
	selectedAccountId: Long,
	descriptionTemplates: List<TransactionDescriptionTemplate>,
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
	val selectedAccountIdInt = remember(selectedAccountId) { selectedAccountId.toInt() }
	var state by remember(existing, fromSources, toSources, selectedAccountIdInt, dateFormat, amountFormat) {
		val initialFromId = if (existing == null) {
			null
		}
		else {
			existing.split?.fromId
				?: fromSources.firstOrNull { it.id == selectedAccountIdInt && it.selectable }?.id
				?: firstSelectableSourceId(fromSources)
		}
		val initialToId = if (existing == null) {
			null
		}
		else {
			existing.split?.toId
				?: firstSelectableSourceId(toSources, initialFromId)
		}
		mutableStateOf(
			TransactionFormState(
				dateIso = toTextFieldValue(existing?.dateIso?.let { formatIsoDateForInput(it, inputDateFormatter) } ?: defaultDate),
				description = toTextFieldValue(existing?.description.orEmpty()),
				number = toTextFieldValue(existing?.number.orEmpty()),
				amount = toTextFieldValue(existing?.split?.amountNumber?.let { formatAmountForInput(it, amountFormat) }.orEmpty()),
				fromId = initialFromId,
				toId = initialToId,
				memo = toTextFieldValue(existing?.split?.memo.orEmpty())
			)
		)
	}
	var descriptionFieldFocused by remember(existing) { mutableStateOf(false) }
	var showDescriptionSuggestions by remember(existing) { mutableStateOf(false) }
	var showDeleteConfirmation by remember(existing) { mutableStateOf(false) }

	val filteredDescriptionTemplates = remember(descriptionTemplates, state.description.text) {
		val query = state.description.text.trim()
		if (query.isBlank()) {
			emptyList()
		}
		else {
			descriptionTemplates.filter { template ->
				template.description.contains(query, ignoreCase = true)
					&& !template.description.equals(query, ignoreCase = true)
			}
		}
	}

	LaunchedEffect(descriptionFieldFocused, state.description.text) {
		if (!descriptionFieldFocused) {
			showDescriptionSuggestions = false
			return@LaunchedEffect
		}
		showDescriptionSuggestions = false
		if (state.description.text.isBlank()) {
			return@LaunchedEffect
		}
		delay(1000)
		showDescriptionSuggestions = true
	}

	val selectedDate = parseInputDate(state.dateIso.text, inputDateFormatter)
	val parsedAmount = parseAmountInput(state.amount.text, amountFormat)
	val isDateValid = selectedDate != null
	val isDateInvalid = state.dateIso.text.isNotBlank() && !isDateValid
	val isAmountInvalid = state.amount.text.isNotBlank() && parsedAmount == null
	val isValid = isDateValid
		&& state.description.text.isNotBlank()
		&& state.amount.text.isNotBlank()
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
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
								description = state.description.text.trim(),
								number = state.number.text.trim(),
								amount = parsedAmount?.toPlainString() ?: return@IconButton,
								fromId = state.fromId ?: return@IconButton,
								toId = state.toId ?: return@IconButton,
								memo = state.memo.text.trim()
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
			DoubleTapSelectAllOutlinedTextField(
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
					IconButton(
						onClick = {
							val pickerDate = selectedDate ?: LocalDate.now()
							DatePickerDialog(
								context,
								{ _, year, month, dayOfMonth ->
									state = state.copy(
										dateIso = toTextFieldValue(LocalDate.of(year, month + 1, dayOfMonth).format(inputDateFormatter))
									)
								},
								pickerDate.year,
								pickerDate.monthValue - 1,
								pickerDate.dayOfMonth
							).show()
						}
					) {
						Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
					}
				}
			)
			Box(modifier = Modifier.fillMaxWidth()) {
				DoubleTapSelectAllOutlinedTextField(
					modifier = Modifier
						.fillMaxWidth()
						.testTag("transactionEditorDescription")
						.onFocusChanged { focusState ->
							descriptionFieldFocused = focusState.isFocused
						},
						value = state.description,
						onValueChange = { value ->
							state = state.copy(description = value)
						},
					label = { Text("Description") },
					singleLine = true
				)
				DropdownMenu(
					expanded = descriptionFieldFocused && showDescriptionSuggestions && filteredDescriptionTemplates.isNotEmpty(),
					onDismissRequest = { showDescriptionSuggestions = false },
					properties = PopupProperties(focusable = false)
				) {
					for (template in filteredDescriptionTemplates) {
						DropdownMenuItem(
							text = { Text(template.description) },
							onClick = {
								state = applyDescriptionTemplate(
									currentState = state,
									template = template,
									selectedAccountId = selectedAccountIdInt,
									amountFormat = amountFormat
								)
								showDescriptionSuggestions = false
							}
						)
					}
				}
			}
			DoubleTapSelectAllOutlinedTextField(
				modifier = Modifier
					.fillMaxWidth()
					.testTag("transactionEditorNumber"),
				value = state.number,
				onValueChange = { state = state.copy(number = it) },
				label = { Text("Number") },
				singleLine = true
			)
			DoubleTapSelectAllOutlinedTextField(
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
				modifier = Modifier.testTag("transactionEditorFrom"),
				label = "From",
				options = fromSources,
				selectedId = state.fromId,
				onSelect = { selectedId ->
					state = applySourceSelection(
						currentState = state,
						selectedId = selectedId,
						selectedAccountId = selectedAccountIdInt,
						isFromSelection = true
					)
				}
			)
			SourceSelector(
				modifier = Modifier.testTag("transactionEditorTo"),
				label = "To",
				options = toSources,
				selectedId = state.toId,
				onSelect = { selectedId ->
					state = applySourceSelection(
						currentState = state,
						selectedId = selectedId,
						selectedAccountId = selectedAccountIdInt,
						isFromSelection = false
					)
				}
			)
			DoubleTapSelectAllOutlinedTextField(
				modifier = Modifier
					.fillMaxWidth()
					.testTag("transactionEditorMemo"),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSelector(
	modifier: Modifier = Modifier,
	label: String,
	options: List<SourceOption>,
	selectedId: Int?,
	onSelect: (Int) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }
	val selected = options.firstOrNull { it.id == selectedId && it.selectable }

	ExposedDropdownMenuBox(
		modifier = modifier.fillMaxWidth(),
		expanded = expanded,
		onExpandedChange = { expanded = !expanded }
	) {
		OutlinedTextField(
			modifier = Modifier
				.menuAnchor()
				.fillMaxWidth(),
			value = selected?.label?.trimStart().orEmpty(),
			onValueChange = {},
			readOnly = true,
			label = { Text(label) },
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
		)
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			for (option in options) {
				val sourceId = option.id
				DropdownMenuItem(
					enabled = option.selectable,
					text = {
						Text(
							text = option.label,
							color = if (option.selectable) {
								MaterialTheme.colorScheme.onSurface
							}
							else {
								MaterialTheme.colorScheme.onSurfaceVariant
							}
						)
					},
					onClick = {
						if (sourceId == null) {
							return@DropdownMenuItem
						}
						onSelect(sourceId)
						expanded = false
					}
				)
			}
		}
	}
}
