package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.request.UserPreferencesRequestDto;
import ca.digitalcave.buddi.live.db.*;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Locale;

@Service
public class UserPreferencesTransactionalService {

	private final Sources sources;
	private final Transactions transactions;
	private final ScheduledTransactions scheduledTransactions;
	private final Entries entries;
	private final Users users;
	private final Crypto crypto;

	public UserPreferencesTransactionalService(final Sources sources,
			final Transactions transactions,
			final ScheduledTransactions scheduledTransactions,
			final Entries entries,
			final Users users,
			final Crypto crypto) {
		this.sources = sources;
		this.transactions = transactions;
		this.scheduledTransactions = scheduledTransactions;
		this.entries = entries;
		this.users = users;
		this.crypto = crypto;
	}

	@Transactional
	public boolean updatePreferences(final User user, final UserPreferencesRequestDto dto, final boolean toggleEncryption) throws CryptoException {
		if (toggleEncryption) {
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
		user.setSkipFocusOnTransactionNumber(Boolean.TRUE.equals(dto.skipFocusOnTransactionNumber()) || (dto.skipFocusOnTransactionNumber() == null && user.isSkipFocusOnTransactionNumber()));

		ConstraintsChecker.checkUpdateUserPreferences(user);

		final int count = users.updateUser(user);
		if (count != 1) {
			throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		}

		if (!user.isTwoFactorRequired()) {
			users.updateUserTotpSecret(user, null);
		}

		DataUpdater.updateBalances(user, sources, transactions, crypto);
		return !user.isTwoFactorRequired();
	}

	@Transactional
	public void invalidateTotpBackups(final User user) {
		users.deleteUnusedBackupCodes(user);
	}

	@Transactional
	public void deleteUserData(final User user) {
		transactions.deleteAllTransactions(user);
		scheduledTransactions.deleteAllScheduledTransactions(user);
		sources.deleteAllSources(user);
		users.deleteUser(user);
	}
}
