package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.CategoryPeriod.CategoryPeriods;
import ca.digitalcave.buddi.live.model.Entry;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Service
public class CategoriesTransactionalService {

	private final Sources sources;
	private final Transactions transactions;
	private final Entries entries;
	private final Crypto crypto;

	public CategoriesTransactionalService(final Sources sources, final Transactions transactions, final Entries entries, final Crypto crypto) {
		this.sources = sources;
		this.transactions = transactions;
		this.entries = entries;
		this.crypto = crypto;
	}

	@Transactional
	public void insertCategory(final User user, final Category category) throws CryptoException {
		ConstraintsChecker.checkInsertCategory(category, user, sources, crypto);
		final int count = sources.insertCategory(user, category);
		if (count != 1) {
			throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
		}
		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}

	@Transactional
	public void deleteCategory(final User user, final Category category) throws CryptoException {
		deleteOrUndeleteCategory(user, category, true);
	}

	@Transactional
	public void undeleteCategory(final User user, final Category category) throws CryptoException {
		deleteOrUndeleteCategory(user, category, false);
	}

	@Transactional
	public void updateCategory(final User user, final Category category) throws CryptoException {
		ConstraintsChecker.checkUpdateCategory(category, user, sources, crypto);
		final int count = sources.updateCategory(user, category);
		if (count != 1) {
			throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		}
		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}

	@Transactional
	public void copyFromPrevious(final User user, final CategoryPeriods period, final Date date) throws CryptoException {
		final Date currentDate = period.getStartOfBudgetPeriod(date);
		final Date previousDate = period.getBudgetPeriodOffset(currentDate, -1);

		final Map<Integer, Entry> previousEntries = entries.selectEntries(user, previousDate);
		final Map<Integer, Entry> currentEntries = entries.selectEntries(user, currentDate);
		for (final Integer categoryId : previousEntries.keySet()) {
			if (CryptoUtil.decryptWrapperBigDecimal(previousEntries.get(categoryId).getAmount(), user, true).compareTo(BigDecimal.ZERO) != 0) {
				if (currentEntries.get(categoryId) == null) {
					final Entry entry = previousEntries.get(categoryId);
					entry.setDate(currentDate);
					ConstraintsChecker.checkInsertEntry(entry, user, sources, crypto);
					final int count = entries.insertEntry(user, entry);
					if (count != 1) {
						throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
					}
				}
				else if (CryptoUtil.decryptWrapperBigDecimal(currentEntries.get(categoryId).getAmount(), user, true).compareTo(BigDecimal.ZERO) == 0) {
					final Entry entry = currentEntries.get(categoryId);
					entry.setAmount(previousEntries.get(categoryId).getAmount());
					ConstraintsChecker.checkUpdateEntry(entry, user, sources, entries, crypto);
					final int count = entries.updateEntry(user, entry);
					if (count != 1) {
						throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
					}
				}
			}
		}

		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}

	@Transactional
	public void setEntry(final User user, final Entry entry) throws CryptoException {
		final Entry existingEntry = entries.selectEntry(user, entry);

		if (existingEntry == null) {
			ConstraintsChecker.checkInsertEntry(entry, user, sources, crypto);
			final int count = entries.insertEntry(user, entry);
			if (count != 1) {
				throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
			}
		}
		else {
			ConstraintsChecker.checkUpdateEntry(entry, user, sources, entries, crypto);
			final int count = entries.updateEntry(user, entry);
			if (count != 1) {
				throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
			}
		}

		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}

	private void deleteOrUndeleteCategory(final User user, final Category category, final boolean deleted) throws CryptoException {
		if (sources.selectSourceAssociatedCount(user, category) == 0) {
			final int count = sources.deleteSource(user, category);
			if (count != 1) {
				throw new DatabaseException(String.format("Delete failed; expected 1 row, returned %s", count));
			}
		}
		else {
			category.setDeleted(deleted);
			final int count = sources.updateSourceDeleted(user, category);
			if (count != 1) {
				throw new DatabaseException(String.format("Delete / undelete failed; expected 1 row, returned %s", count));
			}
		}

		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}
}
