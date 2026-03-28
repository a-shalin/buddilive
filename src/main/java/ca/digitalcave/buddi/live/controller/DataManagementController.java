package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.DataBackupResponseConverter;
import ca.digitalcave.buddi.live.api.dto.DataBackupResponseDto;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.DataRestoreDto;
import ca.digitalcave.buddi.live.service.DataManagementTransactionalService;
import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.List;
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

	@Autowired
	private DataManagementTransactionalService dataManagementTransactionalService;

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
	public SuccessResponseDto restore(@AuthenticationPrincipal final User user,
			@RequestParam("file") final MultipartFile file,
			@RequestParam(value = "deleteData", defaultValue = "false") final boolean deleteData) {
		try {
			final DataRestoreDto request = objectMapper.readValue(file.getBytes(), DataRestoreDto.class);
			dataManagementTransactionalService.restore(user, request, deleteData);

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
}
