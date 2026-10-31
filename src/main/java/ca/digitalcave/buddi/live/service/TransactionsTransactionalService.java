package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.TransactionResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.TransactionRequestDto;
import ca.digitalcave.buddi.live.controller.Action;
import ca.digitalcave.buddi.live.util.CryptoUtil;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class TransactionsTransactionalService {

	private final Sources sources;
	private final Transactions transactions;
	private final Crypto crypto;
	private final TransactionSuggestionsService suggestions;
	private final TransactionsSyncService transactionSync;

	public TransactionsTransactionalService(final Sources sources, final Transactions transactions, final Crypto crypto, final TransactionSuggestionsService suggestions, final TransactionsSyncService transactionSync) {
		this.sources = sources;
		this.transactions = transactions;
		this.crypto = crypto;
		this.suggestions = suggestions;
		this.transactionSync = transactionSync;
	}

	@Transactional
	public TransactionResponseDto mutate(final User user, final TransactionRequestDto request, final boolean includeSuggestions, final boolean includeTransaction) throws CryptoException {
		final Transaction previousTransaction = includeTransaction && request.action() != Action.INSERT && request.id() != null
				? transactions.selectTransactionById(user, request.id()) : null;
		final Set<String> keys = new LinkedHashSet<>();
		if (includeSuggestions) {
			if (request.suggestionKeys() != null) {
				if (request.suggestionKeys().size() > 64 || request.suggestionKeys().stream().anyMatch(key -> key == null || key.length() > 4096)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid suggestion keys");
				}
				keys.addAll(request.suggestionKeys());
			}
			if (request.description() != null) keys.add(request.description());
			if (request.id() != null) {
				final String previous = transactions.selectDescriptionById(user, request.id());
				if (previous != null) keys.add(CryptoUtil.decryptWrapper(previous, user));
			}
		}
		Transaction inserted = null;
		if (request.action() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action is required");
		switch (request.action()) {
			case INSERT -> {
				inserted = insertTransaction(user, Transaction.fromDto(request));
				if (includeSuggestions) keys.add(CryptoUtil.decryptWrapper(inserted.getDescription(), user));
			}
			case UPDATE -> updateTransaction(user, Transaction.fromDto(request));
			case DELETE -> deleteTransaction(user, request.id());
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transaction action");
		}
		final Transaction current = includeTransaction && request.action() != Action.DELETE
				? transactions.selectTransactionById(user, inserted == null ? request.id() : inserted.getId()) : null;
		final List<Integer> previousSourceIds = !includeTransaction ? null : previousTransaction == null ? List.of()
				: previousTransaction.getSplits().stream().flatMap(split -> Stream.of(split.getFromSource(), split.getToSource())).distinct().sorted().toList();
		return new TransactionResponseDto(true, inserted == null ? null : inserted.getId(),
				inserted == null ? null : inserted.getUuid(), includeSuggestions ? suggestions.patch(user, keys) : null,
				current == null ? null : transactionSync.record(user, current, current.getSplits()), previousSourceIds);
	}

	@Transactional
	public Transaction insertTransaction(final User user, final Transaction transaction) throws CryptoException {
		final Transaction existingTransaction = transactions.selectTransactionByUuid(user, transaction.getUuid());
		if (existingTransaction != null) {
			return existingTransaction;
		}

		ConstraintsChecker.checkInsertTransaction(transaction, user, sources, crypto);

		int count = transactions.insertTransaction(user, transaction);
		if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));

		for (final Split split : transaction.getSplits()) {
			split.setTransactionId(transaction.getId());
			count = transactions.insertSplit(user, split);
			if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
		}
		DataUpdater.updateBalances(user, sources, transactions, crypto);
		return transaction;
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
