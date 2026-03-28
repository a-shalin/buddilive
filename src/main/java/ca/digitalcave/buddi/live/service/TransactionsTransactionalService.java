package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionsTransactionalService {

	private final Sources sources;
	private final Transactions transactions;
	private final Crypto crypto;

	public TransactionsTransactionalService(final Sources sources, final Transactions transactions, final Crypto crypto) {
		this.sources = sources;
		this.transactions = transactions;
		this.crypto = crypto;
	}

	@Transactional
	public void insertTransaction(final User user, final Transaction transaction) throws CryptoException {
		ConstraintsChecker.checkInsertTransaction(transaction, user, sources, crypto);

		int count = transactions.insertTransaction(user, transaction);
		if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));

		for (final Split split : transaction.getSplits()) {
			split.setTransactionId(transaction.getId());
			count = transactions.insertSplit(user, split);
			if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
		}
		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}

	@Transactional
	public void updateTransaction(final User user, final Transaction transaction) throws CryptoException {
		ConstraintsChecker.checkUpdateTransaction(transaction, user, sources, crypto);

		int count = transactions.updateTransaction(user, transaction);
		if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));

		count = transactions.deleteSplits(user, transaction);
		if (count == 0) throw new DatabaseException("Failed to delete splits; expected 1 or more rows, returned 0");

		for (final Split split : transaction.getSplits()) {
			split.setTransactionId(transaction.getId());
			count = transactions.insertSplit(user, split);
			if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
		}
		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}

	@Transactional
	public void deleteTransaction(final User user, final Long transactionId) throws CryptoException {
		final Transaction deleteTransaction = new Transaction();
		deleteTransaction.setId(transactionId);
		final int count = transactions.deleteTransaction(user, deleteTransaction);
		if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}
}
