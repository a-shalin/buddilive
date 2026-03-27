package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.UserPreferencesResponseConverter;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.UserPreferencesResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.UserPreferencesRequestDto;
import ca.digitalcave.buddi.live.db.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Currency;
import java.util.Locale;

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
			@RequestBody final UserPreferencesRequestDto dto,
			final HttpServletRequest request,
			final HttpServletResponse response) {
		try {
			final Action action = dto.action();

			if (Action.UPDATE == action) {
				if (Boolean.TRUE.equals(dto.encrypt()) != user.isEncrypted()) {
					final String encryptPassword = dto.encryptPassword();
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
				user.setEmail(Boolean.TRUE.equals(dto.storeEmail()) ? user.getPlaintextIdentifier() : null);
				user.setLocale(LocaleUtil.parseLocale(dto.locale() != null ? dto.locale() : "en_US", Locale.US));
				user.setCurrency(Currency.getInstance(dto.currency() != null ? dto.currency() : "USD"));
				user.setOverrideDateFormat(dto.dateFormat());
				user.setOverrideCurrencyAfter(Boolean.TRUE.equals(dto.currencyAfter()) || (dto.currencyAfter() == null && user.isCurrencyAfter()) ? "Y" : "N");
				user.setOverrideDecimalSeparator(dto.decimalSeparator());
				user.setOverrideThousandsSeparator(dto.thousandSeparator());
				user.setOverrideNegativeFormat(dto.negativeFormat() != null ? dto.negativeFormat() : "N");
				user.setShowCurrencySymbol(Boolean.TRUE.equals(dto.showCurrencySymbol()) || (dto.showCurrencySymbol() == null && user.isShowCurrencySymbol()));
				user.setCurrencySpacing(Boolean.TRUE.equals(dto.currencySpacing()) || (dto.currencySpacing() == null && user.useCurrencySpacing()) ? "Y" : "N");
				user.setTwoFactorRequired(Boolean.TRUE.equals(dto.useTwoFactor()));
				user.setShowDeleted(dto.showDeleted() == null || dto.showDeleted());

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
