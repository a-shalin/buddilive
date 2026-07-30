package ca.digitalcave.buddilive.mobile

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StandaloneBackendE2eTest {

	@get:Rule
	val composeTestRule = createAndroidComposeRule<MainActivity>()

	private val backend = BackendClient("http://10.0.2.2:8081")

	private lateinit var email: String
	private lateinit var password: String
	private lateinit var primaryAccountName: String
	private lateinit var secondaryAccountName: String
	private lateinit var reserveAccountName: String
	private lateinit var softDeleteAccountName: String
	private lateinit var transactionOneDescription: String
	private lateinit var transactionTwoDescription: String
	private lateinit var suggestionTemplatePrefix: String
	private lateinit var suggestionTemplateDescription: String
	private lateinit var nonApplicableSuggestionTemplateDescription: String
	private lateinit var transactionOneNumber: String
	private lateinit var transactionTwoNumber: String
	private lateinit var suggestionTemplateNumber: String
	private var primaryAccountId: Int = 0
	private var softDeleteAccountId: Int = 0

	@Before
	fun setUp() {
		clearMobileCookies()

		val suffix = System.currentTimeMillis()
		email = "android-e2e-$suffix@example.com"
		password = "AndroidE2ePass123!"
		primaryAccountName = "Android E2E Primary $suffix"
		secondaryAccountName = "Android E2E Secondary $suffix"
		reserveAccountName = "Android E2E Reserve $suffix"
		softDeleteAccountName = "Android E2E Soft Delete $suffix"
		transactionOneDescription = "Android E2E Groceries $suffix"
		transactionTwoDescription = "Android E2E Transfer $suffix"
		suggestionTemplatePrefix = "Android E2E Suggestion Template $suffix"
		suggestionTemplateDescription = "$suggestionTemplatePrefix Applicable"
		nonApplicableSuggestionTemplateDescription = "$suggestionTemplatePrefix Inapplicable"
		transactionOneNumber = "ANDROID-E2E-1-$suffix"
		transactionTwoNumber = "ANDROID-E2E-2-$suffix"
		suggestionTemplateNumber = "ANDROID-E2E-TEMPLATE-$suffix"

		backend.registerUser(email, password)
		val apiClient = backend.login(email, password)
		primaryAccountId = backend.createAccount(apiClient, primaryAccountName, "1500.00")
		val secondaryAccountId = backend.createAccount(apiClient, secondaryAccountName, "500.00")
		val reserveAccountId = backend.createAccount(apiClient, reserveAccountName, "700.00")
		softDeleteAccountId = backend.createAccount(apiClient, softDeleteAccountName, "250.00")
		backend.updateUserFormatting(
			client = apiClient,
			dateFormat = PREFERRED_DATE_FORMAT,
			decimalSeparator = PREFERRED_DECIMAL_SEPARATOR,
			thousandSeparator = PREFERRED_THOUSAND_SEPARATOR
		)

		backend.createTransaction(
			client = apiClient,
			description = transactionOneDescription,
			number = transactionOneNumber,
			date = "2026-03-20",
			fromId = primaryAccountId,
			toId = secondaryAccountId,
			amount = "25.75"
		)
		backend.createTransaction(
			client = apiClient,
			description = transactionTwoDescription,
			number = transactionTwoNumber,
			date = "2026-03-21",
			fromId = primaryAccountId,
			toId = reserveAccountId,
			amount = "40.00"
		)
		backend.createTransaction(
			client = apiClient,
			description = suggestionTemplateDescription,
			number = suggestionTemplateNumber,
			date = "2026-03-22",
			fromId = primaryAccountId,
			toId = secondaryAccountId,
			amount = SUGGESTION_TEMPLATE_AMOUNT
		)
		backend.createTransaction(
			client = apiClient,
			description = nonApplicableSuggestionTemplateDescription,
			number = "ANDROID-E2E-TEMPLATE-INAPPLICABLE-$suffix",
			date = "2026-03-22",
			fromId = secondaryAccountId,
			toId = reserveAccountId,
			amount = SUGGESTION_TEMPLATE_AMOUNT
		)
		backend.createTransaction(
			client = apiClient,
			description = "Android E2E Soft Delete Tx 1 $suffix",
			number = "ANDROID-E2E-SOFT-1-$suffix",
			date = "2026-03-23",
			fromId = secondaryAccountId,
			toId = softDeleteAccountId,
			amount = "10.00"
		)
		backend.createTransaction(
			client = apiClient,
			description = "Android E2E Soft Delete Tx 2 $suffix",
			number = "ANDROID-E2E-SOFT-2-$suffix",
			date = "2026-03-24",
			fromId = softDeleteAccountId,
			toId = reserveAccountId,
			amount = "5.00"
		)

		composeTestRule.activityRule.scenario.recreate()
		composeTestRule.waitForIdle()
	}

	@Test
	fun loginShowsAccountsAndTransactionsFromStandaloneBackend() {
		loginAndOpenPrimaryAccountTransactions()
		assertEventuallyVisible(transactionOneDescription)
		assertEventuallyVisible(transactionTwoDescription)
	}

	@Test
	fun stayLoggedInCheckedKeepsUserSignedInAfterActivityRecreate() {
		loginToAccountsScreen(stayLoggedIn = true)
		assertEventuallyVisible("Accounts")
		composeTestRule.activityRule.scenario.recreate()
		assertEventuallyVisible("Accounts")
	}

	@Test
	fun stayLoggedInUncheckedRequiresLoginAfterActivityRecreate() {
		loginToAccountsScreen(stayLoggedIn = false)
		assertEventuallyVisible("Accounts")
		composeTestRule.activityRule.scenario.recreate()
		waitForLoginFields()
		assertEventuallyVisible("Sign In")
	}

	@Test
	fun logoutFromAccountsReturnsToLoginAndDisablesAutoSignIn() {
		loginToAccountsScreen(stayLoggedIn = true)
		composeTestRule.onNode(hasContentDescription("Log out"), useUnmergedTree = true).performClick()
		waitForLoginFields()
		composeTestRule.activityRule.scenario.recreate()
		waitForLoginFields()
		assertEventuallyVisible("Sign In")
	}

	@Test
	fun accountTypeExpansionIsPreservedAfterReturningFromTransactions() {
		loginToAccountsScreen()

		composeTestRule.onNodeWithText(primaryAccountName).performClick()
		assertEventuallyVisible("Transactions")

		composeTestRule.onNode(hasContentDescription("Back"), useUnmergedTree = true).performClick()
		assertEventuallyVisible("Accounts")
		assertEventuallyVisible(primaryAccountName)
		assertEventuallyVisible(secondaryAccountName)
		assertEventuallyVisible(reserveAccountName)
	}

	@Test
	fun accountsRefreshHidesAndShowsDeletedAccountWhenShowDeletedDisabled() {
		val apiClient = backend.login(email, password)
		backend.updateUserFormatting(
			client = apiClient,
			dateFormat = PREFERRED_DATE_FORMAT,
			decimalSeparator = PREFERRED_DECIMAL_SEPARATOR,
			thousandSeparator = PREFERRED_THOUSAND_SEPARATOR,
			showDeleted = false
		)

		loginToAccountsScreen()
		assertEventuallyVisible(primaryAccountName)

		backend.deleteAccount(apiClient, primaryAccountId)
		tapRefreshButton()
		assertEventuallyNotVisible(primaryAccountName)

		backend.undeleteAccount(apiClient, primaryAccountId)
		tapRefreshButton()
		assertEventuallyVisible(primaryAccountName)
	}

	@Test
	fun accountsRefreshKeepsDeletedAccountVisibleWhenShowDeletedEnabled() {
		val apiClient = backend.login(email, password)
		backend.updateUserFormatting(
			client = apiClient,
			dateFormat = PREFERRED_DATE_FORMAT,
			decimalSeparator = PREFERRED_DECIMAL_SEPARATOR,
			thousandSeparator = PREFERRED_THOUSAND_SEPARATOR,
			showDeleted = true
		)

		loginToAccountsScreen()
		assertEventuallyVisible(softDeleteAccountName)
		assertEventuallyNoLineThrough(softDeleteAccountName)

		backend.deleteAccount(apiClient, softDeleteAccountId)
		tapRefreshButton()
		assertEventuallyVisible(softDeleteAccountName)
		assertEventuallyHasLineThrough(softDeleteAccountName)

		backend.undeleteAccount(apiClient, softDeleteAccountId)
		tapRefreshButton()
		assertEventuallyVisible(softDeleteAccountName)
		assertEventuallyNoLineThrough(softDeleteAccountName)
	}

	@Test
	fun transactionEditorAppliesPreferredDateFormatAndStillAllowsUpdate() {
		loginAndOpenPrimaryAccountTransactions()
		openFirstTransactionEditor()

		assertEventuallyVisible("Date ($PREFERRED_DATE_FORMAT)")

		val updatedDescription = "$transactionOneDescription Updated"
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_DESCRIPTION_TAG, useUnmergedTree = true)
			.performTextReplacement(updatedDescription)
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_SAVE_TAG, useUnmergedTree = true)
			.assertIsEnabled()
			.performClick()

		assertEventuallyVisible(updatedDescription)
		val apiClient = backend.login(email, password)
		assertTrue(
			"Updated transaction was not found in backend response.",
			backend.hasTransactionDescription(apiClient, primaryAccountId.toLong(), updatedDescription)
		)
	}

	@Test
	fun transactionEditorInvalidDateDisablesSaveAndShowsFormatHint() {
		loginAndOpenPrimaryAccountTransactions()
		openFirstTransactionEditor()

		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_SAVE_TAG, useUnmergedTree = true)
			.assertIsEnabled()
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_DATE_TAG, useUnmergedTree = true)
			.performTextReplacement("2026-03-20")
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_SAVE_TAG, useUnmergedTree = true)
			.assertIsNotEnabled()
		assertEventuallyVisibleSubstring("Use $PREFERRED_DATE_FORMAT")
	}

	@Test
	fun transactionEditorAmountUsesPreferredFormatAndValidatesSeparators() {
		loginAndOpenPrimaryAccountTransactions()
		openFirstTransactionEditor()
		val editedTransactionNumber = currentEditorNumber()
		assertTrue("Editor Number field is empty.", editedTransactionNumber.isNotBlank())
		assertTrue("Editor Amount field is empty.", currentEditorAmount().isNotBlank())

		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_AMOUNT_TAG, useUnmergedTree = true)
			.performTextReplacement(PREFERRED_INVALID_AMOUNT_INPUT)
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_SAVE_TAG, useUnmergedTree = true)
			.assertIsNotEnabled()
		assertEventuallyVisibleSubstring("Use format")

		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_AMOUNT_TAG, useUnmergedTree = true)
			.performTextReplacement(PREFERRED_VALID_AMOUNT_INPUT)
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_SAVE_TAG, useUnmergedTree = true)
			.assertIsEnabled()
			.performClick()

		assertEventuallyVisible(PREFERRED_VALID_AMOUNT_INPUT)
		val apiClient = backend.login(email, password)
		assertTrue(
			"Updated transaction amount was not found in backend response for Number '$editedTransactionNumber'.",
			backend.hasTransactionAmountByNumber(apiClient, primaryAccountId.toLong(), editedTransactionNumber, "1234.56")
		)
	}

	@Test
	fun currencySymbolFormattingIsConsistentAcrossListsAndEditorLabel() {
		val apiClient = backend.login(email, password)
		backend.updateUserFormatting(
			client = apiClient,
			dateFormat = PREFERRED_DATE_FORMAT,
			decimalSeparator = ".",
			thousandSeparator = ",",
			locale = "en_CA",
			currency = "USD",
			currencyAfter = false,
			currencySpacing = false
		)
		val expectedAccountBalance = backend.getAccountBalanceByName(apiClient, primaryAccountName)
		assertTrue("Expected account balance from backend to be present.", expectedAccountBalance.isNotBlank())
		val expectedTransactionAmount = backend.getTransactionAmountByNumber(
			client = apiClient,
			sourceId = primaryAccountId.toLong(),
			number = transactionOneNumber
		)
		assertTrue("Expected transaction amount from backend to be present.", expectedTransactionAmount.isNotBlank())
		val expectedCurrencyToken = extractCurrencyToken(expectedAccountBalance)
		assertTrue("Could not extract currency token from backend balance '$expectedAccountBalance'.", expectedCurrencyToken.isNotBlank())

		loginToAccountsScreen()
		assertEventuallyVisible(expectedAccountBalance)

		composeTestRule.onNodeWithText(primaryAccountName).performClick()
		assertEventuallyVisible("Transactions")
		assertEventuallyVisible(transactionOneDescription)
		assertEventuallyVisible(expectedTransactionAmount)

		openFirstTransactionEditor()
		assertEventuallyVisible("Amount ($expectedCurrencyToken)")
		val editorAmount = currentEditorAmount()
		assertTrue(
			"Amount editor should not contain currency token '$expectedCurrencyToken': '$editorAmount'.",
			!editorAmount.contains(expectedCurrencyToken)
		)
	}

	@Test
	fun transactionDeleteFromEditorRequiresConfirmationAndDeletes() {
		loginAndOpenPrimaryAccountTransactions()
		openFirstTransactionEditor()
		val deletedTransactionNumber = currentEditorNumber()
		assertTrue("Editor Number field is empty.", deletedTransactionNumber.isNotBlank())

		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_DELETE_TAG, useUnmergedTree = true)
			.performClick()
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_DELETE_CONFIRM_TAG, useUnmergedTree = true)
			.performClick()

		assertEventuallyVisible("Transactions")
		assertEventuallyTagNotVisible(TRANSACTION_EDITOR_DATE_TAG)

		val apiClient = backend.login(email, password)
		assertTrue(
			"Deleted transaction Number '$deletedTransactionNumber' still exists in backend response.",
			backend.getTransactionAmountByNumber(apiClient, primaryAccountId.toLong(), deletedTransactionNumber).isBlank()
		)
	}

	@Test
	fun descriptionSuggestionAutofillKeepsNumberAndMemo() {
		loginAndOpenPrimaryAccountTransactions()
		openNewTransactionEditor()

		val manualNumber = "ANDROID-E2E-MANUAL-${System.currentTimeMillis()}"
		val manualMemo = "Manual memo ${System.currentTimeMillis()}"
		val lookupSubstring = suggestionTemplateDescription.substring(0, minOf(20, suggestionTemplateDescription.length))

		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_NUMBER_TAG, useUnmergedTree = true)
			.performTextReplacement(manualNumber)
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_MEMO_TAG, useUnmergedTree = true)
			.performTextReplacement(manualMemo)
		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_DESCRIPTION_TAG, useUnmergedTree = true)
			.performTextInput(lookupSubstring)

		assertEventuallyVisible(suggestionTemplateDescription)
		composeTestRule.onNodeWithText(suggestionTemplateDescription).performClick()

		assertEquals("Description suggestion should not overwrite Number.", manualNumber, currentEditorNumber())
		assertEquals("Description suggestion should not overwrite Memo.", manualMemo, currentEditorMemo())

		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_SAVE_TAG, useUnmergedTree = true)
			.assertIsEnabled()
			.performClick()

		assertEventuallyVisible("Transactions")
		val apiClient = backend.login(email, password)
		assertTrue(
			"Saved transaction was not visible in selected account with expected amount.",
			backend.hasTransactionAmountByNumber(apiClient, primaryAccountId.toLong(), manualNumber, SUGGESTION_TEMPLATE_AMOUNT)
		)
		assertTrue(
			"Saved transaction memo was changed unexpectedly.",
			backend.hasTransactionMemoByNumber(apiClient, primaryAccountId.toLong(), manualNumber, manualMemo)
		)
	}

	@Test
	fun descriptionSuggestionsHideTemplatesNotApplicableToSelectedAccount() {
		loginAndOpenPrimaryAccountTransactions()
		openNewTransactionEditor()

		composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_DESCRIPTION_TAG, useUnmergedTree = true)
			.performTextInput(suggestionTemplatePrefix)

		assertEventuallyVisible(suggestionTemplateDescription)
		assertEventuallyNotVisible(nonApplicableSuggestionTemplateDescription)
	}

	@Test
	fun transactionsListScrollsAfterRestoringThreeHundredTwentyTransactions() {
		val apiClient = backend.login(email, password)
		val primaryAccountUuid = backend.getAccountUuidByName(apiClient, primaryAccountName)
		val secondaryAccountUuid = backend.getAccountUuidByName(apiClient, secondaryAccountName)
		val descriptionPrefix = "Android E2E Bulk Tx ${System.currentTimeMillis()}"
		val lastRestoredDescription = "$descriptionPrefix ${(BULK_SCROLL_TRANSACTION_COUNT - 1).toString().padStart(3, '0')}"

		backend.restoreTransactions(
			client = apiClient,
			fromAccountUuid = primaryAccountUuid,
			toAccountUuid = secondaryAccountUuid,
			descriptionPrefix = descriptionPrefix,
			transactionCount = BULK_SCROLL_TRANSACTION_COUNT
		)

		loginAndOpenPrimaryAccountTransactions()
		loadAllTransactionPages()
		scrollTransactionListDown()

		composeTestRule
			.onNodeWithTag(TRANSACTIONS_LIST_TAG, useUnmergedTree = true)
			.performScrollToNode(hasText(lastRestoredDescription))
		assertEventuallyVisible(lastRestoredDescription)
	}

	private fun waitForLoginFields() {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule.onAllNodesWithTag(LOGIN_IDENTIFIER_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
				&& composeTestRule.onAllNodesWithTag(LOGIN_PASSWORD_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
		}
	}

	private fun loginAndOpenPrimaryAccountTransactions() {
		loginToAccountsScreen()

		composeTestRule.onNodeWithText(primaryAccountName).performClick()
		assertEventuallyVisible("Transactions")
	}

	private fun loginToAccountsScreen(stayLoggedIn: Boolean = false) {
		waitForLoginFields()

		composeTestRule.onNodeWithTag(LOGIN_IDENTIFIER_TAG, useUnmergedTree = true).performTextReplacement(email)
		composeTestRule.onNodeWithTag(LOGIN_PASSWORD_TAG, useUnmergedTree = true).performTextReplacement(password)
		if (stayLoggedIn) {
			composeTestRule.onNodeWithText("Stay logged in").performClick()
		}
		composeTestRule.onNodeWithText("Sign In").performClick()
		val reachedPostLoginState = runCatching {
			composeTestRule.waitUntil(timeoutMillis = 20_000) {
				isTextVisible(primaryAccountName)
					|| isTextVisible(DEFAULT_ACCOUNT_TYPE)
					|| isTextVisible("Login failed.")
					|| isTextVisible("Email/username and password are required.")
			}
			true
		}.getOrDefault(false)
		if (!reachedPostLoginState) {
			assertTrue(
				"Timed out after Sign In. loginFailed=${isTextVisible("Login failed.")}, " +
					"credentialsRequired=${isTextVisible("Email/username and password are required.")}, " +
					"accountTypeVisible=${isTextVisible(DEFAULT_ACCOUNT_TYPE)}, " +
					"primaryAccountVisible=${isTextVisible(primaryAccountName)}",
				false
			)
		}
		expandAccountTypeIfCollapsed(DEFAULT_ACCOUNT_TYPE, primaryAccountName)

		assertEventuallyVisible(primaryAccountName)
		assertEventuallyVisible(secondaryAccountName)
		assertEventuallyVisible(reserveAccountName)
	}

	private fun expandAccountTypeIfCollapsed(typeName: String, accountName: String) {
		if (isTextVisible(accountName)) {
			return
		}
		assertEventuallyVisible(typeName)
		composeTestRule.onNodeWithText(typeName, useUnmergedTree = true).performClick()
		assertEventuallyVisible(accountName)
	}

	private fun isTextVisible(text: String): Boolean {
		return composeTestRule
			.onAllNodes(hasText(text), useUnmergedTree = true)
			.fetchSemanticsNodes()
			.isNotEmpty()
	}

	private fun openFirstTransactionEditor() {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule
				.onAllNodes(hasContentDescription("Edit"), useUnmergedTree = true)
				.fetchSemanticsNodes().isNotEmpty()
		}
		composeTestRule
			.onAllNodes(hasContentDescription("Edit"), useUnmergedTree = true)[0]
			.performClick()
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule
				.onAllNodesWithTag(TRANSACTION_EDITOR_DATE_TAG, useUnmergedTree = true)
				.fetchSemanticsNodes().isNotEmpty()
		}
	}

	private fun openNewTransactionEditor() {
		composeTestRule.onNode(hasContentDescription("New"), useUnmergedTree = true).performClick()
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule
				.onAllNodesWithTag(TRANSACTION_EDITOR_DATE_TAG, useUnmergedTree = true)
				.fetchSemanticsNodes().isNotEmpty()
		}
	}

	private fun currentEditorNumber(): String {
		val semanticsNode = composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_NUMBER_TAG, useUnmergedTree = true)
			.fetchSemanticsNode()
		return semanticsNode.config.getOrNull(SemanticsProperties.EditableText)?.text?.toString().orEmpty()
	}

	private fun currentEditorAmount(): String {
		val semanticsNode = composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_AMOUNT_TAG, useUnmergedTree = true)
			.fetchSemanticsNode()
		return semanticsNode.config.getOrNull(SemanticsProperties.EditableText)?.text?.toString().orEmpty()
	}

	private fun currentEditorMemo(): String {
		val semanticsNode = composeTestRule
			.onNodeWithTag(TRANSACTION_EDITOR_MEMO_TAG, useUnmergedTree = true)
			.fetchSemanticsNode()
		return semanticsNode.config.getOrNull(SemanticsProperties.EditableText)?.text?.toString().orEmpty()
	}

	private fun loadAllTransactionPages(maxClicks: Int = 6) {
		for (attempt in 0 until maxClicks) {
			val loadMoreNodes = composeTestRule
				.onAllNodes(hasText("Load More"), useUnmergedTree = true)
				.fetchSemanticsNodes()
			if (loadMoreNodes.isEmpty()) {
				return
			}
			composeTestRule.waitUntil(timeoutMillis = 20_000) {
				val nodes = composeTestRule
					.onAllNodes(hasText("Load More"), useUnmergedTree = true)
					.fetchSemanticsNodes()
				nodes.isEmpty() || nodes.any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
			}
			val visibleNodes = composeTestRule
				.onAllNodes(hasText("Load More"), useUnmergedTree = true)
				.fetchSemanticsNodes()
			if (visibleNodes.isEmpty()) {
				return
			}
			composeTestRule.onNodeWithText("Load More", useUnmergedTree = true).performClick()
		}
	}

	private fun scrollTransactionListDown() {
		composeTestRule
			.onNodeWithTag(TRANSACTIONS_LIST_TAG, useUnmergedTree = true)
			.performTouchInput {
				repeat(80) { swipeUp() }
			}
	}

	private fun extractCurrencyToken(formattedAmount: String): String {
		val trimmed = formattedAmount.trim()
		if (trimmed.isEmpty()) {
			return ""
		}
		val prefix = trimmed.takeWhile { !it.isDigit() && it != '-' && it != '(' }.trim()
		if (prefix.isNotEmpty()) {
			return prefix
		}
		val suffix = trimmed.reversed().takeWhile { !it.isDigit() && it != '-' && it != ')' }.reversed().trim()
		return suffix
	}

	private fun assertEventuallyVisible(text: String) {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
		}
		assertTrue(
			"Expected text not visible: $text",
			composeTestRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
		)
	}

	private fun tapRefreshButton() {
		composeTestRule.onNode(hasContentDescription("Refresh"), useUnmergedTree = true).performClick()
	}

	private fun assertEventuallyNotVisible(text: String) {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
		}
		assertTrue(
			"Expected text to be hidden: $text",
			composeTestRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
		)
	}

	private fun assertEventuallyHasLineThrough(text: String) {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			hasLineThroughText(text)
		}
		assertTrue("Expected line-through text style for: $text", hasLineThroughText(text))
	}

	private fun assertEventuallyNoLineThrough(text: String) {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			isTextVisible(text) && !hasLineThroughText(text)
		}
		assertFalse("Expected plain text style for: $text", hasLineThroughText(text))
	}

	private fun hasLineThroughText(text: String): Boolean {
		return hasLineThroughText(text, false) || hasLineThroughText(text, true)
	}

	private fun hasLineThroughText(text: String, useUnmergedTree: Boolean): Boolean {
		val nodes = composeTestRule
			.onAllNodes(hasText(text), useUnmergedTree = useUnmergedTree)
			.fetchSemanticsNodes()
		for (node in nodes) {
			val textLayoutAction = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)
			if (textLayoutAction != null) {
				val textLayoutResults = mutableListOf<TextLayoutResult>()
				if (textLayoutAction.action?.invoke(textLayoutResults) == true) {
					for (textLayoutResult in textLayoutResults) {
						val textDecoration = textLayoutResult.layoutInput.style.textDecoration
						if (textDecoration != null && textDecoration.toString().contains("LineThrough")) {
							return true
						}
					}
				}
			}

			val textEntries = node.config.getOrNull(SemanticsProperties.Text) ?: continue
			for (entry in textEntries) {
				for (styleRange in entry.spanStyles) {
					val decoration = styleRange.item.textDecoration
					if (decoration != null && decoration.toString().contains("LineThrough")) {
						return true
					}
				}
			}
		}
		return false
	}

	private fun assertEventuallyVisibleSubstring(text: String) {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
		}
		assertTrue(
			"Expected text not visible: $text",
			composeTestRule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
		)
	}

	private fun assertEventuallyTagNotVisible(tag: String) {
		composeTestRule.waitUntil(timeoutMillis = 20_000) {
			composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
		}
		assertTrue(
			"Expected tag to be absent: $tag",
			composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
		)
	}

	private fun clearMobileCookies() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		context.getSharedPreferences("buddilive_mobile_cookies", Context.MODE_PRIVATE)
			.edit()
			.clear()
			.commit()
		context.getSharedPreferences("buddilive_mobile_auth", Context.MODE_PRIVATE)
			.edit()
			.clear()
			.commit()
	}

	companion object {
		private const val PREFERRED_DATE_FORMAT = "dd/MM/yyyy"
		private const val PREFERRED_DECIMAL_SEPARATOR = ","
		private const val PREFERRED_THOUSAND_SEPARATOR = " "
		private const val PREFERRED_FIRST_AMOUNT_LABEL = "25,75 $"
		private const val PREFERRED_INVALID_AMOUNT_INPUT = "25.75 $"
		private const val PREFERRED_VALID_AMOUNT_INPUT = "1 234,56 $"
		private const val TRANSACTION_EDITOR_DATE_TAG = "transactionEditorDate"
		private const val TRANSACTION_EDITOR_DESCRIPTION_TAG = "transactionEditorDescription"
		private const val TRANSACTION_EDITOR_NUMBER_TAG = "transactionEditorNumber"
		private const val TRANSACTION_EDITOR_AMOUNT_TAG = "transactionEditorAmount"
		private const val TRANSACTION_EDITOR_MEMO_TAG = "transactionEditorMemo"
		private const val TRANSACTION_EDITOR_DELETE_TAG = "transactionEditorDelete"
			private const val TRANSACTION_EDITOR_DELETE_CONFIRM_TAG = "transactionEditorDeleteConfirm"
			private const val TRANSACTION_EDITOR_SAVE_TAG = "transactionEditorSave"
			private const val TRANSACTIONS_LIST_TAG = "transactionsList"
			private const val LOGIN_IDENTIFIER_TAG = "loginIdentifier"
			private const val LOGIN_PASSWORD_TAG = "loginPassword"
			private const val SUGGESTION_TEMPLATE_AMOUNT = "88.88"
			private const val BULK_SCROLL_TRANSACTION_COUNT = 320
			private const val DEFAULT_ACCOUNT_TYPE = "Chequing"
		}
	}

private class BackendClient(private val baseUrl: String) {

	fun registerUser(email: String, password: String) {
		val form = FormBody.Builder()
			.add("email", email)
			.add("password", password)
			.add("locale", "en_US")
			.add("currency", "USD")
			.add("agree", "on")
			.build()

		execute(
			newClient().newCall(
				Request.Builder()
					.url("$baseUrl/authentication/register")
					.post(form)
					.build()
			).execute()
		) { response ->
			assertEquals(200, response.code)
			assertSuccess(response)
		}
	}

	fun login(email: String, password: String): OkHttpClient {
		val client = newClient()
		val form = FormBody.Builder()
			.add("identifier", email)
			.add("password", password)
			.add("disableIpLock", "on")
			.build()

		execute(
			client.newCall(
				Request.Builder()
					.url("$baseUrl/authentication/login")
					.post(form)
					.build()
			).execute()
		) { response ->
			assertEquals(200, response.code)
			assertSuccess(response)
		}
		return client
	}

	fun createAccount(client: OkHttpClient, name: String, startBalance: String): Int {
		val payload = JSONObject()
			.put("action", "insert")
			.put("name", name)
			.put("type", "D")
			.put("accountType", "Chequing")
			.put("startBalance", startBalance)
			.put("startDate", "2026-01-01")

		postJson(client, "/data/accounts", payload)
		return findAccountIdByName(client, name)
	}

	fun createTransaction(
		client: OkHttpClient,
		description: String,
		number: String,
		date: String,
		fromId: Int,
		toId: Int,
		amount: String
	) {
		val split = JSONObject()
			.put("amount", amount)
			.put("fromId", fromId)
			.put("toId", toId)

		val payload = JSONObject()
			.put("action", "insert")
			.put("description", description)
			.put("number", number)
			.put("date", date)
			.put("splits", JSONArray().put(split))

		postJson(client, "/data/transactions", payload)
	}

	fun updateUserFormatting(
		client: OkHttpClient,
		dateFormat: String,
		decimalSeparator: String,
		thousandSeparator: String,
		locale: String = "en_US",
		currency: String = "USD",
		currencyAfter: Boolean = true,
		currencySpacing: Boolean = true,
		showDeleted: Boolean = true
	) {
		val payload = JSONObject()
			.put("action", "update")
			.put("locale", locale)
			.put("currency", currency)
			.put("showCurrencySymbol", true)
			.put("currencyAfter", currencyAfter)
			.put("currencySpacing", currencySpacing)
			.put("dateFormat", dateFormat)
			.put("decimalSeparator", decimalSeparator)
			.put("thousandSeparator", thousandSeparator)
			.put("showDeleted", showDeleted)
		postJson(client, "/data/userpreferences", payload)
	}

	fun deleteAccount(client: OkHttpClient, accountId: Int) {
		val payload = JSONObject()
			.put("action", "delete")
			.put("id", accountId)
		postJson(client, "/data/accounts", payload)
	}

	fun undeleteAccount(client: OkHttpClient, accountId: Int) {
		val payload = JSONObject()
			.put("action", "undelete")
			.put("id", accountId)
		postJson(client, "/data/accounts", payload)
	}

	fun hasTransactionDescription(client: OkHttpClient, sourceId: Long, description: String): Boolean {
		val response = getJson(client, "/data/transactions?source=$sourceId&start=0&limit=200")
		val data = response.optJSONArray("data") ?: return false
		for (index in 0 until data.length()) {
			val transaction = data.optJSONObject(index) ?: continue
			if (transaction.optString("description") == description) {
				return true
			}
		}
		return false
	}

	fun hasTransactionAmountByNumber(client: OkHttpClient, sourceId: Long, number: String, amount: String): Boolean {
		val expectedAmount = amount.toBigDecimalOrNull() ?: return false
		val response = getJson(client, "/data/transactions?source=$sourceId&start=0&limit=200")
		val data = response.optJSONArray("data") ?: return false
		for (index in 0 until data.length()) {
			val transaction = data.optJSONObject(index) ?: continue
			if (transaction.optString("number") != number) {
				continue
			}
			val splits = transaction.optJSONArray("splits") ?: continue
			for (splitIndex in 0 until splits.length()) {
				val split = splits.optJSONObject(splitIndex) ?: continue
				val amountValue = split.opt("amountNumber")?.toString() ?: continue
				val actualAmount = runCatching { BigDecimal(amountValue) }.getOrNull() ?: continue
				if (actualAmount.compareTo(expectedAmount) == 0) {
					return true
				}
			}
		}
		return false
	}

	fun hasTransactionMemoByNumber(client: OkHttpClient, sourceId: Long, number: String, memo: String): Boolean {
		val response = getJson(client, "/data/transactions?source=$sourceId&start=0&limit=200")
		val data = response.optJSONArray("data") ?: return false
		for (index in 0 until data.length()) {
			val transaction = data.optJSONObject(index) ?: continue
			if (transaction.optString("number") != number) {
				continue
			}
			val splits = transaction.optJSONArray("splits") ?: continue
			for (splitIndex in 0 until splits.length()) {
				val split = splits.optJSONObject(splitIndex) ?: continue
				if (split.optString("memo") == memo) {
					return true
				}
			}
		}
		return false
	}

	fun getAccountBalanceByName(client: OkHttpClient, name: String): String {
		val response = getJson(client, "/data/accounts")
		val account = findAccount(response, name) ?: return ""
		return account.optString("balance")
	}

	fun getAccountUuidByName(client: OkHttpClient, name: String): String {
		val response = getJson(client, "/data/backup")
		val accounts = response.optJSONArray("accounts") ?: JSONArray()
		var uuid = ""
		for (index in 0 until accounts.length()) {
			val account = accounts.optJSONObject(index) ?: continue
			if (account.optString("name") == name) {
				uuid = account.optString("uuid")
				break
			}
		}
		assertTrue("Account UUID not found for '$name'.", uuid.isNotBlank())
		return uuid
	}

	fun restoreTransactions(
		client: OkHttpClient,
		fromAccountUuid: String,
		toAccountUuid: String,
		descriptionPrefix: String,
		transactionCount: Int
	) {
		val restoreBatch = System.currentTimeMillis()
		val transactions = JSONArray()
		for (index in 0 until transactionCount) {
			val description = "$descriptionPrefix ${index.toString().padStart(3, '0')}"
			val split = JSONObject()
				.put("amount", "1.00")
				.put("from", fromAccountUuid)
				.put("to", toAccountUuid)
				.put("memo", "")
			val transaction = JSONObject()
				.put("uuid", "android-e2e-restore-$restoreBatch-$index")
				.put("description", description)
				.put("number", "ANDROID-E2E-RESTORE-$restoreBatch-$index")
				.put("date", "2026-03-01")
				.put("deleted", false)
				.put("splits", JSONArray().put(split))
			transactions.put(transaction)
		}

		val payload = JSONObject()
			.put("accounts", JSONArray().put(JSONObject().put("uuid", fromAccountUuid)).put(JSONObject().put("uuid", toAccountUuid)))
			.put("categories", JSONArray())
			.put("entries", JSONArray())
			.put("transactions", transactions)
			.put("scheduledTransactions", JSONArray())
		postRestore(client, payload)
	}

	fun getTransactionAmountByNumber(client: OkHttpClient, sourceId: Long, number: String): String {
		val response = getJson(client, "/data/transactions?source=$sourceId&start=0&limit=200")
		val data = response.optJSONArray("data") ?: return ""
		for (index in 0 until data.length()) {
			val transaction = data.optJSONObject(index) ?: continue
			if (transaction.optString("number") != number) {
				continue
			}
			val splits = transaction.optJSONArray("splits") ?: continue
			if (splits.length() == 0) {
				continue
			}
			val split = splits.optJSONObject(0) ?: continue
			return split.optString("amount")
		}
		return ""
	}

	private fun findAccountIdByName(client: OkHttpClient, name: String): Int {
		val response = getJson(client, "/data/accounts")
		val found = findAccountId(response, name)
		assertNotNull("Account not found: $name", found)
		return found ?: error("Account not found: $name")
	}

	private fun findAccountId(node: JSONObject, name: String): Int? {
		val account = findAccount(node, name)
		return account?.optInt("id")
	}

	private fun findAccount(node: JSONObject, name: String): JSONObject? {
		if (node.optString("nodeType") == "account" && node.optString("name") == name) {
			return node
		}

		val children = node.optJSONArray("children") ?: return null
		for (index in 0 until children.length()) {
			val child = children.optJSONObject(index) ?: continue
			val found = findAccount(child, name)
			if (found != null) {
				return found
			}
		}
		return null
	}

	private fun postJson(client: OkHttpClient, path: String, payload: JSONObject): JSONObject {
		val request = Request.Builder()
			.url("$baseUrl$path")
			.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
			.build()
		return execute(client.newCall(request).execute()) { response ->
			assertEquals(200, response.code)
			assertSuccess(response)
		}
	}

	private fun getJson(client: OkHttpClient, path: String): JSONObject {
		val request = Request.Builder()
			.url("$baseUrl$path")
			.get()
			.build()
		return execute(client.newCall(request).execute()) { response ->
			assertEquals(200, response.code)
			JSONObject(response.body?.string().orEmpty())
		}
	}

	private fun postRestore(client: OkHttpClient, payload: JSONObject): JSONObject {
		val multipart = MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("file", "restore.json", payload.toString().toRequestBody(JSON_MEDIA_TYPE))
			.build()
		val request = Request.Builder()
			.url("$baseUrl/data/restore")
			.post(multipart)
			.build()
		return execute(client.newCall(request).execute()) { response ->
			assertEquals(200, response.code)
			assertSuccess(response)
		}
	}

	private fun assertSuccess(response: Response): JSONObject {
		val body = JSONObject(response.body?.string().orEmpty())
		assertEquals(true, body.optBoolean("success"))
		return body
	}

	private fun newClient(): OkHttpClient {
		return OkHttpClient.Builder()
			.cookieJar(InMemoryCookieJar())
			.followRedirects(false)
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.build()
	}

	private fun <T> execute(response: Response, block: (Response) -> T): T {
		response.use {
			return block(it)
		}
	}

	private class InMemoryCookieJar : CookieJar {

		private val cookies = mutableListOf<Cookie>()

		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			for (cookie in cookies) {
				this.cookies.removeAll {
					it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
				}
				this.cookies.add(
					Cookie.Builder()
						.name(cookie.name)
						.value(cookie.value)
						.domain(cookie.domain)
						.path(cookie.path)
						.expiresAt(cookie.expiresAt)
						.httpOnly()
						.build()
				)
			}
		}

		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			val now = System.currentTimeMillis()
			return cookies.filter { it.expiresAt >= now }
		}
	}

	companion object {
		private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}
}
