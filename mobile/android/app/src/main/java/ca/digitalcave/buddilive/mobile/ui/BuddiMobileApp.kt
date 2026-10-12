package ca.digitalcave.buddilive.mobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.digitalcave.buddilive.mobile.BuildConfig
import ca.digitalcave.buddilive.mobile.NetworkMonitor
import ca.digitalcave.buddilive.mobile.data.AccountSummary
import ca.digitalcave.buddilive.mobile.data.BuddiRepository
import ca.digitalcave.buddilive.mobile.data.LoginResult
import ca.digitalcave.buddilive.mobile.data.PendingTransactionStatus
import ca.digitalcave.buddilive.mobile.data.SourceOption
import ca.digitalcave.buddilive.mobile.data.TransactionDescriptionTemplate
import ca.digitalcave.buddilive.mobile.data.TransactionDescriptionTemplateSplit
import ca.digitalcave.buddilive.mobile.data.TransactionEditInput
import ca.digitalcave.buddilive.mobile.data.TransactionSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private const val authPreferencesName = "buddilive_mobile_auth"
private const val stayLoggedInPreferenceKey = "stay_logged_in"
private const val loginIdentifierTag = "loginIdentifier"
private const val loginPasswordTag = "loginPassword"

private fun readStayLoggedInPreference(context: Context): Boolean {
	return context.getSharedPreferences(authPreferencesName, Context.MODE_PRIVATE)
		.getBoolean(stayLoggedInPreferenceKey, false)
}

private fun writeStayLoggedInPreference(context: Context, stayLoggedIn: Boolean) {
	context.getSharedPreferences(authPreferencesName, Context.MODE_PRIVATE)
		.edit()
		.putBoolean(stayLoggedInPreferenceKey, stayLoggedIn)
		.apply()
}

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

@Composable
private fun RefreshActionButton(isRefreshing: Boolean, onRefresh: () -> Unit) {
	val iconModifier = if (isRefreshing) {
		val transition = rememberInfiniteTransition(label = "refreshIconTransition")
		val rotation by transition.animateFloat(
			initialValue = 0f,
			targetValue = 360f,
			animationSpec = infiniteRepeatable(
				animation = tween(
					durationMillis = 900,
					easing = LinearEasing
				)
			),
			label = "refreshIconRotation"
		)
		Modifier.rotate(rotation)
	}
	else {
		Modifier
	}

	IconButton(onClick = onRefresh) {
		Icon(
			imageVector = Icons.Filled.Refresh,
			contentDescription = "Refresh",
			modifier = iconModifier
		)
	}
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
fun BuddiMobileApp(repository: BuddiRepository, networkMonitor: NetworkMonitor) {
	val context = LocalContext.current
	val snackbarHostState = remember { SnackbarHostState() }
	val coroutineScope = rememberCoroutineScope()
	val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()
	val syncState by repository.syncState.collectAsStateWithLifecycle()
	val lifecycleOwner = LocalLifecycleOwner.current
	var showSignIn by remember { mutableStateOf(false) }
	var legacyPendingCount by remember { mutableStateOf(0) }
	var showLegacyRestore by remember { mutableStateOf(false) }
	val cacheVersion by repository.cacheVersion.collectAsStateWithLifecycle()

	var isAuthenticated by remember { mutableStateOf(false) }
	var isAuthBootstrapInProgress by remember { mutableStateOf(true) }
	var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
	var selectedAccountName by rememberSaveable { mutableStateOf("") }
	var selectedAccountBalance by rememberSaveable { mutableStateOf("") }
	var expandedAccountTypes by rememberSaveable { mutableStateOf(setOf<String>()) }

	LaunchedEffect(repository, context) {
		isAuthenticated = readStayLoggedInPreference(context) && repository.restoreLocalSession()
		isAuthBootstrapInProgress = false
	}

	DisposableEffect(repository, lifecycleOwner, isAuthenticated) {
		fun updateForeground() {
			repository.setForeground(isAuthenticated && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
		}
		val observer = LifecycleEventObserver { _, _ -> updateForeground() }
		lifecycleOwner.lifecycle.addObserver(observer)
		updateForeground()
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
			repository.setForeground(false)
		}
	}

	LaunchedEffect(repository, isAuthenticated, isOnline) {
		if (isAuthenticated && isOnline) repository.requestSync()
	}

	LaunchedEffect(isAuthenticated, cacheVersion) {
		legacyPendingCount = if (isAuthenticated) repository.legacyPendingCount() else 0
	}

	if (showLegacyRestore) {
		AlertDialog(
			onDismissRequest = { showLegacyRestore = false },
			title = { Text("Restore saved transactions?") },
			text = { Text("This device has $legacyPendingCount transactions saved before account identification was available. Restore them only if they belong to the account you have signed into.") },
			confirmButton = {
				TextButton(onClick = {
					showLegacyRestore = false
					coroutineScope.launch {
						repository.restoreLegacyTransactions().onFailure {
							snackbarHostState.showSnackbar(it.message ?: "Unable to restore saved transactions.")
						}
					}
				}) { Text("Restore") }
			},
			dismissButton = { TextButton(onClick = { showLegacyRestore = false }) { Text("Cancel") } }
		)
	}

	Scaffold(
		snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.consumeWindowInsets(paddingValues)
		) {
			if (isAuthenticated) {
				if (syncState.needsLogin) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text("Sign in to sync", modifier = Modifier.padding(horizontal = 16.dp))
						TextButton(onClick = { showSignIn = true }) { Text("Sign in") }
					}
				} else if (!isOnline || syncState.error != null) {
					OfflineStatusBanner()
				} else if (!syncState.isPrepared) {
					Text("Downloading data for offline use…", modifier = Modifier.padding(12.dp))
				}
			}
			if (legacyPendingCount > 0 && !syncState.needsLogin) {
				TextButton(onClick = { showLegacyRestore = true }) { Text("Restore $legacyPendingCount saved transactions") }
			}
			if (showSignIn) {
				TextButton(onClick = { showSignIn = false }) { Text("Continue offline") }
			}
			Box(modifier = Modifier.weight(1f, fill = true)) {
				when {
					isAuthBootstrapInProgress -> Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center
					) {
						CircularProgressIndicator()
					}

					!isAuthenticated || showSignIn -> LoginScreen(
						modifier = Modifier,
						repository = repository,
						onLoginSuccess = { stayLoggedIn ->
							writeStayLoggedInPreference(context, stayLoggedIn)
							isAuthenticated = true
							showSignIn = false
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
						modifier = Modifier,
						repository = repository,
						expandedTypes = expandedAccountTypes,
						onExpandedTypesChange = { expandedAccountTypes = it },
						onAccountSelected = { account ->
							selectedAccountId = account.id
							selectedAccountName = account.name
							selectedAccountBalance = account.balance
						},
						onLogout = {
							writeStayLoggedInPreference(context, false)
							isAuthenticated = false
							selectedAccountId = null
							selectedAccountName = ""
							selectedAccountBalance = ""
							coroutineScope.launch {
								repository.logout()
							}
						},
						onNeedsLogin = {
							isAuthenticated = false
						}
					)

					else -> TransactionsScreen(
						modifier = Modifier,
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
	}
}

@Composable
private fun OfflineStatusBanner() {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.errorContainer)
			.padding(horizontal = 16.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.Center
	) {
		Text(
			text = "Offline",
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onErrorContainer
		)
	}
}

@Composable
private fun LoginScreen(
	modifier: Modifier,
	repository: BuddiRepository,
	onLoginSuccess: (Boolean) -> Unit,
	onNextStep: (String) -> Unit
) {
	val viewModel: LoginViewModel = viewModel(factory = LoginViewModelFactory(repository))
	val state by viewModel.state.collectAsStateWithLifecycle()

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(horizontal = 28.dp),
		verticalArrangement = Arrangement.Center
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				text = "Buddi",
				color = MaterialTheme.colorScheme.primary,
				fontSize = 36.sp,
				fontWeight = FontWeight.Bold,
				letterSpacing = (-1).sp
			)
			Text(
				text = "Live",
				color = MaterialTheme.colorScheme.onSurface,
				fontSize = 36.sp,
				fontWeight = FontWeight.Bold,
				letterSpacing = (-1).sp
			)
		}
		Text(
			text = "Your finances, simplified.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Spacer(modifier = Modifier.height(28.dp))

		OutlinedTextField(
			modifier = Modifier
				.testTag(loginIdentifierTag)
				.fillMaxWidth()
				.contentType(ContentType.Username + ContentType.EmailAddress),
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
		Spacer(modifier = Modifier.height(12.dp))
		OutlinedTextField(
			modifier = Modifier
				.testTag(loginPasswordTag)
				.fillMaxWidth()
				.contentType(ContentType.Password),
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
		Spacer(modifier = Modifier.height(12.dp))
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable { viewModel.onStayLoggedInChanged(!state.stayLoggedIn) },
			verticalAlignment = Alignment.CenterVertically
		) {
			Checkbox(
				checked = state.stayLoggedIn,
				onCheckedChange = viewModel::onStayLoggedInChanged
			)
			Text("Stay logged in")
		}
		Spacer(modifier = Modifier.height(16.dp))

		Button(
			onClick = {
				viewModel.login { result ->
					when (result) {
						is LoginResult.Success -> onLoginSuccess(state.stayLoggedIn)
						is LoginResult.NextStep -> onNextStep(result.nextStep)
						is LoginResult.Error -> Unit
					}
				}
			},
			enabled = !state.isLoading,
			shape = RoundedCornerShape(8.dp),
			modifier = Modifier
				.fillMaxWidth()
				.height(52.dp)
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
	expandedTypes: Set<String>,
	onExpandedTypesChange: (Set<String>) -> Unit,
	onAccountSelected: (AccountSummary) -> Unit,
	onLogout: () -> Unit,
	onNeedsLogin: () -> Unit
) {
	val sessionVersion by repository.sessionVersion.collectAsStateWithLifecycle()
	val syncState by repository.syncState.collectAsStateWithLifecycle()
	val viewModel: AccountsViewModel = viewModel(key = "accounts-$sessionVersion", factory = AccountsViewModelFactory(repository))
	val state by viewModel.state.collectAsStateWithLifecycle()

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
					IconButton(onClick = onLogout) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.Logout,
							contentDescription = "Log out"
						)
					}
					RefreshActionButton(
						isRefreshing = syncState.isSyncing,
						onRefresh = viewModel::refresh
					)
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
					scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
				)
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.padding(12.dp)
		) {
			state.error?.let {
				Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
			}

			LazyColumn {
				for (accountType in state.overview.accountTypes) {
					val typeName = accountType.name
					val isExpanded = expandedTypes.contains(typeName)
					item(key = "type-$typeName") {
						Column {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.clickable {
										onExpandedTypesChange(if (isExpanded) {
											expandedTypes - typeName
										}
										else {
											expandedTypes + typeName
										})
									}
									.padding(horizontal = 12.dp, vertical = 14.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Row(
									modifier = Modifier.weight(1f),
									verticalAlignment = Alignment.CenterVertically
								) {
									Icon(
										imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
										contentDescription = null,
										modifier = Modifier
											.padding(end = 8.dp)
											.size(18.dp),
										tint = MaterialTheme.colorScheme.onSurfaceVariant
									)
									Text(
										text = accountType.name,
										style = MaterialTheme.typography.titleMedium
									)
								}
								Column(horizontalAlignment = Alignment.End) {
									Text(
										text = accountType.balance,
										style = MaterialTheme.typography.titleMedium,
										color = if ((accountType.balanceNumber?.signum() ?: 0) < 0) {
											MaterialTheme.colorScheme.error
										}
										else {
											MaterialTheme.colorScheme.onSurface
										},
										textAlign = TextAlign.End
									)
									if (accountType.hasPending) {
										Text(
											text = "includes pending",
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.primary,
											textAlign = TextAlign.End
										)
									}
								}
							}
							HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
						}
					}
					if (isExpanded) {
						items(accountType.accounts, key = { it.id }) { account ->
							val deletedAccountTextDecoration = if (account.deleted) {
								TextDecoration.LineThrough
							}
							else {
								TextDecoration.None
							}
							Surface(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 4.dp, vertical = 4.dp),
								shape = RoundedCornerShape(10.dp),
								color = MaterialTheme.colorScheme.surfaceContainerLow,
								onClick = { onAccountSelected(account) }
							) {
								Row(
									modifier = Modifier.padding(start = 40.dp, top = 13.dp, end = 12.dp, bottom = 13.dp),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically
								) {
									Text(
										text = account.name,
										style = MaterialTheme.typography.titleMedium,
										textDecoration = deletedAccountTextDecoration,
										modifier = Modifier.weight(1f)
									)
									Column(horizontalAlignment = Alignment.End) {
										Text(
											text = account.balance,
											style = MaterialTheme.typography.titleMedium,
											textDecoration = deletedAccountTextDecoration,
											color = if ((account.balanceNumber?.signum() ?: 0) < 0) {
												MaterialTheme.colorScheme.error
											}
											else {
												MaterialTheme.colorScheme.onSurface
											},
											textAlign = TextAlign.End
										)
										if (account.hasPending) {
											Text(
												text = "includes pending",
												style = MaterialTheme.typography.bodySmall,
												color = MaterialTheme.colorScheme.primary,
												textAlign = TextAlign.End
											)
										}
									}
								}
							}
						}
					}
				}
				state.overview.netWorth?.let { netWorth ->
					item(key = "net-worth") {
						Surface(
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 14.dp, start = 4.dp, end = 4.dp),
							shape = RoundedCornerShape(10.dp),
							color = MaterialTheme.colorScheme.surfaceContainerLow
						) {
							Row(
								modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically
							) {
								Text(
									text = netWorth.label,
									style = MaterialTheme.typography.titleMedium,
									modifier = Modifier.weight(1f)
								)
								Column(horizontalAlignment = Alignment.End) {
									Text(
										text = netWorth.balance,
										style = MaterialTheme.typography.titleMedium,
										textAlign = TextAlign.End
									)
									if (netWorth.hasPending) {
										Text(
											text = "includes pending",
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.primary,
											textAlign = TextAlign.End
										)
									}
								}
							}
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
	val sessionVersion by repository.sessionVersion.collectAsStateWithLifecycle()
	val syncState by repository.syncState.collectAsStateWithLifecycle()
	val viewModel: TransactionsViewModel = viewModel(
		key = "transactions-$sessionVersion-$accountId",
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
	var lastTransactionDateIso by rememberSaveable(accountId) { mutableStateOf<String?>(null) }
	val transactionsListState = rememberLazyListState()

	LaunchedEffect(accountId) {
		viewModel.loadInitial()
	}

	LaunchedEffect(state.needsLogin) {
		if (state.needsLogin) {
			onNeedsLogin()
		}
	}

	LaunchedEffect(state.scrollToTopRequestKey) {
		if (state.scrollToTopRequestKey > 0L) {
			transactionsListState.scrollToItem(0)
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
			preferredNewDateIso = lastTransactionDateIso,
			isMutating = state.isMutating,
			onPreferredNewDateIsoChange = { dateIso ->
				if (editingTransaction == null) {
					lastTransactionDateIso = dateIso
				}
			},
			onBack = { showEditor = false },
			onSave = { input ->
				val existingId = editingTransaction?.id
				if (existingId == null) {
					viewModel.createTransaction(input) { success ->
						if (success) {
							lastTransactionDateIso = input.dateIso
							editingTransaction = null
							showEditor = false
						}
					}
				}
				else {
					viewModel.updateTransaction(existingId, input) { success ->
						if (success) {
							lastTransactionDateIso = input.dateIso
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
							RefreshActionButton(
								isRefreshing = syncState.isSyncing,
								onRefresh = viewModel::refresh
							)
						},
						colors = TopAppBarDefaults.topAppBarColors(
							containerColor = MaterialTheme.colorScheme.background,
							scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
						)
					)
				},
				bottomBar = {
					Surface(color = MaterialTheme.colorScheme.background) {
						Surface(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 12.dp, vertical = 8.dp),
							shape = RoundedCornerShape(10.dp),
							color = MaterialTheme.colorScheme.surfaceContainerLow
						) {
							Row(
								modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Text(
									text = "$accountName:",
									style = MaterialTheme.typography.titleMedium,
									modifier = Modifier.weight(1f)
								)
								Column(horizontalAlignment = Alignment.End) {
									Text(
										text = state.accountBalance.ifBlank { accountBalance },
										style = MaterialTheme.typography.titleMedium,
										color = MaterialTheme.colorScheme.primary,
										textAlign = TextAlign.End
									)
									if (state.accountBalanceHasPending) {
										Text(
											text = "includes pending",
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.primary,
											textAlign = TextAlign.End
										)
									}
								}
							}
						}
					}
				}
			) { paddingValues ->
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(paddingValues)
						.padding(horizontal = 12.dp)
				) {
					state.error?.let {
						Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
					}

					Row(
						modifier = Modifier
							.fillMaxWidth()
							.background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
							.padding(horizontal = 12.dp, vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = accountName.uppercase(),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.weight(1f)
						)
						Text(
							text = state.accountBalance.ifBlank { accountBalance },
							style = MaterialTheme.typography.labelLarge,
							color = MaterialTheme.colorScheme.primary,
							textAlign = TextAlign.End
						)
					}

					LazyColumn(
						modifier = Modifier
							.weight(1f, fill = true)
							.testTag("transactionsList"),
						state = transactionsListState
					) {
						items(state.transactions, key = { it.id }) { transaction ->
							val split = transaction.split
							val isInflow = split?.toId?.toLong() == accountId
							val isOutflow = split?.fromId?.toLong() == accountId
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.clickable {
										editingTransaction = transaction
										showEditor = true
									}
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
										text = split?.amountLabel.orEmpty(),
											style = MaterialTheme.typography.titleMedium,
											color = when {
												isInflow -> MaterialTheme.colorScheme.primary
												isOutflow -> MaterialTheme.colorScheme.error
												else -> MaterialTheme.colorScheme.onSurface
											},
											textAlign = TextAlign.End
										)
									}
									Row(modifier = Modifier.fillMaxWidth()) {
										Text(
											text = transaction.dateIso,
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											modifier = Modifier.weight(1f)
										)
										Text(
											text = split?.let { "${it.fromName} → ${it.toName}" }.orEmpty(),
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											textAlign = TextAlign.End
										)
									}
									transaction.pendingStatus?.let { pendingStatus ->
										val pendingText = when (pendingStatus) {
											PendingTransactionStatus.FAILED -> transaction.pendingError ?: "Sync failed"
											PendingTransactionStatus.SYNCING -> "Syncing"
											PendingTransactionStatus.PENDING -> "Pending"
										}
										Text(
											text = pendingText,
											style = MaterialTheme.typography.bodySmall,
											color = if (pendingStatus == PendingTransactionStatus.FAILED) {
												MaterialTheme.colorScheme.error
											}
											else {
												MaterialTheme.colorScheme.primary
											}
										)
									}
								}
								IconButton(
									modifier = Modifier.size(40.dp),
									onClick = {
										editingTransaction = transaction
										showEditor = true
									}
								) {
									Icon(
										imageVector = Icons.Filled.ChevronRight,
										contentDescription = "Edit",
										modifier = Modifier.size(18.dp),
										tint = MaterialTheme.colorScheme.onSurfaceVariant
									)
								}
							}
							HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

private fun isWordCharacter(character: Char): Boolean {
	return character.isLetterOrDigit() || character == '_'
}

private fun selectWordRangeAtCursor(value: TextFieldValue): TextRange? {
	val text = value.text
	if (text.isEmpty()) {
		return null
	}

	val cursor = value.selection.start.coerceIn(0, text.length)
	var index = if (cursor == text.length) {
		cursor - 1
	}
	else {
		cursor
	}

	if (index >= 0 && !isWordCharacter(text[index]) && cursor > 0 && isWordCharacter(text[cursor - 1])) {
		index = cursor - 1
	}
	if (index < 0 || !isWordCharacter(text[index])) {
		return null
	}

	var start = index
	while (start > 0 && isWordCharacter(text[start - 1])) {
		start--
	}
	var end = index + 1
	while (end < text.length && isWordCharacter(text[end])) {
		end++
	}
	return TextRange(start, end)
}

@Composable
private fun DoubleTapSelectWordOutlinedTextField(
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
			if (now - lastTapTimestamp <= doubleTapTimeoutMillis) {
				val selectedWordRange = selectWordRangeAtCursor(latestValue) ?: return@collect
				latestOnValueChange(latestValue.copy(selection = selectedWordRange))
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
	val amount = if (currentState.amount.text.isBlank()) {
		toTextFieldValue(formatAmountForInput(templateSplit.amountNumber, amountFormat))
	}
	else {
		currentState.amount
	}
	return currentState.copy(
		description = toTextFieldValue(template.description),
		amount = amount,
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
private fun TransactionEditorScreen(
	modifier: Modifier,
	existing: TransactionSummary?,
	fromSources: List<SourceOption>,
	toSources: List<SourceOption>,
	selectedAccountId: Long,
	descriptionTemplates: List<TransactionDescriptionTemplate>,
	dateFormat: String,
	amountFormat: AmountFormat,
	preferredNewDateIso: String?,
	isMutating: Boolean,
	onPreferredNewDateIsoChange: (String) -> Unit,
	onBack: () -> Unit,
	onSave: (TransactionEditInput) -> Unit,
	onDelete: (Long) -> Unit
) {
	val inputDateFormatter = remember(dateFormat, amountFormat.localeTag) {
		createInputDateFormatter(dateFormat, amountFormat.localeTag)
	}
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
				dateIso = toTextFieldValue(
					if (existing == null) {
						formatIsoDateForInput(preferredNewDateIso, inputDateFormatter)
					}
					else {
						formatIsoDateForInput(existing.dateIso, inputDateFormatter)
					}
				),
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
	var showDatePicker by remember(existing) { mutableStateOf(false) }
	var showDeleteConfirmation by remember(existing) { mutableStateOf(false) }
	val keyboardController = LocalSoftwareKeyboardController.current
	val updateDateIso: (TextFieldValue) -> Unit = { dateValue ->
		state = state.copy(dateIso = dateValue)
		if (existing == null) {
			parseInputDate(dateValue.text, inputDateFormatter)?.let { parsedDate ->
				onPreferredNewDateIsoChange(parsedDate.format(isoDateFormatter))
			}
		}
	}

	val filteredDescriptionTemplates = remember(descriptionTemplates, state.description.text, selectedAccountIdInt) {
		val query = state.description.text.trim()
		if (query.isBlank()) {
			emptyList()
		}
		else {
			descriptionTemplates.filter { template ->
				template.splits.any { split ->
					split.fromId == selectedAccountIdInt || split.toId == selectedAccountIdInt
				}
					&& template.description.contains(query, ignoreCase = true)
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
						enabled = isValid && !isMutating
					) {
						Icon(Icons.Filled.Check, contentDescription = "Save")
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
					scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
				)
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.padding(horizontal = 12.dp, vertical = 8.dp)
				.verticalScroll(scrollState),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			DoubleTapSelectWordOutlinedTextField(
				modifier = Modifier
					.fillMaxWidth()
					.testTag("transactionEditorDate"),
				value = state.dateIso,
				onValueChange = updateDateIso,
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
						onClick = { showDatePicker = true }
					) {
						Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
					}
				}
			)
			Box(modifier = Modifier.fillMaxWidth()) {
				DoubleTapSelectWordOutlinedTextField(
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
					modifier = Modifier
						.fillMaxWidth()
						.background(MaterialTheme.colorScheme.surfaceContainerLowest),
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
								keyboardController?.hide()
							}
						)
						if (template != filteredDescriptionTemplates.last()) {
							HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
						}
					}
				}
			}
			DoubleTapSelectWordOutlinedTextField(
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
			DoubleTapSelectWordOutlinedTextField(
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

	if (showDatePicker) {
		val pickerDate = selectedDate ?: LocalDate.now()
		val pickerState = rememberDatePickerState(
			initialSelectedDateMillis = pickerDate
				.atStartOfDay(ZoneOffset.UTC)
				.toInstant()
				.toEpochMilli()
		)
		DatePickerDialog(
			onDismissRequest = { showDatePicker = false },
			confirmButton = {
				TextButton(onClick = {
					pickerState.selectedDateMillis?.let { selectedDateMillis ->
						val date = Instant.ofEpochMilli(selectedDateMillis)
							.atZone(ZoneOffset.UTC)
							.toLocalDate()
						updateDateIso(toTextFieldValue(date.format(inputDateFormatter)))
					}
					showDatePicker = false
				}) {
					Text("Set")
				}
			},
			dismissButton = {
				TextButton(onClick = { showDatePicker = false }) {
					Text("Cancel")
				}
			}
		) {
			DatePicker(state = pickerState)
		}
	}

	if (showDeleteConfirmation && existing != null) {
		AlertDialog(
			onDismissRequest = { showDeleteConfirmation = false },
			icon = {
				Icon(
					imageVector = Icons.Filled.Delete,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.error
				)
			},
			title = { Text("Delete Transaction?") },
			text = { Text("This action cannot be undone.") },
			confirmButton = {
				Button(
					modifier = Modifier.testTag("transactionEditorDeleteConfirm"),
					colors = ButtonDefaults.buttonColors(
						containerColor = MaterialTheme.colorScheme.error,
						contentColor = MaterialTheme.colorScheme.onError
					),
					onClick = {
						showDeleteConfirmation = false
						onDelete(existing.id)
					}
				) {
					Text("Delete")
				}
			},
			dismissButton = {
				OutlinedButton(onClick = { showDeleteConfirmation = false }) {
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
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

	Box(
		modifier = modifier.fillMaxWidth()
	) {
		OutlinedTextField(
			modifier = Modifier.fillMaxWidth(),
			value = selected?.label?.trimStart() ?: "Select account",
			onValueChange = {},
			readOnly = true,
			label = { Text(label) },
			textStyle = MaterialTheme.typography.bodyLarge.copy(
				color = if (selected == null) {
					MaterialTheme.colorScheme.onSurfaceVariant
				} else {
					MaterialTheme.colorScheme.onSurface
				}
			),
			trailingIcon = {
				Icon(
					imageVector = Icons.Filled.ExpandMore,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		)
		Box(
			modifier = Modifier
				.matchParentSize()
				.clickable { expanded = true }
		)
	}

	if (expanded) {
		ModalBottomSheet(
			onDismissRequest = { expanded = false },
			sheetState = sheetState,
			containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.navigationBarsPadding()
			) {
				Text(
					text = "Select source",
					style = MaterialTheme.typography.titleLarge,
					modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
				)
				LazyColumn(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(max = 520.dp)
				) {
					items(options) { option ->
						val sourceId = option.id
						val optionLabel = option.label
							.replace('\u00a0', ' ')
							.trim()
							.trim('-')
							.trim()
						if (!option.selectable) {
							Text(
								text = optionLabel.uppercase(),
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 8.dp)
							)
						}
						else if (sourceId != null) {
							Surface(
								modifier = Modifier.fillMaxWidth(),
								color = if (sourceId == selectedId) {
									MaterialTheme.colorScheme.primaryContainer
								}
								else {
									MaterialTheme.colorScheme.surfaceContainerLowest
								},
								onClick = {
									onSelect(sourceId)
									expanded = false
								}
							) {
								Row(
									modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
									verticalAlignment = Alignment.CenterVertically
								) {
									Text(
										text = optionLabel,
										style = MaterialTheme.typography.bodyLarge,
										modifier = Modifier.weight(1f)
									)
								}
							}
							HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
						}
					}
				}
				TextButton(
					onClick = { expanded = false },
					modifier = Modifier
						.fillMaxWidth()
						.padding(8.dp)
				) {
					Text("Cancel")
				}
			}
		}
	}
}
