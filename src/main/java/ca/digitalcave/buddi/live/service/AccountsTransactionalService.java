package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.controller.Action;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountsTransactionalService {

	private final Sources sources;
	private final Transactions transactions;
	private final Crypto crypto;

	public AccountsTransactionalService(final Sources sources, final Transactions transactions, final Crypto crypto) {
		this.sources = sources;
		this.transactions = transactions;
		this.crypto = crypto;
	}

	@Transactional
	public void applyAction(final User user, final Action action, final Account account) throws CryptoException {
		if (Action.INSERT == action) {
			ConstraintsChecker.checkInsertAccount(account, user, sources, crypto);
			final int count = sources.insertAccount(user, account);
			if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
		}
		else if (Action.DELETE == action || Action.UNDELETE == action) {
			if (sources.selectSourceAssociatedCount(user, account) == 0) {
				final int count = sources.deleteSource(user, account);
				if (count != 1) throw new DatabaseException(String.format("Delete failed; expected 1 row, returned %s", count));
			}
			else {
				account.setDeleted(Action.DELETE == action);
				final int count = sources.updateSourceDeleted(user, account);
				if (count != 1) throw new DatabaseException(String.format("Delete / undelete failed; expected 1 row, returned %s", count));
			}
		}
		else if (Action.UPDATE == action) {
			ConstraintsChecker.checkUpdateAccount(account, user, sources, crypto);
			final int count = sources.updateAccount(user, account);
			if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		}
		else {
			throw new DatabaseException("Unsupported action");
		}

		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}
}
