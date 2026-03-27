package ca.digitalcave.buddi.live.controller;

import java.util.Currency;
import java.util.Locale;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.api.converter.UserPreferencesResponseConverter;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.UserPreferencesResponseDto;
import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.security.CookieUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/data/userpreferences")
public class UserPreferencesController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private ScheduledTransactions scheduledTransactions;

	@Autowired
	private Entries entries;

	@Autowired
	private Users users;

	@Autowired
	private Crypto crypto;

	@Autowired
	private AuthenticationHelper authenticationHelper;

	@Autowired
	private UserPreferencesResponseConverter userPreferencesResponseConverter;

	@GetMapping
	public UserPreferencesResponseDto get(@AuthenticationPrincipal final User user) {
		return userPreferencesResponseConverter.convert(user);
	}

	@PostMapping
	@Transactional
	public SuccessResponseDto post(@AuthenticationPrincipal final User user,
			@RequestBody final String body,
			final HttpServletRequest request,
			final HttpServletResponse response) {
		try {
			final JSONObject json = new JSONObject(body);
			final Action action = Action.fromString(json.optString("action"));

			if (Action.UPDATE == action) {
				if (json.optBoolean("encrypt", false) != user.isEncrypted()) {
					final String encryptPassword = json.getString("encryptPassword");
					if (!DefaultHash.verify(new String(user.getSecret()), encryptPassword)) {
						throw new ResponseStatusException(HttpStatus.FORBIDDEN, LocaleUtil.getTranslation(user).getString("INCORRECT_PASSWORD"));
					}

					if (user.isEncrypted()) {
						DataUpdater.turnOffEncryption(user, sources, entries, transactions, scheduledTransactions, users, crypto);
					}
					else {
						DataUpdater.turnOnEncryption(user, sources, entries, transactions, scheduledTransactions, users, crypto);
					}
				}
				user.setEmail(json.optBoolean("storeEmail", false) ? user.getPlaintextIdentifier() : null);
				user.setLocale(LocaleUtil.parseLocale(json.optString("locale", "en_US"), Locale.US));
				user.setCurrency(Currency.getInstance(json.optString("currency", "USD")));
				user.setOverrideDateFormat(json.optString("dateFormat", null));
				user.setOverrideCurrencyAfter(json.optBoolean("currencyAfter", user.isCurrencyAfter()) ? "Y" : "N");
				user.setOverrideDecimalSeparator(json.optString("decimalSeparator", null));
				user.setOverrideThousandsSeparator(json.optString("thousandSeparator", null));
				user.setOverrideNegativeFormat(json.optString("negativeFormat", "N"));
				user.setShowCurrencySymbol(json.optBoolean("showCurrencySymbol", user.isShowCurrencySymbol()));
				user.setCurrencySpacing(json.optBoolean("currencySpacing", user.useCurrencySpacing()) ? "Y" : "N");
				user.setTwoFactorRequired(json.optBoolean("useTwoFactor", false));
				user.setShowDeleted(json.optBoolean("showDeleted", true));

				ConstraintsChecker.checkUpdateUserPreferences(user);

				final int count = users.updateUser(user);
				if (count != 1) {
					throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
				}

				if (!user.isTwoFactorRequired()) {
					CookieUtil.setTwoFactorInvalid(request, response, authenticationHelper);
					users.updateUserTotpSecret(user, null);
				}

				DataUpdater.updateBalances(user, sources, transactions, crypto);
			}
			else if (Action.INVALIDATE_TOTP_BACKUPS == action) {
				users.deleteUnusedBackupCodes(user);
			}
			else if (Action.DELETE == action) {
				transactions.deleteAllTransactions(user);
				scheduledTransactions.deleteAllScheduledTransactions(user);
				sources.deleteAllSources(user);
				users.deleteUser(user);
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			return new SuccessResponseDto(true);
		}
		catch (final DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
