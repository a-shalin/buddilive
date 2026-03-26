package ca.digitalcave.buddi.live.controller;

import java.util.Date;
import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.api.converter.ScheduledTransactionsResponseConverter;
import ca.digitalcave.buddi.live.api.dto.ScheduledTransactionsExecuteResponseDto;
import ca.digitalcave.buddi.live.api.dto.ScheduledTransactionsResponseDto;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.common.DateUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@RestController
@RequestMapping("/data/scheduledtransactions")
public class ScheduledTransactionsController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private ScheduledTransactions scheduledTransactions;

	@Autowired
	private Crypto crypto;

	@Autowired
	private ScheduledTransactionsResponseConverter scheduledTransactionsResponseConverter;

	@GetMapping
	public ScheduledTransactionsResponseDto get(@AuthenticationPrincipal final User user) {
		try {
			final List<ScheduledTransaction> list = scheduledTransactions.selectScheduledTransactions(user);
			return scheduledTransactionsResponseConverter.convert(user, list);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping
	@Transactional
	public SuccessResponseDto post(@AuthenticationPrincipal final User user, @RequestBody final String body) {
		try {
			final JSONObject json = new JSONObject(body);
			final String action = json.optString("action");

			if ("insert".equals(action)) {
				final ScheduledTransaction scheduledTransaction = new ScheduledTransaction(json);
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
			else if ("update".equals(action)) {
				final ScheduledTransaction scheduledTransaction = new ScheduledTransaction(json);
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
			else if ("delete".equals(action)) {
				final ScheduledTransaction scheduledTransaction = new ScheduledTransaction();
				scheduledTransaction.setId(json.getLong("id"));
				final int count = scheduledTransactions.deleteScheduledTransaction(user, scheduledTransaction);
				if (count != 1) {
					throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
				}
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			DataUpdater.updateBalances(user, sources, transactions, crypto);
			return new SuccessResponseDto(true);
		}
		catch (final DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping("/execute")
	@Transactional
	public ScheduledTransactionsExecuteResponseDto execute(@AuthenticationPrincipal final User user, @RequestBody final String body) {
		try {
			Date userDate = null;
			try {
				userDate = FormatUtil.parseDateInternal(body);
			}
			catch (final Throwable e) {
				;
			}
			if (userDate == null || Math.abs(DateUtil.getDaysBetween(new Date(), userDate, false)) > 2) {
				userDate = new Date();
			}

			final String messages = DataUpdater.updateScheduledTransactions(user, sources, transactions, scheduledTransactions, crypto, userDate);
			return scheduledTransactionsResponseConverter.convertExecute(messages);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
		catch (final DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
