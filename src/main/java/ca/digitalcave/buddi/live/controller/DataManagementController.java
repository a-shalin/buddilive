package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.DataBackupResponseConverter;
import ca.digitalcave.buddi.live.api.dto.DataBackupResponseDto;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto.*;
import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.*;
import ca.digitalcave.buddi.live.model.report.Interval;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DataBackupResponseConverter dataBackupResponseConverter;

	@GetMapping("/data/backup")
	public ResponseEntity<DataBackupResponseDto> backup(@AuthenticationPrincipal final User user) {
		try {
			final List<Account> accounts = sources.selectAccounts(user);
			final List<Category> categories = Category.getHierarchy(sources.selectCategories(user));
			final List<Transaction> txns = transactions.selectTransactions(user);
			final List<ScheduledTransaction> scheduledTxns = scheduledTransactions.selectScheduledTransactions(user);
			final List<Entry> entryList = entries.selectEntries(user);

			final String filename = "Backup (" + FormatUtil.formatDate(new Date(), user) + ").json";
			final HttpHeaders headers = new HttpHeaders();
			headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
			headers.setContentType(MediaType.APPLICATION_JSON);
			return new ResponseEntity<>(dataBackupResponseConverter.convert(user, accounts, categories, entryList, txns, scheduledTxns), headers, HttpStatus.OK);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@GetMapping("/data/export")
	public ResponseEntity<StreamingResponseBody> export(@AuthenticationPrincipal final User user,
			@RequestParam final String interval,
			@RequestParam final String type,
			@RequestParam(required = false) final String startDate,
			@RequestParam(required = false) final String endDate) {
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
			try (CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream), CSVFormat.EXCEL)) {
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
		};

		final String filename = "Export (" + FormatUtil.formatDate(new Date(), user) + ").csv";
		final HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
		headers.set(HttpHeaders.CONTENT_TYPE, "text/csv");
		return new ResponseEntity<>(body, headers, HttpStatus.OK);
	}

	@PostMapping("/data/restore")
	@Transactional
	public SuccessResponseDto restore(@AuthenticationPrincipal final User user,
			@RequestParam("file") final MultipartFile file,
			@RequestParam(value = "deleteData", defaultValue = "false") final boolean deleteData) {
		try {
			if (deleteData) {
				sources.deleteAllSources(user);
				transactions.deleteAllTransactions(user);
				scheduledTransactions.deleteAllScheduledTransactions(user);
			}

			final DataRestoreDto request = objectMapper.readValue(file.getBytes(), DataRestoreDto.class);
			final Map<String, Integer> sourceIDsByUUID = new HashMap<>();
			restoreAccounts(request.accounts(), user, sourceIDsByUUID);
			restoreCategories(request.categories(), user, sourceIDsByUUID, null);
			restoreEntries(request.entries(), user, sourceIDsByUUID);
			restoreTransactions(request.transactions(), user, sourceIDsByUUID);
			restoreScheduledTransactions(request.scheduledTransactions(), user, sourceIDsByUUID);

			DataUpdater.updateBalances(user, sources, transactions, crypto);

			return new SuccessResponseDto(true);
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
