package ca.digitalcave.buddi.live.controller;

import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
import ca.digitalcave.buddi.live.model.report.Interval;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@RestController
public class DataManagementController {

	private static final Logger logger = Logger.getLogger(DataManagementController.class.getName());

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private ScheduledTransactions scheduledTransactions;

	@Autowired
	private Entries entries;

	@Autowired
	private Crypto crypto;

	@GetMapping("/data/backup")
	public ResponseEntity<String> backup(@AuthenticationPrincipal User user) {
		try {
			final JSONObject result = new JSONObject();
			final List<Account> accounts = sources.selectAccounts(user);
			final List<Category> categories = Category.getHierarchy(sources.selectCategories(user));
			final List<Transaction> txns = transactions.selectTransactions(user);
			final List<ScheduledTransaction> scheduledTxns = scheduledTransactions.selectScheduledTransactions(user);
			final List<Entry> entryList = entries.selectEntries(user);
			final Map<Integer, String> sourceUUIDsById = new HashMap<>();

			for (Account account : accounts) {
				addAccount(result, user, account, sourceUUIDsById);
			}
			for (Category category : categories) {
				addCategory(result, user, category, sourceUUIDsById);
			}
			for (Entry entry : entryList) {
				addEntry(result, user, entry, sourceUUIDsById);
			}
			for (Transaction transaction : txns) {
				addTransaction(result, user, transaction, sourceUUIDsById);
			}
			for (ScheduledTransaction scheduledTransaction : scheduledTxns) {
				addScheduledTransaction(result, user, scheduledTransaction, sourceUUIDsById);
			}

			final String filename = "Backup (" + FormatUtil.formatDate(new Date(), user) + ").json";
			final HttpHeaders headers = new HttpHeaders();
			headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
			headers.setContentType(MediaType.APPLICATION_JSON);
			return new ResponseEntity<>(result.toString(), headers, HttpStatus.OK);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private void addAccount(JSONObject result, User user, Account account, Map<Integer, String> sourceUUIDsById) throws CryptoException {
		sourceUUIDsById.put(account.getId(), account.getUuid());
		final JSONObject a = new JSONObject();
		a.put("uuid", account.getUuid());
		a.put("name", CryptoUtil.decryptWrapper(account.getName(), user));
		a.put("startDate", FormatUtil.formatDateInternal((Date) account.getStartDate()));
		if (account.isDeleted()) a.put("deleted", account.isDeleted());
		a.put("type", account.getType());
		a.put("startBalance", CryptoUtil.decryptWrapperBigDecimal(account.getStartBalance(), user, true).toPlainString());
		a.put("accountType", CryptoUtil.decryptWrapper(account.getAccountType(), user));
		result.append("accounts", a);
	}

	private void addCategory(JSONObject result, User user, Category category, Map<Integer, String> sourceUUIDsById) throws CryptoException {
		sourceUUIDsById.put(category.getId(), category.getUuid());
		final JSONObject c = new JSONObject();
		c.put("uuid", category.getUuid());
		c.put("name", CryptoUtil.decryptWrapper(category.getName(), user));
		if (category.isDeleted()) c.put("deleted", category.isDeleted());
		c.put("type", category.getType());
		c.put("parent", sourceUUIDsById.get(category.getParent()));
		c.put("periodType", category.getPeriodType());
		if (category.getChildren() != null) {
			for (Category child : category.getChildren()) {
				addCategory(c, user, child, sourceUUIDsById);
			}
		}
		result.append("categories", c);
	}

	private void addEntry(JSONObject result, User user, Entry entry, Map<Integer, String> sourceUUIDsById) throws CryptoException {
		final JSONObject e = new JSONObject();
		e.put("date", FormatUtil.formatDateInternal((Date) entry.getDate()));
		e.put("category", sourceUUIDsById.get(entry.getCategoryId()));
		e.put("amount", CryptoUtil.decryptWrapperBigDecimal(entry.getAmount(), user, true).toPlainString());
		result.append("entries", e);
	}

	private void addTransaction(JSONObject result, User user, Transaction transaction, Map<Integer, String> sourceUUIDsById) throws CryptoException {
		final JSONObject t = new JSONObject();
		t.put("uuid", transaction.getUuid());
		t.put("description", CryptoUtil.decryptWrapper(transaction.getDescription(), user));
		t.put("number", CryptoUtil.decryptWrapper(transaction.getNumber(), user));
		t.put("date", FormatUtil.formatDateInternal((Date) transaction.getDate()));
		if (transaction.isDeleted()) t.put("deleted", transaction.isDeleted());
		if (transaction.getSplits() != null) {
			for (Split split : transaction.getSplits()) {
				final JSONObject s = new JSONObject();
				s.put("amount", CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true).toPlainString());
				s.put("from", sourceUUIDsById.get(split.getFromSource()));
				s.put("to", sourceUUIDsById.get(split.getToSource()));
				s.put("memo", CryptoUtil.decryptWrapper(split.getMemo(), user));
				t.append("splits", s);
			}
		}
		result.append("transactions", t);
	}

	private void addScheduledTransaction(JSONObject result, User user, ScheduledTransaction scheduledTransaction, Map<Integer, String> sourceUUIDsById) throws CryptoException {
		final JSONObject t = new JSONObject();
		t.put("uuid", scheduledTransaction.getUuid());
		t.put("description", CryptoUtil.decryptWrapper(scheduledTransaction.getDescription(), user));
		t.put("number", CryptoUtil.decryptWrapper(scheduledTransaction.getNumber(), user));
		t.put("scheduleName", CryptoUtil.decryptWrapper(scheduledTransaction.getScheduleName(), user));
		t.put("scheduleDay", scheduledTransaction.getScheduleDay());
		t.put("scheduleWeek", scheduledTransaction.getScheduleWeek());
		t.put("scheduleMonth", scheduledTransaction.getScheduleMonth());
		t.put("frequencyType", scheduledTransaction.getFrequencyType());
		t.put("startDate", FormatUtil.formatDateInternal((Date) scheduledTransaction.getStartDate()));
		t.put("endDate", FormatUtil.formatDateInternal((Date) scheduledTransaction.getEndDate()));
		t.put("lastCreatedDate", FormatUtil.formatDateInternal((Date) scheduledTransaction.getLastCreatedDate()));
		t.put("message", CryptoUtil.decryptWrapper(scheduledTransaction.getMessage(), user));
		if (scheduledTransaction.getSplits() != null) {
			for (Split split : scheduledTransaction.getSplits()) {
				final JSONObject s = new JSONObject();
				s.put("amount", CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true).toPlainString());
				s.put("from", sourceUUIDsById.get(split.getFromSource()));
				s.put("to", sourceUUIDsById.get(split.getToSource()));
				s.put("memo", CryptoUtil.decryptWrapper(split.getMemo(), user));
				t.append("splits", s);
			}
		}
		result.append("scheduledTransactions", t);
	}

	@GetMapping("/data/export")
	public ResponseEntity<StreamingResponseBody> export(@AuthenticationPrincipal User user,
			@RequestParam String interval,
			@RequestParam String type,
			@RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate) {
		if (!user.isPremium()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		if (!"csv".equalsIgnoreCase(type)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}

		final Interval i = Interval.valueOf(interval);
		final Date[] dates;
		if (i != Interval.PLUGIN_FILTER_OTHER) {
			dates = new Date[]{i.getStartDate(), i.getEndDate()};
		}
		else {
			final Date start = FormatUtil.parseDateInternal(startDate);
			final Date end = FormatUtil.parseDateInternal(endDate);
			if (start == null || end == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "For PLUGIN_FILTER_OTHER intervals, startDate and endDate parameters are required.");
			dates = new Date[]{start, end};
		}

		final StreamingResponseBody body = outputStream -> {
			final CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream), CSVFormat.EXCEL);
			try {
				csvPrinter.printRecord(new Object[]{"Date", "Description", "Number", "Amount", "From", "To", "Memo"});
				final List<Transaction> txns = transactions.selectTransactions(user, dates[0], dates[1]);
				for (Transaction transaction : txns) {
					if (transaction.getSplits() != null) {
						for (Split split : transaction.getSplits()) {
							csvPrinter.printRecord(
									FormatUtil.formatDate(transaction.getDate(), user),
									CryptoUtil.decryptWrapper(transaction.getDescription(), user),
									CryptoUtil.decryptWrapper(transaction.getNumber(), user),
									CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true).toPlainString(),
									CryptoUtil.decryptWrapper(split.getFromSourceName(), user),
									CryptoUtil.decryptWrapper(split.getToSourceName(), user),
									CryptoUtil.decryptWrapper(split.getMemo(), user)
							);
						}
					}
				}
				csvPrinter.flush();
			}
			catch (CryptoException e) {
				logger.log(Level.WARNING, "Error during CSV export", e);
			}
			finally {
				csvPrinter.close();
			}
		};

		final String filename = "Export (" + FormatUtil.formatDate(new Date(), user) + ").csv";
		final HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
		headers.set(HttpHeaders.CONTENT_TYPE, "text/csv");
		return new ResponseEntity<>(body, headers, HttpStatus.OK);
	}

	@PostMapping("/data/restore")
	@Transactional
	public String restore(@AuthenticationPrincipal User user,
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "deleteData", defaultValue = "false") boolean deleteData) {
		try {
			if (deleteData) {
				sources.deleteAllSources(user);
				transactions.deleteAllTransactions(user);
				scheduledTransactions.deleteAllScheduledTransactions(user);
			}

			final JSONObject request = new JSONObject(new String(file.getBytes()));
			final Map<String, Integer> sourceIDsByUUID = new HashMap<>();
			restoreAccounts(request, user, sourceIDsByUUID);
			restoreCategories(request, user, sourceIDsByUUID);
			restoreEntries(request, user, sourceIDsByUUID);
			restoreTransactions(request, user, sourceIDsByUUID);
			restoreScheduledTransactions(request, user, sourceIDsByUUID);

			DataUpdater.updateBalances(user, sources, transactions, crypto);

			final JSONObject result = new JSONObject();
			result.put("success", true);
			return result.toString();
		}
		catch (DatabaseException e) {
			logger.log(Level.WARNING, "Error encountered when restoring data", e);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (CryptoException e) {
			logger.log(Level.WARNING, "Error encountered when restoring data", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Error encountered when restoring data", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private void restoreAccounts(JSONObject jsonObject, User user, Map<String, Integer> sourceIDsByUUID) throws DatabaseException, CryptoException {
		final JSONArray accounts = jsonObject.optJSONArray("accounts");
		if (accounts != null) {
			for (int i = 0; i < accounts.length(); i++) {
				final JSONObject a = accounts.getJSONObject(i);
				final Account existing = sources.selectAccount(user, a.getString("uuid"));
				if (existing == null) {
					final Account account = new Account();
					account.setUuid(a.getString("uuid"));
					account.setName(a.getString("name"));
					account.setStartDate(FormatUtil.parseDateInternal(a.getString("startDate")));
					account.setDeleted(a.optBoolean("deleted", false));
					account.setType(a.getString("type"));
					account.setStartBalance(FormatUtil.parseCurrency(a.getString("startBalance")).toPlainString());
					account.setAccountType(a.getString("accountType"));

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

	private void restoreCategories(JSONObject jsonObject, User user, Map<String, Integer> sourceIDsByUUID) throws DatabaseException, CryptoException {
		final JSONArray categories = jsonObject.optJSONArray("categories");
		if (categories != null) {
			for (int i = 0; i < categories.length(); i++) {
				final JSONObject c = categories.getJSONObject(i);
				final Category existing = sources.selectCategory(user, c.getString("uuid"));
				if (existing == null) {
					final Category category = new Category();
					category.setUuid(c.getString("uuid"));
					category.setName(c.getString("name"));
					category.setDeleted(c.optBoolean("deleted", false));
					category.setType(c.getString("type"));
					final Integer impliedParent = sourceIDsByUUID.get(jsonObject.optString("uuid"));
					final Integer explicitParent = sourceIDsByUUID.get(c.optString("parent"));
					category.setParent(impliedParent != null ? impliedParent : explicitParent);
					category.setPeriodType(c.getString("periodType"));

					ConstraintsChecker.checkInsertCategory(category, user, sources, crypto);
					int count = sources.insertCategory(user, category);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
					sourceIDsByUUID.put(category.getUuid(), category.getId());
				}
				else {
					sourceIDsByUUID.put(existing.getUuid(), existing.getId());
				}
				if (c.has("categories")) {
					restoreCategories(c, user, sourceIDsByUUID);
				}
			}
		}
	}

	private void restoreEntries(JSONObject jsonObject, User user, Map<String, Integer> sourceIDsByUUID) throws DatabaseException, CryptoException {
		final JSONArray entryArray = jsonObject.optJSONArray("entries");
		if (entryArray != null) {
			for (int i = 0; i < entryArray.length(); i++) {
				final JSONObject e = entryArray.getJSONObject(i);
				final Entry entry = new Entry();
				final Integer categoryId = sourceIDsByUUID.get(e.getString("category"));
				if (categoryId == null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find category UUID " + e.getString("category") + " for budget entry " + e.getString("date") + " / " + e.getString("amount"));
				}
				entry.setCategoryId(categoryId);
				entry.setDate(FormatUtil.parseDateInternal(e.getString("date")));
				entry.setAmount(FormatUtil.parseCurrency(e.getString("amount")).toPlainString());
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

	private void restoreTransactions(JSONObject jsonObject, User user, Map<String, Integer> sourceIDsByUUID) throws DatabaseException, CryptoException {
		final JSONArray txnArray = jsonObject.optJSONArray("transactions");
		if (txnArray != null) {
			for (int i = 0; i < txnArray.length(); i++) {
				final JSONObject t = txnArray.getJSONObject(i);
				if (t.optBoolean("deleted", false)) continue;
				if (transactions.selectTransactionCount(user, t.getString("uuid")) == 0) {
					final Transaction transaction = new Transaction();
					transaction.setUuid(t.getString("uuid"));
					transaction.setDescription(t.getString("description"));
					transaction.setNumber(t.optString("number", null));
					transaction.setDate(FormatUtil.parseDateInternal(t.getString("date")));
					transaction.setDeleted(t.optBoolean("deleted", false));
					transaction.setSplits(new ArrayList<>());
					final JSONArray splits = t.getJSONArray("splits");
					for (int j = 0; j < splits.length(); j++) {
						final JSONObject s = splits.getJSONObject(j);
						if (FormatUtil.parseCurrency(s.getString("amount")).compareTo(BigDecimal.ZERO) != 0) {
							final Split split = new Split();
							final Integer fromSource = sourceIDsByUUID.get(s.getString("from"));
							final Integer toSource = sourceIDsByUUID.get(s.getString("to"));
							if (fromSource == null) {
								throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find 'from' source UUID " + s.getString("from") + " for split #" + j + " in transaction " + t.getString("uuid"));
							}
							if (toSource == null) {
								throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find 'to' source UUID " + s.getString("to") + " for split #" + j + " in transaction " + t.getString("uuid"));
							}
							split.setAmount(FormatUtil.parseCurrency(s.getString("amount")).toPlainString());
							split.setFromSource(fromSource);
							split.setToSource(toSource);
							split.setMemo(s.optString("memo", ""));
							transaction.getSplits().add(split);
						}
					}

					if (transaction.getSplits().size() > 0) {
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

	private void restoreScheduledTransactions(JSONObject jsonObject, User user, Map<String, Integer> sourceIDsByUUID) throws DatabaseException, CryptoException {
		final JSONArray txnArray = jsonObject.optJSONArray("scheduledTransactions");
		if (txnArray != null) {
			for (int i = 0; i < txnArray.length(); i++) {
				final JSONObject t = txnArray.getJSONObject(i);
				if (scheduledTransactions.selectScheduledTransactionCount(user, t.getString("uuid")) == 0) {
					final ScheduledTransaction transaction = new ScheduledTransaction();
					transaction.setUuid(t.getString("uuid"));
					transaction.setDescription(t.getString("description"));
					transaction.setNumber(t.optString("number", null));
					transaction.setScheduleName(t.getString("scheduleName"));
					transaction.setScheduleDay(t.getInt("scheduleDay"));
					transaction.setScheduleWeek(t.getInt("scheduleWeek"));
					transaction.setScheduleMonth(t.getInt("scheduleMonth"));
					transaction.setFrequencyType(t.getString("frequencyType"));
					transaction.setStartDate(FormatUtil.parseDateInternal(t.getString("startDate")));
					transaction.setEndDate(FormatUtil.parseDateInternal(t.optString("endDate", null)));
					transaction.setLastCreatedDate(FormatUtil.parseDateInternal(t.optString("lastCreatedDate", null)));
					transaction.setMessage(t.optString("message", null));
					transaction.setSplits(new ArrayList<>());
					final JSONArray splits = t.getJSONArray("splits");
					for (int j = 0; j < splits.length(); j++) {
						final JSONObject s = splits.getJSONObject(j);
						final Split split = new Split();
						final Integer fromSource = sourceIDsByUUID.get(s.getString("from"));
						if (fromSource == null) {
							throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find transaction from source UUID " + s.getString("from") + " for split #" + j + " in transaction " + t.getString("uuid"));
						}
						final Integer toSource = sourceIDsByUUID.get(s.getString("to"));
						if (toSource == null) {
							throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find transaction from source UUID " + s.getString("to") + " for split #" + j + " in transaction " + t.getString("uuid"));
						}
						split.setAmount(FormatUtil.parseCurrency(s.getString("amount")).toPlainString());
						split.setFromSource(fromSource);
						split.setToSource(toSource);
						split.setMemo(s.optString("memo", null));
						transaction.getSplits().add(split);
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