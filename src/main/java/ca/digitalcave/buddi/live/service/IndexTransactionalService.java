package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IndexTransactionalService {

	private final Users users;
	private final Sources sources;
	private final Transactions transactions;
	private final Entries entries;
	private final ScheduledTransactions scheduledTransactions;
	private final Crypto crypto;

	public IndexTransactionalService(final Users users,
			final Sources sources,
			final Transactions transactions,
			final Entries entries,
			final ScheduledTransactions scheduledTransactions,
			final Crypto crypto) {
		this.users = users;
		this.sources = sources;
		this.transactions = transactions;
		this.entries = entries;
		this.scheduledTransactions = scheduledTransactions;
		this.crypto = crypto;
	}

	@Transactional
	public boolean processAuthenticatedUser(final User user) throws CryptoException {
		final int encryptionVersion = users.selectEncryptionVersion(user);
		if (encryptionVersion == 1) {
			DataUpdater.upgradeEncryptionFrom1(user, sources, entries, transactions, scheduledTransactions, users, crypto);
		}

		final List<Account> accounts = sources.selectAccounts(user);
		users.updateUserLoginTime(user);
		return accounts.isEmpty();
	}
}
