package ca.digitalcave.buddi.live.service;

import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto.RestoreAccountDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto.RestoreCategoryDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto.RestoreEntryDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto.RestoreScheduledTransactionDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto.RestoreSplitDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto.RestoreTransactionDto;
import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.Entry;
import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataManagementTransactionalService {

	private final Sources sources;
	private final Transactions transactions;
	private final ScheduledTransactions scheduledTransactions;
	private final Entries entries;
	private final Crypto crypto;

	public DataManagementTransactionalService(final Sources sources,
			final Transactions transactions,
			final ScheduledTransactions scheduledTransactions,
			final Entries entries,
			final Crypto crypto) {
		this.sources = sources;
		this.transactions = transactions;
		this.scheduledTransactions = scheduledTransactions;
		this.entries = entries;
		this.crypto = crypto;
	}

	@Transactional
	public void restore(final User user, final DataRestoreDto request, final boolean deleteData) throws CryptoException {
		if (deleteData) {
			sources.deleteAllSources(user);
			transactions.deleteAllTransactions(user);
			scheduledTransactions.deleteAllScheduledTransactions(user);
		}

		final Map<String, Integer> sourceIDsByUUID = new HashMap<>();
		restoreAccounts(request.accounts(), user, sourceIDsByUUID);
		restoreCategories(request.categories(), user, sourceIDsByUUID, null);
		restoreEntries(request.entries(), user, sourceIDsByUUID);
		restoreTransactions(request.transactions(), user, sourceIDsByUUID);
		restoreScheduledTransactions(request.scheduledTransactions(), user, sourceIDsByUUID);

		DataUpdater.updateBalances(user, sources, transactions, crypto);
	}

	private void restoreAccounts(final List<RestoreAccountDto> accounts, final User user, final Map<String, Integer> sourceIDsByUUID) throws CryptoException {
		if (accounts != null) {
			for (final RestoreAccountDto a : accounts) {
				final Account existing = sources.selectAccount(user, a.uuid());
				if (existing == null) {
					final Account account = new Account();
					account.setUuid(a.uuid());
					account.setName(a.name());
					account.setStartDate(FormatUtil.parseDateInternal(a.startDate()));
					account.setDeleted(Boolean.TRUE.equals(a.deleted()));
					account.setType(a.type());
					account.setStartBalance(FormatUtil.parseCurrency(a.startBalance()).toPlainString());
					account.setAccountType(a.accountType());

					ConstraintsChecker.checkInsertAccount(account, user, sources, crypto);
					int count = sources.insertAccount(user, account);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
					sourceIDsByUUID.put(account.getUuid(), account.getId());
				}
				else {
					sourceIDsByUUID.put(existing.getUuid(), existing.getId());
				}
			}
		}
	}

	private void restoreCategories(final List<RestoreCategoryDto> categories, final User user, final Map<String, Integer> sourceIDsByUUID, final String parentUuid) throws CryptoException {
		if (categories != null) {
			for (final RestoreCategoryDto c : categories) {
				final Category existing = sources.selectCategory(user, c.uuid());
				if (existing == null) {
					final Category category = new Category();
					category.setUuid(c.uuid());
					category.setName(c.name());
					category.setDeleted(Boolean.TRUE.equals(c.deleted()));
					category.setType(c.type());
					final Integer impliedParent = parentUuid != null ? sourceIDsByUUID.get(parentUuid) : null;
					final Integer explicitParent = c.parent() != null ? sourceIDsByUUID.get(c.parent()) : null;
					category.setParent(impliedParent != null ? impliedParent : explicitParent);
					category.setPeriodType(c.periodType());

					ConstraintsChecker.checkInsertCategory(category, user, sources, crypto);
					int count = sources.insertCategory(user, category);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
					sourceIDsByUUID.put(category.getUuid(), category.getId());
				}
				else {
					sourceIDsByUUID.put(existing.getUuid(), existing.getId());
				}
				if (c.categories() != null) {
					restoreCategories(c.categories(), user, sourceIDsByUUID, c.uuid());
				}
			}
		}
	}

	private void restoreEntries(final List<RestoreEntryDto> entryList, final User user, final Map<String, Integer> sourceIDsByUUID) throws CryptoException {
		if (entryList != null) {
			for (final RestoreEntryDto e : entryList) {
				final Entry entry = new Entry();
				final Integer categoryId = sourceIDsByUUID.get(e.category());
				if (categoryId == null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find category UUID " + e.category() + " for budget entry " + e.date() + " / " + e.amount());
				}
				entry.setCategoryId(categoryId);
				entry.setDate(FormatUtil.parseDateInternal(e.date()));
				entry.setAmount(FormatUtil.parseCurrency(e.amount()).toPlainString());
				final Entry existingEntry = entries.selectEntry(user, entry);
				if (existingEntry == null) {
					ConstraintsChecker.checkInsertEntry(entry, user, sources, crypto);
					int count = entries.insertEntry(user, entry);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
				else {
					ConstraintsChecker.checkUpdateEntry(entry, user, sources, entries, crypto);
					int count = entries.updateEntry(user, entry);
					if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
				}
			}
		}
	}

	private void restoreTransactions(final List<RestoreTransactionDto> txnList, final User user, final Map<String, Integer> sourceIDsByUUID) throws CryptoException {
		if (txnList != null) {
			for (final RestoreTransactionDto t : txnList) {
				if (Boolean.TRUE.equals(t.deleted())) continue;
				if (transactions.selectTransactionCount(user, t.uuid()) == 0) {
					final Transaction transaction = new Transaction();
					transaction.setUuid(t.uuid());
					transaction.setDescription(t.description());
					transaction.setNumber(t.number());
					transaction.setDate(FormatUtil.parseDateInternal(t.date()));
					transaction.setDeleted(Boolean.TRUE.equals(t.deleted()));
					transaction.setSplits(new ArrayList<>());
					if (t.splits() != null) {
						for (int j = 0; j < t.splits().size(); j++) {
							final RestoreSplitDto s = t.splits().get(j);
							if (FormatUtil.parseCurrency(s.amount()).compareTo(BigDecimal.ZERO) != 0) {
								final Split split = new Split();
								final Integer fromSource = sourceIDsByUUID.get(s.from());
								final Integer toSource = sourceIDsByUUID.get(s.to());
								if (fromSource == null) {
									throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find 'from' source UUID " + s.from() + " for split #" + j + " in transaction " + t.uuid());
								}
								if (toSource == null) {
									throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find 'to' source UUID " + s.to() + " for split #" + j + " in transaction " + t.uuid());
								}
								split.setAmount(FormatUtil.parseCurrency(s.amount()).toPlainString());
								split.setFromSource(fromSource);
								split.setToSource(toSource);
								split.setMemo(s.memo() != null ? s.memo() : "");
								transaction.getSplits().add(split);
							}
						}
					}

					if (!transaction.getSplits().isEmpty()) {
						ConstraintsChecker.checkInsertTransaction(transaction, user, sources, crypto);
						int count = transactions.insertTransaction(user, transaction);
						if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
						for (Split split : transaction.getSplits()) {
							split.setTransactionId(transaction.getId());
							count = transactions.insertSplit(user, split);
							if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
						}
					}
				}
			}
		}
	}

	private void restoreScheduledTransactions(final List<RestoreScheduledTransactionDto> txnList, final User user, final Map<String, Integer> sourceIDsByUUID) throws CryptoException {
		if (txnList != null) {
			for (final RestoreScheduledTransactionDto t : txnList) {
				if (scheduledTransactions.selectScheduledTransactionCount(user, t.uuid()) == 0) {
					final ScheduledTransaction transaction = new ScheduledTransaction();
					transaction.setUuid(t.uuid());
					transaction.setDescription(t.description());
					transaction.setNumber(t.number());
					transaction.setScheduleName(t.scheduleName());
					transaction.setScheduleDay(t.scheduleDay());
					transaction.setScheduleWeek(t.scheduleWeek());
					transaction.setScheduleMonth(t.scheduleMonth());
					transaction.setFrequencyType(t.frequencyType());
					transaction.setStartDate(FormatUtil.parseDateInternal(t.startDate()));
					transaction.setEndDate(FormatUtil.parseDateInternal(t.endDate()));
					transaction.setLastCreatedDate(FormatUtil.parseDateInternal(t.lastCreatedDate()));
					transaction.setMessage(t.message());
					transaction.setSplits(new ArrayList<>());
					if (t.splits() != null) {
						for (int j = 0; j < t.splits().size(); j++) {
							final RestoreSplitDto s = t.splits().get(j);
							final Split split = new Split();
							final Integer fromSource = sourceIDsByUUID.get(s.from());
							if (fromSource == null) {
								throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find transaction from source UUID " + s.from() + " for split #" + j + " in transaction " + t.uuid());
							}
							final Integer toSource = sourceIDsByUUID.get(s.to());
							if (toSource == null) {
								throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find transaction from source UUID " + s.to() + " for split #" + j + " in transaction " + t.uuid());
							}
							split.setAmount(FormatUtil.parseCurrency(s.amount()).toPlainString());
							split.setFromSource(fromSource);
							split.setToSource(toSource);
							split.setMemo(s.memo());
							transaction.getSplits().add(split);
						}
					}

					ConstraintsChecker.checkInsertScheduledTransaction(transaction, user, sources, crypto);
					int count = scheduledTransactions.insertScheduledTransaction(user, transaction);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
					for (Split split : transaction.getSplits()) {
						split.setTransactionId(transaction.getId());
						count = scheduledTransactions.insertScheduledSplit(user, split);
						if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
					}
				}
			}
		}
	}
}
