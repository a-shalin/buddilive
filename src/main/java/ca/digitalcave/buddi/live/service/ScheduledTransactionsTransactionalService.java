package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.controller.Action;
import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class ScheduledTransactionsTransactionalService {

	private final Sources sources;
	private final Transactions transactions;
	private final ScheduledTransactions scheduledTransactions;
	private final Crypto crypto;

	public ScheduledTransactionsTransactionalService(final Sources sources,
			final Transactions transactions,
			final ScheduledTransactions scheduledTransactions,
			final Crypto crypto) {
		this.sources = sources;
		this.transactions = transactions;
		this.scheduledTransactions = scheduledTransactions;
		this.crypto = crypto;
	}

	@Transactional
	public void applyAction(final User user,
			final Action action,
			final ScheduledTransaction scheduledTransaction,
			final Long scheduledTransactionId) throws CryptoException {
		if (Action.INSERT == action) {
			ConstraintsChecker.checkInsertScheduledTransaction(scheduledTransaction, user, sources, crypto);

			int count = scheduledTransactions.insertScheduledTransaction(user, scheduledTransaction);
			if (count != 1) {
				throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
			}

			for (final Split split : scheduledTransaction.getSplits()) {
				split.setTransactionId(scheduledTransaction.getId());
				count = scheduledTransactions.insertScheduledSplit(user, split);
				if (count != 1) {
					throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
			}
		}
		else if (Action.UPDATE == action) {
			ConstraintsChecker.checkUpdateScheduledTransaction(scheduledTransaction, user, sources, crypto);

			int count = scheduledTransactions.updateScheduledTransaction(user, scheduledTransaction);
			if (count != 1) {
				throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
			}

			count = scheduledTransactions.deleteScheduledSplits(user, scheduledTransaction);
			if (count == 0) {
				throw new DatabaseException(String.format("Delete scheduled splits failed; expected 1 or more rows, returned %s", count));
			}
			for (final Split split : scheduledTransaction.getSplits()) {
				split.setTransactionId(scheduledTransaction.getId());
				count = scheduledTransactions.insertScheduledSplit(user, split);
				if (count != 1) {
					throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
			}
		}
		else if (Action.DELETE == action) {
			final ScheduledTransaction deleteScheduledTransaction = new ScheduledTransaction();
			deleteScheduledTransaction.setId(scheduledTransactionId);
			final int count = scheduledTransactions.deleteScheduledTransaction(user, deleteScheduledTransaction);
			if (count != 1) {
				throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
			}
		}
		else {
			throw new DatabaseException("Unsupported action");
		}

		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}

	@Transactional
	public String execute(final User user, final Date userDate) throws CryptoException {
		return DataUpdater.updateScheduledTransactions(user, sources, transactions, scheduledTransactions, crypto, userDate);
	}
}
