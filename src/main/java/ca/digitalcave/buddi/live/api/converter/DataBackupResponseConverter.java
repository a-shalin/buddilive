package ca.digitalcave.buddi.live.api.converter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.DataBackupResponseDto;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.Entry;
import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@Component
public class DataBackupResponseConverter {

	public DataBackupResponseDto convert(final User user,
			final List<Account> accounts,
			final List<Category> categories,
			final List<Entry> entryList,
			final List<Transaction> transactions,
			final List<ScheduledTransaction> scheduledTransactions) throws CryptoException {
		final Map<Integer, String> sourceUUIDsById = new LinkedHashMap<>();
		final List<Map<String, Object>> accountRows = new ArrayList<>();
		final List<Map<String, Object>> categoryRows = new ArrayList<>();
		final List<Map<String, Object>> entryRows = new ArrayList<>();
		final List<Map<String, Object>> transactionRows = new ArrayList<>();
		final List<Map<String, Object>> scheduledTransactionRows = new ArrayList<>();

		for (final Account account : accounts) {
			accountRows.add(convertAccount(user, account, sourceUUIDsById));
		}
		for (final Category category : categories) {
			categoryRows.add(convertCategory(user, category, sourceUUIDsById));
		}
		for (final Entry entry : entryList) {
			entryRows.add(convertEntry(user, entry, sourceUUIDsById));
		}
		for (final Transaction transaction : transactions) {
			transactionRows.add(convertTransaction(user, transaction, sourceUUIDsById));
		}
		for (final ScheduledTransaction scheduledTransaction : scheduledTransactions) {
			scheduledTransactionRows.add(convertScheduledTransaction(user, scheduledTransaction, sourceUUIDsById));
		}

		return new DataBackupResponseDto(accountRows, categoryRows, entryRows, transactionRows, scheduledTransactionRows);
	}

	private Map<String, Object> convertAccount(final User user,
			final Account account,
			final Map<Integer, String> sourceUUIDsById) throws CryptoException {
		sourceUUIDsById.put(account.getId(), account.getUuid());
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("uuid", account.getUuid());
		row.put("name", CryptoUtil.decryptWrapper(account.getName(), user));
		row.put("startDate", FormatUtil.formatDateInternal(account.getStartDate()));
		if (account.isDeleted()) {
			row.put("deleted", account.isDeleted());
		}
		row.put("type", account.getType());
		row.put("startBalance", CryptoUtil.decryptWrapperBigDecimal(account.getStartBalance(), user, true).toPlainString());
		row.put("accountType", CryptoUtil.decryptWrapper(account.getAccountType(), user));
		return row;
	}

	private Map<String, Object> convertCategory(final User user,
			final Category category,
			final Map<Integer, String> sourceUUIDsById) throws CryptoException {
		sourceUUIDsById.put(category.getId(), category.getUuid());
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("uuid", category.getUuid());
		row.put("name", CryptoUtil.decryptWrapper(category.getName(), user));
		if (category.isDeleted()) {
			row.put("deleted", category.isDeleted());
		}
		row.put("type", category.getType());
		row.put("parent", sourceUUIDsById.get(category.getParent()));
		row.put("periodType", category.getPeriodType());

		if (category.getChildren() != null && !category.getChildren().isEmpty()) {
			final List<Map<String, Object>> children = new ArrayList<>();
			for (final Category child : category.getChildren()) {
				children.add(convertCategory(user, child, sourceUUIDsById));
			}
			row.put("categories", children);
		}
		return row;
	}

	private Map<String, Object> convertEntry(final User user,
			final Entry entry,
			final Map<Integer, String> sourceUUIDsById) throws CryptoException {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("date", FormatUtil.formatDateInternal(entry.getDate()));
		row.put("category", sourceUUIDsById.get(entry.getCategoryId()));
		row.put("amount", CryptoUtil.decryptWrapperBigDecimal(entry.getAmount(), user, true).toPlainString());
		return row;
	}

	private Map<String, Object> convertTransaction(final User user,
			final Transaction transaction,
			final Map<Integer, String> sourceUUIDsById) throws CryptoException {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("uuid", transaction.getUuid());
		row.put("description", CryptoUtil.decryptWrapper(transaction.getDescription(), user));
		row.put("number", CryptoUtil.decryptWrapper(transaction.getNumber(), user));
		row.put("date", FormatUtil.formatDateInternal(transaction.getDate()));
		if (transaction.isDeleted()) {
			row.put("deleted", transaction.isDeleted());
		}
		if (transaction.getSplits() != null && !transaction.getSplits().isEmpty()) {
			final List<Map<String, Object>> splits = new ArrayList<>();
			for (final Split split : transaction.getSplits()) {
				final Map<String, Object> splitRow = new LinkedHashMap<>();
				splitRow.put("amount", CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true).toPlainString());
				splitRow.put("from", sourceUUIDsById.get(split.getFromSource()));
				splitRow.put("to", sourceUUIDsById.get(split.getToSource()));
				splitRow.put("memo", CryptoUtil.decryptWrapper(split.getMemo(), user));
				splits.add(splitRow);
			}
			row.put("splits", splits);
		}
		return row;
	}

	private Map<String, Object> convertScheduledTransaction(final User user,
			final ScheduledTransaction scheduledTransaction,
			final Map<Integer, String> sourceUUIDsById) throws CryptoException {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("uuid", scheduledTransaction.getUuid());
		row.put("description", CryptoUtil.decryptWrapper(scheduledTransaction.getDescription(), user));
		row.put("number", CryptoUtil.decryptWrapper(scheduledTransaction.getNumber(), user));
		row.put("scheduleName", CryptoUtil.decryptWrapper(scheduledTransaction.getScheduleName(), user));
		row.put("scheduleDay", scheduledTransaction.getScheduleDay());
		row.put("scheduleWeek", scheduledTransaction.getScheduleWeek());
		row.put("scheduleMonth", scheduledTransaction.getScheduleMonth());
		row.put("frequencyType", scheduledTransaction.getFrequencyType());
		row.put("startDate", FormatUtil.formatDateInternal(scheduledTransaction.getStartDate()));
		row.put("endDate", FormatUtil.formatDateInternal(scheduledTransaction.getEndDate()));
		row.put("lastCreatedDate", FormatUtil.formatDateInternal(scheduledTransaction.getLastCreatedDate()));
		row.put("message", CryptoUtil.decryptWrapper(scheduledTransaction.getMessage(), user));
		if (scheduledTransaction.getSplits() != null && !scheduledTransaction.getSplits().isEmpty()) {
			final List<Map<String, Object>> splits = new ArrayList<>();
			for (final Split split : scheduledTransaction.getSplits()) {
				final Map<String, Object> splitRow = new LinkedHashMap<>();
				splitRow.put("amount", CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true).toPlainString());
				splitRow.put("from", sourceUUIDsById.get(split.getFromSource()));
				splitRow.put("to", sourceUUIDsById.get(split.getToSource()));
				splitRow.put("memo", CryptoUtil.decryptWrapper(split.getMemo(), user));
				splits.add(splitRow);
			}
			row.put("splits", splits);
		}
		return row;
	}
}
