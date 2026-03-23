package ca.digitalcave.buddi.live.controller;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
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

import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
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

	@GetMapping
	public String get(@AuthenticationPrincipal User user) {
		try {
			final List<ScheduledTransaction> list = scheduledTransactions.selectScheduledTransactions(user);
			final JSONObject result = new JSONObject();

			Collections.sort(list, new Comparator<ScheduledTransaction>() {
				@Override
				public int compare(ScheduledTransaction o1, ScheduledTransaction o2) {
					if (o1 == null || o2 == null) return 0;
					try {
						return CryptoUtil.decryptWrapper(o1.getScheduleName(), user).compareTo(CryptoUtil.decryptWrapper(o2.getScheduleName(), user));
					}
					catch (CryptoException e) {
						return 0;
					}
				}
			});
			for (ScheduledTransaction t : list) {
				final JSONObject scheduledTransaction = new JSONObject();
				scheduledTransaction.put("id", t.getId());
				scheduledTransaction.put("name", CryptoUtil.decryptWrapper(t.getScheduleName(), user));
				scheduledTransaction.put("description", CryptoUtil.decryptWrapper(t.getDescription(), user));
				scheduledTransaction.put("number", CryptoUtil.decryptWrapper(t.getNumber(), user));
				scheduledTransaction.put("scheduleDay", t.getScheduleDay());
				scheduledTransaction.put("scheduleWeek", t.getScheduleWeek());
				scheduledTransaction.put("scheduleMonth", t.getScheduleMonth());
				scheduledTransaction.put("start", FormatUtil.formatDateInternal(t.getStartDate()));
				scheduledTransaction.put("end", FormatUtil.formatDateInternal(t.getEndDate()));
				scheduledTransaction.put("repeat", t.getFrequencyType());
				scheduledTransaction.put("lastCreatedDate", FormatUtil.formatDateInternal(t.getLastCreatedDate()));
				scheduledTransaction.put("message", CryptoUtil.decryptWrapper(t.getMessage(), user));
				for (Split s : t.getSplits()) {
					final JSONObject split = new JSONObject();
					split.put("id", s.getId());
					final BigDecimal amount = CryptoUtil.decryptWrapperBigDecimal(s.getAmount(), user, false);
					split.put("amount", FormatUtil.formatCurrency(amount, user));
					split.put("amountNumber", amount);
					split.put("fromId", s.getFromSource());
					split.put("toId", s.getToSource());
					split.put("memo", CryptoUtil.decryptWrapper(s.getMemo(), user));
					scheduledTransaction.append("splits", split);
				}
				result.append("data", scheduledTransaction);
			}

			result.put("total", list.size());
			result.put("success", true);
			return result.toString();
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping
	@Transactional
	public String post(@AuthenticationPrincipal User user, @RequestBody String body) {
		try {
			final JSONObject json = new JSONObject(body);
			final String action = json.optString("action");

			if ("insert".equals(action)) {
				final ScheduledTransaction scheduledTransaction = new ScheduledTransaction(json);
				ConstraintsChecker.checkInsertScheduledTransaction(scheduledTransaction, user, sources, crypto);

				int count = scheduledTransactions.insertScheduledTransaction(user, scheduledTransaction);
				if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));

				for (Split split : scheduledTransaction.getSplits()) {
					split.setTransactionId(scheduledTransaction.getId());
					count = scheduledTransactions.insertScheduledSplit(user, split);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
			}
			else if ("update".equals(action)) {
				final ScheduledTransaction scheduledTransaction = new ScheduledTransaction(json);
				ConstraintsChecker.checkUpdateScheduledTransaction(scheduledTransaction, user, sources, crypto);

				int count = scheduledTransactions.updateScheduledTransaction(user, scheduledTransaction);
				if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));

				count = scheduledTransactions.deleteScheduledSplits(user, scheduledTransaction);
				if (count == 0) throw new DatabaseException(String.format("Delete scheduled splits failed; expected 1 or more rows, returned %s", count));
				for (Split split : scheduledTransaction.getSplits()) {
					split.setTransactionId(scheduledTransaction.getId());
					count = scheduledTransactions.insertScheduledSplit(user, split);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
			}
			else if ("delete".equals(action)) {
				final ScheduledTransaction scheduledTransaction = new ScheduledTransaction();
				scheduledTransaction.setId(json.getLong("id"));
				int count = scheduledTransactions.deleteScheduledTransaction(user, scheduledTransaction);
				if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			DataUpdater.updateBalances(user, sources, transactions, crypto);

			final JSONObject result = new JSONObject();
			result.put("success", true);
			return result.toString();
		}
		catch (DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping("/execute")
	@Transactional
	public String execute(@AuthenticationPrincipal User user, @RequestBody String body) {
		try {
			Date userDate = null;
			try {
				userDate = FormatUtil.parseDateInternal(body);
			}
			catch (Throwable e) {
				;
			}
			if (userDate == null || Math.abs(DateUtil.getDaysBetween(new Date(), userDate, false)) > 2) {
				userDate = new Date();
			}

			final String messages = DataUpdater.updateScheduledTransactions(user, sources, transactions, scheduledTransactions, crypto, userDate);
			final JSONObject result = new JSONObject();
			result.put("success", true);
			result.put("messages", messages);
			return result.toString();
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
		catch (DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}